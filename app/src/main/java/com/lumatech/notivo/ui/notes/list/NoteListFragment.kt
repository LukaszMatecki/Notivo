package com.lumatech.notivo.ui.notes.list

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentNoteListBinding
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.NoteFilter
import com.lumatech.notivo.domain.model.NoteLayoutMode
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.ExpandableFabView
import com.lumatech.notivo.ui.common.NotivoOptionSheet
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.navigateSafely
import com.lumatech.notivo.ui.common.serviceLocator
import com.lumatech.notivo.ui.folders.FolderDialogHelper
import com.lumatech.notivo.ui.folders.FolderManagerFragment
import com.lumatech.notivo.ui.security.AuthGateHelper

/**
 * Primary notes screen featuring persistent search, folder filter chips, sort/layout controls,
 * staggered grid/list view, contextual long-press menu, and an expandable FAB.
 */
class NoteListFragment : BindingFragment<FragmentNoteListBinding>(
    FragmentNoteListBinding::inflate
) {
    private val viewModel: NoteListViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    private lateinit var noteAdapter: NoteAdapter
    private var currentLayoutMode: NoteLayoutMode? = null
    private var renderedFilterSignature: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(REQ_OPEN_LOCKED_NOTE) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD)
                val noteId = payload?.getLong(EXTRA_NOTE_ID, 0L) ?: 0L
                if (noteId > 0L) {
                    openEditor(noteId = noteId, authorized = true)
                }
            }
        }

        setFragmentResultListener(REQ_UNLOCK_FOLDER) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD)
                val folderId = payload?.getLong(FolderManagerFragment.EXTRA_FOLDER_ID, -1L) ?: -1L
                if (folderId > 0L) {
                    viewModel.authorizePrivateFolder(folderId)
                }
            }
        }

        setFragmentResultListener(FolderManagerFragment.REQ_SELECT_FOLDER) { _, bundle ->
            val folderId = bundle.getLong(FolderManagerFragment.EXTRA_FOLDER_ID, -1L)
            val authorized = bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)
            if (folderId > 0L) {
                if (authorized) {
                    viewModel.authorizePrivateFolder(folderId)
                } else {
                    viewModel.selectFilter(NoteFilter.ByFolder(folderId))
                }
            }
        }

        setFragmentResultListener(REQ_PIN_CREATE_PRIVATE_FOLDER) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD) ?: return@setFragmentResultListener
                val name = payload.getString(EXTRA_FOLDER_NAME).orEmpty()
                val colorHex = payload.getString(EXTRA_FOLDER_COLOR).orEmpty()
                if (name.isNotBlank() && colorHex.isNotBlank()) {
                    viewModel.createFolder(name = name, colorHex = colorHex, isPrivate = true)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentLayoutMode = null
        renderedFilterSignature = ""

        setupRecyclerView()
        setupSearchBar()
        setupToolbarAndControls()
        setupExpandableFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            compactHorizontal = false,
            onNoteClick = { note -> handleNoteClicked(note) },
            onNoteLongClick = { note, anchor -> showNoteContextMenu(note, anchor) }
        )
        binding.rvNotes.adapter = noteAdapter
    }

    private fun setupSearchBar() {
        binding.etSearchNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                viewModel.onSearchQueryChanged(text)
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchNotes.setText("")
        }
    }

    private fun setupToolbarAndControls() {
        binding.btnFolders.setOnClickListener {
            navigateSafely(R.id.folderManagerFragment)
        }
        binding.btnSettings.setOnClickListener {
            navigateSafely(R.id.settingsFragment)
        }
        binding.btnEmptyCreateNote.setOnClickListener {
            val lockedFolder = viewModel.uiState.value.lockedActiveFolder
            if (lockedFolder != null) {
                requestPrivateFolderUnlock(lockedFolder)
            } else {
                createNewNoteInActiveFolder()
            }
        }
    }

    private fun setupExpandableFab() {
        binding.expandableFabNotes.setActions(
            listOf(
                ExpandableFabView.FabAction(
                    labelRes = R.string.fab_new_note,
                    iconRes = R.drawable.ic_note_add,
                    onClick = { createNewNoteInActiveFolder() }
                ),
                ExpandableFabView.FabAction(
                    labelRes = R.string.fab_new_folder,
                    iconRes = R.drawable.ic_folder_add,
                    onClick = { showCreateFolderDialog() }
                )
            )
        )
    }

    private fun observeViewModel() {
        collectWithLifecycle(viewModel.uiState) { state ->
            applyLayoutModeIfNeeded(state.layoutMode)
            renderFilterChips(state.folders, state.activeFilter)

            noteAdapter.updateMetadata(state.folders, state.fontSizeOption)
            noteAdapter.submitList(state.notes)

            renderEmptyState(state)
        }

        collectWithLifecycle(viewModel.events) { event ->
            when (event) {
                is NoteListEvent.ShowUndoDelete -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.note_moved_to_trash,
                        iconRes = R.drawable.ic_delete,
                        duration = Snackbar.LENGTH_LONG,
                        actionLabelRes = R.string.action_undo,
                        onAction = { viewModel.restoreDeletedNote(event.noteId) }
                    )
                }
                is NoteListEvent.ShowMessage -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        message = event.message,
                        iconRes = R.drawable.ic_info
                    )
                }
                NoteListEvent.NotePinned -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.note_pinned,
                        iconRes = R.drawable.ic_pin
                    )
                }
                NoteListEvent.NoteUnpinned -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.note_unpinned,
                        iconRes = R.drawable.ic_pin
                    )
                }
            }
        }
    }

    private fun applyLayoutModeIfNeeded(mode: NoteLayoutMode) {
        if (currentLayoutMode == mode) return
        currentLayoutMode = mode

        val spanCount = resources.getInteger(R.integer.notes_grid_span_count)
        binding.rvNotes.layoutManager = when (mode) {
            NoteLayoutMode.GRID -> StaggeredGridLayoutManager(
                spanCount,
                StaggeredGridLayoutManager.VERTICAL
            ).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            NoteLayoutMode.LIST -> LinearLayoutManager(requireContext())
        }
    }

    private fun renderFilterChips(folders: List<Folder>, activeFilter: NoteFilter) {
        val signature = buildString {
            append(activeFilter)
            folders.forEach { append("|${it.id}:${it.name}:${it.isPrivate}") }
        }
        if (signature == renderedFilterSignature) return
        renderedFilterSignature = signature

        val chipGroup = binding.chipGroupFilters
        chipGroup.removeAllViews()

        chipGroup.addView(
            createFilterChip(
                text = getString(R.string.filter_all),
                checked = activeFilter is NoteFilter.All,
                showLock = false
            ) {
                viewModel.selectFilter(NoteFilter.All)
            }
        )

        folders.forEach { folder ->
            val isSelected = activeFilter is NoteFilter.ByFolder && activeFilter.folderId == folder.id
            chipGroup.addView(
                createFilterChip(
                    text = folder.name,
                    checked = isSelected,
                    showLock = folder.isPrivate
                ) {
                    if (viewModel.isFolderUnlocked(folder)) {
                        viewModel.selectFilter(NoteFilter.ByFolder(folder.id))
                    } else {
                        // Reset selection visually until user authorizes private folder
                        renderedFilterSignature = ""
                        renderFilterChips(folders, activeFilter)
                        requestPrivateFolderUnlock(folder)
                    }
                }
            )
        }

        chipGroup.addView(
            createFilterChip(
                text = getString(R.string.filter_unassigned),
                checked = activeFilter is NoteFilter.Unassigned,
                showLock = false
            ) {
                viewModel.selectFilter(NoteFilter.Unassigned)
            }
        )
    }

    private fun createFilterChip(
        text: String,
        checked: Boolean,
        showLock: Boolean,
        onClick: () -> Unit
    ): Chip {
        return Chip(requireContext()).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            isChecked = checked
            applyNotivoPillStyle(checked = checked)
            if (showLock) {
                setChipIconResource(R.drawable.ic_lock)
                isChipIconVisible = true
                chipIconSize = resources.displayMetrics.density * 14f
                setChipIconTintResource(
                    if (checked) R.color.notivo_on_primary_container else R.color.notivo_on_surface_variant
                )
            }
            setOnClickListener { onClick() }
        }
    }

    private fun Chip.applyNotivoPillStyle(checked: Boolean) {
        val density = resources.displayMetrics.density
        chipStrokeWidth = 0f
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(ShapeAppearanceModel.PILL)
            .build()
        chipMinHeight = 38f * density
        chipStartPadding = 12f * density
        chipEndPadding = 12f * density
        setChipBackgroundColorResource(
            if (checked) R.color.notivo_primary_container else R.color.notivo_surface_container
        )
        setTextColor(
            ContextCompat.getColor(
                context,
                if (checked) R.color.notivo_on_primary_container else R.color.notivo_on_surface_variant
            )
        )
        setEnsureMinTouchTargetSize(false)
    }

    private fun renderEmptyState(state: NoteListUiState) {
        if (state.isLoading) {
            binding.emptyNotesContainer.visibility = View.GONE
            return
        }
        if (state.notes.isNotEmpty()) {
            binding.emptyNotesContainer.visibility = View.GONE
            binding.rvNotes.visibility = View.VISIBLE
            return
        }

        binding.rvNotes.visibility = View.GONE
        binding.emptyNotesContainer.visibility = View.VISIBLE

        val lockedFolder = state.lockedActiveFolder
        when {
            state.isCurrentFolderLocked && lockedFolder != null -> {
                binding.ivEmptyNotesIcon.setImageResource(R.drawable.ic_lock)
                binding.tvEmptyNotesTitle.setText(R.string.private_folder_locked_title)
                binding.tvEmptyNotesSubtitle.setText(R.string.private_folder_locked_subtitle)
                binding.btnEmptyCreateNote.setText(R.string.private_folder_unlock_cta)
                binding.btnEmptyCreateNote.visibility = View.VISIBLE
            }
            state.searchQuery.isNotBlank() -> {
                binding.ivEmptyNotesIcon.setImageResource(R.drawable.ic_search)
                binding.tvEmptyNotesTitle.setText(R.string.search_empty_title)
                binding.tvEmptyNotesSubtitle.text =
                    getString(R.string.search_empty_subtitle, state.searchQuery.trim())
                binding.btnEmptyCreateNote.visibility = View.GONE
            }
            else -> {
                binding.ivEmptyNotesIcon.setImageResource(R.drawable.ic_notes)
                binding.tvEmptyNotesTitle.setText(R.string.notes_empty_title)
                binding.tvEmptyNotesSubtitle.setText(R.string.notes_empty_subtitle)
                binding.btnEmptyCreateNote.setText(R.string.notes_empty_cta)
                binding.btnEmptyCreateNote.visibility = View.VISIBLE
            }
        }
    }

    private fun handleNoteClicked(note: Note) {
        if (!note.isLocked) {
            openEditor(noteId = note.id, authorized = false)
            return
        }

        val subtitle = note.title.ifBlank { getString(R.string.note_locked_preview) }
        AuthGateHelper.requestUnlock(
            fragment = this,
            requestKey = REQ_OPEN_LOCKED_NOTE,
            subtitle = subtitle,
            payload = Bundle().apply { putLong(EXTRA_NOTE_ID, note.id) },
            onBiometricSuccess = {
                openEditor(noteId = note.id, authorized = true)
            }
        )
    }

    private fun requestPrivateFolderUnlock(folder: Folder) {
        AuthGateHelper.requestUnlock(
            fragment = this,
            requestKey = REQ_UNLOCK_FOLDER,
            subtitle = folder.name,
            payload = Bundle().apply { putLong(FolderManagerFragment.EXTRA_FOLDER_ID, folder.id) },
            onBiometricSuccess = {
                viewModel.authorizePrivateFolder(folder.id)
            }
        )
    }

    private fun showNoteContextMenu(note: Note, @Suppress("UNUSED_PARAMETER") anchor: View) {
        val noteTitle = note.title.ifBlank { getString(R.string.note_untitled) }
        NotivoOptionSheet.show(
            context = requireContext(),
            title = noteTitle,
            subtitle = getString(R.string.note_options_title),
            options = listOf(
                NotivoOptionSheet.OptionItem(
                    id = MENU_PIN,
                    title = getString(if (note.isPinned) R.string.note_unpinned else R.string.editor_action_pin),
                    iconRes = R.drawable.ic_pin
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
                MENU_PIN -> viewModel.togglePin(note)
                MENU_DELETE -> viewModel.softDeleteNote(note.id)
            }
        }
    }

    private fun showCreateFolderDialog() {
        FolderDialogHelper.show(requireContext()) { name, colorHex, isPrivate ->
            if (!isPrivate) {
                viewModel.createFolder(name, colorHex, false)
            } else {
                AuthGateHelper.ensurePinConfiguredThenProceed(
                    fragment = this,
                    requestKey = REQ_PIN_CREATE_PRIVATE_FOLDER,
                    payload = Bundle().apply {
                        putString(EXTRA_FOLDER_NAME, name)
                        putString(EXTRA_FOLDER_COLOR, colorHex)
                    },
                    onAlreadyConfigured = {
                        viewModel.createFolder(name, colorHex, true)
                    }
                )
            }
        }
    }

    private fun createNewNoteInActiveFolder() {
        val state = viewModel.uiState.value
        val lockedFolder = state.lockedActiveFolder
        if (lockedFolder != null) {
            requestPrivateFolderUnlock(lockedFolder)
            return
        }
        val folderId = (state.activeFilter as? NoteFilter.ByFolder)?.folderId ?: -1L
        val isPrivateUnlocked = folderId > 0L &&
            state.folders.any { it.id == folderId && it.isPrivate }
        openEditor(noteId = 0L, folderId = folderId, authorized = isPrivateUnlocked)
    }

    private fun openEditor(noteId: Long, folderId: Long = -1L, authorized: Boolean) {
        navigateSafely(
            R.id.noteEditorFragment,
            Bundle().apply {
                putLong("noteId", noteId)
                putLong("folderId", folderId)
                putBoolean("authorized", authorized)
            }
        )
    }

    companion object {
        private const val REQ_OPEN_LOCKED_NOTE = "req_open_locked_note"
        private const val REQ_UNLOCK_FOLDER = "req_unlock_folder"
        private const val REQ_PIN_CREATE_PRIVATE_FOLDER = "req_pin_create_private_folder"
        private const val EXTRA_NOTE_ID = "extra_note_id"
        private const val EXTRA_FOLDER_NAME = "extra_folder_name"
        private const val EXTRA_FOLDER_COLOR = "extra_folder_color"
        private const val MENU_PIN = 1
        private const val MENU_DELETE = 2
    }
}
