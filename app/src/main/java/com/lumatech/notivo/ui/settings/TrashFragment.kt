package com.lumatech.notivo.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentTrashBinding
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.NotivoConfirmDialog
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.serviceLocator
import com.lumatech.notivo.ui.security.AuthGateHelper

/**
 * Displays soft-deleted notes allowing restoration or permanent deletion, with biometric/PIN
 * protection for private notes and folders.
 */
class TrashFragment : BindingFragment<FragmentTrashBinding>(
    FragmentTrashBinding::inflate
) {
    private val viewModel: TrashViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    private lateinit var trashAdapter: TrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener(REQ_TRASH_AUTH) { _, bundle ->
            if (!bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) return@setFragmentResultListener
            val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD) ?: return@setFragmentResultListener
            val action = payload.getInt(EXTRA_ACTION, -1)
            val noteId = payload.getLong(EXTRA_NOTE_ID, -1L)
            executeTrashAction(action, noteId)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTrashBack.setOnClickListener { findNavController().popBackStack() }

        trashAdapter = TrashAdapter(
            onRestore = { note -> runGuardedTrashAction(note, ACTION_RESTORE) },
            onDeleteForever = { note -> runGuardedTrashAction(note, ACTION_DELETE_FOREVER) }
        )

        binding.rvTrashNotes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = trashAdapter
        }

        binding.btnEmptyTrash.setOnClickListener {
            val state = viewModel.uiState.value
            val hasPrivate = state.notes.any { isNotePrivate(it, state.privateFolderIds) }
            if (hasPrivate) {
                AuthGateHelper.requestUnlock(
                    fragment = this,
                    requestKey = REQ_TRASH_AUTH,
                    subtitle = getString(R.string.trash_unlock_private_subtitle),
                    payload = Bundle().apply { putInt(EXTRA_ACTION, ACTION_EMPTY_TRASH) },
                    onBiometricSuccess = { confirmAndEmptyTrash() }
                )
            } else {
                confirmAndEmptyTrash()
            }
        }

        collectWithLifecycle(viewModel.uiState) { state ->
            trashAdapter.updatePrivateFolders(state.privateFolderIds)
            trashAdapter.submitList(state.notes)
            val isEmpty = state.notes.isEmpty()
            binding.emptyTrashContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvTrashNotes.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.btnEmptyTrash.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }

        collectWithLifecycle(viewModel.events) { event ->
            val msgRes = when (event) {
                TrashEvent.Restored -> R.string.trash_restored
                TrashEvent.DeletedForever -> R.string.trash_deleted_forever
            }
            val iconRes = when (event) {
                TrashEvent.Restored -> R.drawable.ic_restore
                TrashEvent.DeletedForever -> R.drawable.ic_delete
            }
            NotivoSnackbarHelper.show(
                view = binding.root,
                messageRes = msgRes,
                iconRes = iconRes
            )
        }
    }

    private fun isNotePrivate(note: Note, privateFolderIds: Set<Long>): Boolean {
        return note.isLocked || (note.folderId != null && note.folderId in privateFolderIds)
    }

    private fun runGuardedTrashAction(note: Note, action: Int) {
        val state = viewModel.uiState.value
        if (!isNotePrivate(note, state.privateFolderIds)) {
            executeTrashAction(action, note.id)
            return
        }
        AuthGateHelper.requestUnlock(
            fragment = this,
            requestKey = REQ_TRASH_AUTH,
            subtitle = getString(R.string.trash_unlock_private_subtitle),
            payload = Bundle().apply {
                putInt(EXTRA_ACTION, action)
                putLong(EXTRA_NOTE_ID, note.id)
            },
            onBiometricSuccess = { executeTrashAction(action, note.id) }
        )
    }

    private fun executeTrashAction(action: Int, noteId: Long) {
        when (action) {
            ACTION_RESTORE -> if (noteId > 0L) viewModel.restore(noteId)
            ACTION_DELETE_FOREVER -> if (noteId > 0L) viewModel.deletePermanently(noteId)
            ACTION_EMPTY_TRASH -> confirmAndEmptyTrash()
        }
    }

    private fun confirmAndEmptyTrash() {
        NotivoConfirmDialog.show(
            context = requireContext(),
            title = getString(R.string.trash_confirm_empty_title),
            message = getString(R.string.trash_confirm_empty_msg),
            positiveText = getString(R.string.trash_empty_action),
            negativeText = getString(R.string.action_cancel),
            iconRes = R.drawable.ic_delete,
            isDestructive = true,
            onPositive = { viewModel.emptyTrash() }
        )
    }

    companion object {
        private const val REQ_TRASH_AUTH = "req_trash_auth"
        private const val EXTRA_ACTION = "extra_action"
        private const val EXTRA_NOTE_ID = "extra_note_id"
        private const val ACTION_RESTORE = 1
        private const val ACTION_DELETE_FOREVER = 2
        private const val ACTION_EMPTY_TRASH = 3
    }
}
