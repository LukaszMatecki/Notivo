package com.lumatech.notivo.ui.notes.editor

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.LeadingMarginSpan
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentNoteEditorBinding
import com.lumatech.notivo.databinding.ItemEditorImageBinding
import com.lumatech.notivo.ui.MainActivity
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.NoteImageStorage
import com.lumatech.notivo.ui.common.NotivoConfirmDialog
import com.lumatech.notivo.ui.common.NotivoOptionSheet
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.UndoRedoHistory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.performConfirmHaptic
import com.lumatech.notivo.ui.common.performHapticSafe
import com.lumatech.notivo.ui.common.serviceLocator
import com.lumatech.notivo.ui.folders.FolderDialogHelper
import com.lumatech.notivo.ui.security.AuthGateHelper
import com.lumatech.notivo.util.BulletListHelper
import com.lumatech.notivo.util.DateUtils
import com.lumatech.notivo.util.NoteColorPalette
import kotlinx.coroutines.launch

/**
 * Full-featured note editor with live background color preview, hardware AES-GCM lock toggle,
 * Undo/Redo history, smart bullet list formatting, photo attachments, folder assignment, and auto-save on exit.
 */
class NoteEditorFragment : BindingFragment<FragmentNoteEditorBinding>(
    FragmentNoteEditorBinding::inflate
) {
    private val viewModel: NoteEditorViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    private val undoRedoHistory = UndoRedoHistory()
    private var isProgrammaticTextUpdate = false
    private var hasInitializedFields = false
    private var pendingNewlinePos: Int? = null
    private var renderedImagePaths: List<String> = emptyList()

    private class BulletHangSpan(first: Int, rest: Int) : LeadingMarginSpan.Standard(first, rest)

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || !isAdded) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            NoteImageStorage.importFromUri(requireContext(), uri)
                .onSuccess { path ->
                    viewModel.addImage(path)
                }
                .onFailure {
                    if (isAdded) {
                        NotivoSnackbarHelper.show(
                            view = binding.root,
                            messageRes = R.string.editor_image_error,
                            isError = true,
                            anchorView = binding.editorBottomToolbar
                        )
                    }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(ColorPickerBottomSheet.REQ_COLOR_PICKED) { _, bundle ->
            val selectedHex = bundle.getString(ColorPickerBottomSheet.EXTRA_COLOR_HEX)
            viewModel.updateColor(selectedHex)
        }

        setFragmentResultListener(FolderPickerBottomSheet.REQ_FOLDER_PICKED) { _, bundle ->
            val folderId = bundle.getLong(FolderPickerBottomSheet.EXTRA_FOLDER_ID, -1L)
                .takeIf { it > 0L }
            handleFolderPicked(folderId)
        }

        setFragmentResultListener(REQ_UNLOCK_ASSIGN_FOLDER) { _, bundle ->
            if (!bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) return@setFragmentResultListener
            val folderId = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD)
                ?.getLong(EXTRA_TARGET_FOLDER_ID, -1L)
                ?.takeIf { it > 0L } ?: return@setFragmentResultListener
            serviceLocator.privateFolderSession.unlock(folderId)
            viewModel.updateFolder(folderId)
        }

        setFragmentResultListener(FolderPickerBottomSheet.REQ_NEW_FOLDER_FROM_PICKER) { _, _ ->
            FolderDialogHelper.show(requireContext()) { name, colorHex, isPrivate ->
                if (!isPrivate) {
                    viewModel.createFolderAndAssign(name, colorHex, false)
                } else {
                    AuthGateHelper.ensurePinConfiguredThenProceed(
                        fragment = this,
                        requestKey = REQ_PIN_BEFORE_PRIVATE_FOLDER,
                        payload = Bundle().apply {
                            putString(EXTRA_FOLDER_NAME, name)
                            putString(EXTRA_FOLDER_COLOR, colorHex)
                        },
                        onAlreadyConfigured = {
                            viewModel.createFolderAndAssign(name, colorHex, true)
                        }
                    )
                }
            }
        }

        setFragmentResultListener(REQ_PIN_BEFORE_PRIVATE_FOLDER) { _, bundle ->
            if (!bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) return@setFragmentResultListener
            val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD) ?: return@setFragmentResultListener
            val name = payload.getString(EXTRA_FOLDER_NAME).orEmpty()
            val colorHex = payload.getString(EXTRA_FOLDER_COLOR).orEmpty()
            if (name.isNotBlank() && colorHex.isNotBlank()) {
                viewModel.createFolderAndAssign(name, colorHex, true)
            }
        }

        setFragmentResultListener(REQ_PIN_BEFORE_LOCK) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                viewModel.lockNoteNow()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val noteId = arguments?.getLong("noteId", 0L) ?: 0L
        val folderId = arguments?.getLong("folderId", -1L)?.takeIf { it > 0L }
        val authorized = arguments?.getBoolean("authorized", false) ?: false

        viewModel.loadNote(noteId, folderId, authorized)

        setupTextWatchers()
        setupToolbarActions()
        setupBackHandling()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.uiState.value
        if (state.isLoaded &&
            state.isPrivateFolder &&
            state.noteId > 0L &&
            !serviceLocator.privateFolderSession.isUnlocked(state.folderId)
        ) {
            binding.editorRoot.visibility = View.INVISIBLE
            viewModel.autoSaveIfNeeded()
            findNavController().popBackStack()
            return
        }
        (activity as? MainActivity)?.setScreenSecurity(state.isLocked || state.isPrivateFolder)
    }

    override fun onPause() {
        super.onPause()
        viewModel.autoSaveIfNeeded()
        (activity as? MainActivity)?.setScreenSecurity(false)
    }

    override fun onDestroyView() {
        renderedImagePaths = emptyList()
        (activity as? MainActivity)?.setSystemBarSurfaceColor(null)
        super.onDestroyView()
    }

    private fun setupTextWatchers() {
        binding.etNoteTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticTextUpdate) return
                viewModel.updateTitle(s?.toString().orEmpty())
            }
        })

        binding.etNoteContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticTextUpdate) return
                pendingNewlinePos = if (count == 1 && s != null && start < s.length && s[start] == '\n') {
                    start
                } else {
                    null
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticTextUpdate) return
                val editable = s ?: return

                val nlPos = pendingNewlinePos
                pendingNewlinePos = null
                if (nlPos != null) {
                    val continued = BulletListHelper.handleNewlineAfterBullet(editable.toString(), nlPos)
                    if (continued != null) {
                        isProgrammaticTextUpdate = true
                        editable.replace(0, editable.length, continued.text)
                        binding.etNoteContent.setSelection(continued.cursorPosition)
                        isProgrammaticTextUpdate = false
                    }
                }

                applyBulletVisualIndents(editable)
                val text = editable.toString()
                undoRedoHistory.recordChange(text)
                refreshUndoRedoButtons()
                viewModel.updateContent(text)
            }
        })
    }

    private fun setupToolbarActions() {
        binding.btnBack.setOnClickListener {
            viewModel.autoSaveIfNeeded()
            findNavController().popBackStack()
        }

        binding.btnUndo.setOnClickListener {
            val previous = undoRedoHistory.undo() ?: return@setOnClickListener
            applyContentFromHistory(previous)
        }

        binding.btnRedo.setOnClickListener {
            val next = undoRedoHistory.redo() ?: return@setOnClickListener
            applyContentFromHistory(next)
        }

        binding.btnSaveNote.setOnClickListener {
            viewModel.saveExplicitly()
        }

        binding.btnActionColor.setOnClickListener {
            ColorPickerBottomSheet
                .newInstance(viewModel.uiState.value.colorHex)
                .show(parentFragmentManager, ColorPickerBottomSheet.TAG)
        }

        binding.btnActionLock.setOnClickListener {
            handleLockToggleClicked()
        }

        binding.btnActionFolder.setOnClickListener {
            FolderPickerBottomSheet
                .newInstance(viewModel.uiState.value.folderId)
                .show(parentFragmentManager, FolderPickerBottomSheet.TAG)
        }

        binding.btnActionImage.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnActionPin.setOnClickListener {
            it.performHapticSafe(HapticFeedbackConstants.CONTEXT_CLICK)
            viewModel.togglePin()
        }

        binding.btnActionChecklist.setOnClickListener {
            toggleBulletListAtCursor()
        }

        binding.btnActionMore.setOnClickListener { anchor ->
            showOverflowMenu(anchor)
        }
    }

    private fun setupBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.autoSaveIfNeeded()
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun observeViewModel() {
        collectWithLifecycle(viewModel.uiState) { state ->
            if (!state.isLoaded) return@collectWithLifecycle

            if (state.isPrivateFolder &&
                state.noteId > 0L &&
                !serviceLocator.privateFolderSession.isUnlocked(state.folderId)
            ) {
                binding.editorRoot.visibility = View.INVISIBLE
                findNavController().popBackStack()
                return@collectWithLifecycle
            }

            if (!hasInitializedFields) {
                hasInitializedFields = true
                isProgrammaticTextUpdate = true
                binding.etNoteTitle.setText(state.title)
                binding.etNoteContent.setText(state.content)
                undoRedoHistory.reset(state.content)
                isProgrammaticTextUpdate = false
                refreshUndoRedoButtons()
            }

            val scale = state.fontSizeOption.scaleFactor
            binding.etNoteTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * scale)
            binding.etNoteContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f * scale)
            binding.tvEditorDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * scale)

            if (binding.etNoteContent.text?.contains(BulletListHelper.BULLET_CHAR) == true) {
                applyBulletVisualIndents(binding.etNoteContent.text)
            }

            binding.tvEditorDate.text = DateUtils.formatEditorHeaderDate(state.updatedAt)

            applyBackgroundColor(state.colorHex)
            updateSecurityAndPinIcons(state)
            updateFolderBadge(state.folderId)
            renderAttachedImages(state.imagePaths)
            (activity as? MainActivity)?.setScreenSecurity(state.isLocked || state.isPrivateFolder)
        }

        collectWithLifecycle(viewModel.folders) {
            updateFolderBadge(viewModel.uiState.value.folderId)
        }

        collectWithLifecycle(viewModel.events) { event ->
            when (event) {
                NoteEditorEvent.NoteSaved -> {
                    binding.root.performConfirmHaptic()
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.editor_note_saved,
                        anchorView = binding.editorBottomToolbar
                    )
                }
                NoteEditorEvent.NoteLocked -> {
                    binding.root.performConfirmHaptic()
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.editor_note_locked_msg,
                        iconRes = R.drawable.ic_lock,
                        anchorView = binding.editorBottomToolbar
                    )
                }
                NoteEditorEvent.NoteLockPending -> {
                    binding.root.performConfirmHaptic()
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.editor_note_lock_pending,
                        iconRes = R.drawable.ic_lock,
                        anchorView = binding.editorBottomToolbar
                    )
                }
                NoteEditorEvent.NoteUnlocked -> {
                    binding.root.performConfirmHaptic()
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.editor_note_unlocked_msg,
                        iconRes = R.drawable.ic_lock_open,
                        anchorView = binding.editorBottomToolbar
                    )
                }
                NoteEditorEvent.NoteDeletedAndClose -> {
                    findNavController().popBackStack()
                }
                NoteEditorEvent.TaskCreatedFromNote -> {
                    binding.root.performConfirmHaptic()
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.task_created_from_note,
                        iconRes = R.drawable.ic_tasks,
                        anchorView = binding.editorBottomToolbar
                    )
                }
                is NoteEditorEvent.ShowError -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        message = event.message,
                        isError = true,
                        anchorView = binding.editorBottomToolbar
                    )
                }
            }
        }
    }

    private fun applyBackgroundColor(colorHex: String?) {
        val defaultSurface = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurface
        )
        val resolved = NoteColorPalette.resolveBackgroundColor(requireContext(), colorHex)
        binding.editorRoot.setBackgroundColor(resolved ?: defaultSurface)
        (activity as? MainActivity)?.setSystemBarSurfaceColor(resolved)
    }

    private fun updateSecurityAndPinIcons(state: NoteEditorUiState) {
        val primaryColor = MaterialColors.getColor(
            binding.root,
            androidx.appcompat.R.attr.colorPrimary
        )
        val normalColor = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurface
        )

        if (state.isLocked) {
            binding.btnActionLock.setImageResource(R.drawable.ic_lock)
            binding.btnActionLock.imageTintList = ColorStateList.valueOf(primaryColor)
            binding.btnActionLock.contentDescription = getString(R.string.editor_action_unlock)
        } else {
            binding.btnActionLock.setImageResource(R.drawable.ic_lock_open)
            binding.btnActionLock.imageTintList = ColorStateList.valueOf(normalColor)
            binding.btnActionLock.contentDescription = getString(R.string.editor_action_lock)
        }

        binding.btnActionPin.imageTintList = ColorStateList.valueOf(
            if (state.isPinned) primaryColor else normalColor
        )
    }

    private fun updateFolderBadge(folderId: Long?) {
        val folder = folderId?.let { id ->
            viewModel.folders.value.firstOrNull { it.id == id }
        }
        if (folder != null) {
            binding.tvEditorFolderBadge.visibility = View.VISIBLE
            binding.tvEditorFolderBadge.text = folder.name
        } else {
            binding.tvEditorFolderBadge.visibility = View.GONE
        }
    }

    private fun renderAttachedImages(paths: List<String>) {
        if (paths == renderedImagePaths) return
        renderedImagePaths = paths

        val container = binding.editorImagesContainer
        container.removeAllViews()
        if (paths.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        val inflater = layoutInflater
        paths.forEach { path ->
            val itemBinding = ItemEditorImageBinding.inflate(inflater, container, false)
            NoteImageStorage.loadInto(itemBinding.ivEditorAttachment, path, 1080, 720)
            itemBinding.btnRemoveAttachment.setOnClickListener {
                viewModel.removeImage(path)
            }
            container.addView(itemBinding.root)
        }
    }

    private fun applyContentFromHistory(text: String) {
        isProgrammaticTextUpdate = true
        binding.etNoteContent.setText(text)
        applyBulletVisualIndents(binding.etNoteContent.text)
        binding.etNoteContent.setSelection(text.length)
        isProgrammaticTextUpdate = false
        viewModel.updateContent(text)
        refreshUndoRedoButtons()
    }

    private fun refreshUndoRedoButtons() {
        binding.btnUndo.isEnabled = undoRedoHistory.canUndo()
        binding.btnUndo.alpha = if (undoRedoHistory.canUndo()) 1f else 0.35f
        binding.btnRedo.isEnabled = undoRedoHistory.canRedo()
        binding.btnRedo.alpha = if (undoRedoHistory.canRedo()) 1f else 0.35f
    }

    private fun handleLockToggleClicked() {
        val isCurrentlyLocked = viewModel.uiState.value.isLocked
        if (!isCurrentlyLocked) {
            AuthGateHelper.ensurePinConfiguredThenProceed(
                fragment = this,
                requestKey = REQ_PIN_BEFORE_LOCK,
                onAlreadyConfigured = { viewModel.lockNoteNow() }
            )
            return
        }

        NotivoConfirmDialog.show(
            context = requireContext(),
            title = getString(R.string.editor_unlock_confirm_title),
            message = getString(R.string.editor_unlock_confirm_message),
            positiveText = getString(R.string.editor_action_unlock),
            negativeText = getString(R.string.action_cancel),
            iconRes = R.drawable.ic_lock_open,
            onPositive = { viewModel.unlockNotePermanently() }
        )
    }

    private fun handleFolderPicked(folderId: Long?) {
        val targetFolder = folderId?.let { id ->
            viewModel.folders.value.firstOrNull { it.id == id }
        }
        if (targetFolder != null &&
            targetFolder.isPrivate &&
            !serviceLocator.privateFolderSession.isUnlocked(targetFolder.id)
        ) {
            AuthGateHelper.requestUnlock(
                fragment = this,
                requestKey = REQ_UNLOCK_ASSIGN_FOLDER,
                subtitle = targetFolder.name,
                payload = Bundle().apply { putLong(EXTRA_TARGET_FOLDER_ID, targetFolder.id) },
                onBiometricSuccess = {
                    serviceLocator.privateFolderSession.unlock(targetFolder.id)
                    viewModel.updateFolder(targetFolder.id)
                }
            )
            return
        }
        viewModel.updateFolder(folderId)
    }

    private fun toggleBulletListAtCursor() {
        val editable = binding.etNoteContent.text ?: return
        val cursor = binding.etNoteContent.selectionStart.coerceAtLeast(0)
        val result = BulletListHelper.toggleOrIndentAtCursor(editable.toString(), cursor)
        isProgrammaticTextUpdate = true
        editable.replace(0, editable.length, result.text)
        binding.etNoteContent.setSelection(result.cursorPosition)
        isProgrammaticTextUpdate = false
        applyBulletVisualIndents(editable)
        undoRedoHistory.recordChange(result.text)
        refreshUndoRedoButtons()
        viewModel.updateContent(result.text)
    }

    private fun applyBulletVisualIndents(editable: Editable?) {
        if (editable == null) return
        val oldSpans = editable.getSpans(0, editable.length, BulletHangSpan::class.java)
        oldSpans.forEach { editable.removeSpan(it) }

        val text = editable.toString()
        if (text.isEmpty()) return

        val density = resources.displayMetrics.density
        val baseMarginPx = (4 * density).toInt()
        val textPaint = binding.etNoteContent.paint
        var lineStart = 0

        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
            if (lineStart < lineEnd) {
                val line = text.substring(lineStart, lineEnd)
                val parsed = BulletListHelper.parseLine(line)
                if (parsed.hasBullet) {
                    val prefixWidthPx = textPaint.measureText(parsed.linePrefix).toInt()
                    editable.setSpan(
                        BulletHangSpan(baseMarginPx, baseMarginPx + prefixWidthPx),
                        lineStart,
                        lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            if (lineEnd >= text.length) break
            lineStart = lineEnd + 1
        }
    }

    private fun showOverflowMenu(@Suppress("UNUSED_PARAMETER") anchor: View) {
        NotivoOptionSheet.show(
            context = requireContext(),
            title = getString(R.string.editor_more_options),
            options = listOf(
                NotivoOptionSheet.OptionItem(
                    id = MENU_SHARE,
                    title = getString(R.string.editor_menu_share),
                    iconRes = R.drawable.ic_notes
                ),
                NotivoOptionSheet.OptionItem(
                    id = MENU_CREATE_TASK,
                    title = getString(R.string.editor_menu_create_task),
                    iconRes = R.drawable.ic_tasks
                ),
                NotivoOptionSheet.OptionItem(
                    id = MENU_DELETE,
                    title = getString(R.string.editor_menu_delete),
                    iconRes = R.drawable.ic_delete,
                    isDestructive = true
                )
            )
        ) { selected ->
            when (selected.id) {
                MENU_SHARE -> shareCurrentNote()
                MENU_CREATE_TASK -> viewModel.createLinkedTask()
                MENU_DELETE -> viewModel.deleteNote()
            }
        }
    }

    private fun shareCurrentNote() {
        val state = viewModel.uiState.value
        val shareText = buildString {
            if (state.title.isNotBlank()) {
                append(state.title).append("\n\n")
            }
            append(state.content)
        }.trim()
        if (shareText.isEmpty()) return

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, state.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.editor_menu_share)))
    }

    companion object {
        private const val REQ_PIN_BEFORE_LOCK = "req_pin_before_lock"
        private const val REQ_UNLOCK_ASSIGN_FOLDER = "req_unlock_assign_folder"
        private const val REQ_PIN_BEFORE_PRIVATE_FOLDER = "req_editor_pin_private_folder"
        private const val EXTRA_TARGET_FOLDER_ID = "extra_target_folder_id"
        private const val EXTRA_FOLDER_NAME = "extra_folder_name"
        private const val EXTRA_FOLDER_COLOR = "extra_folder_color"
        private const val MENU_SHARE = 1
        private const val MENU_CREATE_TASK = 2
        private const val MENU_DELETE = 3
    }
}
