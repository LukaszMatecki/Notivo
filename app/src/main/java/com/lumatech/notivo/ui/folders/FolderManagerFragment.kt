package com.lumatech.notivo.ui.folders

import android.os.Bundle
import android.view.View
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentFolderManagerBinding
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.NotivoConfirmDialog
import com.lumatech.notivo.ui.common.NotivoOptionSheet
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.serviceLocator
import com.lumatech.notivo.ui.security.AuthGateHelper

/**
 * Displays folders in a colorful 2-column grid with note counts and privacy controls.
 */
class FolderManagerFragment : BindingFragment<FragmentFolderManagerBinding>(
    FragmentFolderManagerBinding::inflate
) {
    private val viewModel: FolderManagerViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    private lateinit var folderAdapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener(REQ_UNLOCK_PRIVATE_FOLDER) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD)
                val folderId = payload?.getLong(EXTRA_FOLDER_ID, -1L) ?: -1L
                if (folderId > 0L) {
                    deliverSelectedFolderAndReturn(folderId, authorized = true)
                }
            }
        }

        setFragmentResultListener(REQ_UNLOCK_PRIVATE_FOLDER_ACTION) { _, bundle ->
            if (!bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) return@setFragmentResultListener
            val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD) ?: return@setFragmentResultListener
            val folderId = payload.getLong(EXTRA_FOLDER_ID, -1L)
            val action = payload.getInt(EXTRA_FOLDER_ACTION, -1)
            val folder = viewModel.folders.value.firstOrNull { it.id == folderId } ?: return@setFragmentResultListener
            executeFolderAction(folder, action)
        }

        setFragmentResultListener(REQ_PIN_BEFORE_PRIVATE_FOLDER) { _, bundle ->
            if (!bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) return@setFragmentResultListener
            val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD) ?: return@setFragmentResultListener
            val folderId = payload.getLong(EXTRA_FOLDER_ID, 0L)
            val name = payload.getString(EXTRA_FOLDER_NAME).orEmpty()
            val colorHex = payload.getString(EXTRA_FOLDER_COLOR).orEmpty()
            if (name.isNotBlank() && colorHex.isNotBlank()) {
                viewModel.saveFolder(id = folderId, name = name, colorHex = colorHex, isPrivate = true)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnFoldersBack.setOnClickListener { findNavController().popBackStack() }

        folderAdapter = FolderAdapter(
            onFolderClicked = { folder -> handleFolderClick(folder) },
            onFolderMoreClicked = { folder, anchor -> showFolderMenu(folder, anchor) }
        )

        binding.rvFolders.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = folderAdapter
        }

        binding.btnCreateFolderTop.setOnClickListener {
            FolderDialogHelper.show(requireContext()) { name, colorHex, isPrivate ->
                saveFolderWithPinGuard(id = 0L, name = name, colorHex = colorHex, isPrivate = isPrivate)
            }
        }

        collectWithLifecycle(viewModel.folders) { folders ->
            folderAdapter.submitList(folders)
        }

        collectWithLifecycle(viewModel.events) { event ->
            when (event) {
                is FolderManagerEvent.ShowMessage -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        message = event.message,
                        isError = true
                    )
                }
            }
        }
    }

    private fun handleFolderClick(folder: Folder) {
        if (!folder.isPrivate) {
            deliverSelectedFolderAndReturn(folder.id, authorized = false)
            return
        }

        AuthGateHelper.requestUnlock(
            fragment = this,
            requestKey = REQ_UNLOCK_PRIVATE_FOLDER,
            subtitle = folder.name,
            payload = Bundle().apply { putLong(EXTRA_FOLDER_ID, folder.id) },
            onBiometricSuccess = {
                deliverSelectedFolderAndReturn(folder.id, authorized = true)
            }
        )
    }

    private fun showFolderMenu(folder: Folder, @Suppress("UNUSED_PARAMETER") anchor: View) {
        NotivoOptionSheet.show(
            context = requireContext(),
            title = folder.name,
            subtitle = getString(R.string.folder_options_title),
            options = listOf(
                NotivoOptionSheet.OptionItem(
                    id = MENU_EDIT,
                    title = getString(R.string.folder_action_rename),
                    iconRes = R.drawable.ic_folder
                ),
                NotivoOptionSheet.OptionItem(
                    id = MENU_DELETE,
                    title = getString(R.string.folder_action_delete),
                    iconRes = R.drawable.ic_delete,
                    isDestructive = true
                )
            )
        ) { selected ->
            if (!folder.isPrivate) {
                executeFolderAction(folder, selected.id)
                return@show
            }
            AuthGateHelper.requestUnlock(
                fragment = this,
                requestKey = REQ_UNLOCK_PRIVATE_FOLDER_ACTION,
                subtitle = folder.name,
                payload = Bundle().apply {
                    putLong(EXTRA_FOLDER_ID, folder.id)
                    putInt(EXTRA_FOLDER_ACTION, selected.id)
                },
                onBiometricSuccess = { executeFolderAction(folder, selected.id) }
            )
        }
    }

    private fun executeFolderAction(folder: Folder, actionId: Int) {
        when (actionId) {
            MENU_EDIT -> FolderDialogHelper.show(requireContext(), folder) { name, colorHex, isPrivate ->
                saveFolderWithPinGuard(folder.id, name, colorHex, isPrivate)
            }
            MENU_DELETE -> confirmDeleteFolder(folder)
        }
    }

    private fun saveFolderWithPinGuard(id: Long, name: String, colorHex: String, isPrivate: Boolean) {
        if (!isPrivate) {
            viewModel.saveFolder(id = id, name = name, colorHex = colorHex, isPrivate = false)
            return
        }
        AuthGateHelper.ensurePinConfiguredThenProceed(
            fragment = this,
            requestKey = REQ_PIN_BEFORE_PRIVATE_FOLDER,
            payload = Bundle().apply {
                putLong(EXTRA_FOLDER_ID, id)
                putString(EXTRA_FOLDER_NAME, name)
                putString(EXTRA_FOLDER_COLOR, colorHex)
            },
            onAlreadyConfigured = {
                viewModel.saveFolder(id = id, name = name, colorHex = colorHex, isPrivate = true)
            }
        )
    }

    private fun confirmDeleteFolder(folder: Folder) {
        NotivoConfirmDialog.show(
            context = requireContext(),
            title = getString(R.string.folder_delete_title),
            message = getString(R.string.folder_delete_message),
            positiveText = getString(R.string.action_delete),
            negativeText = getString(R.string.action_cancel),
            iconRes = R.drawable.ic_delete,
            isDestructive = true,
            onPositive = { viewModel.deleteFolder(folder.id) }
        )
    }

    private fun deliverSelectedFolderAndReturn(folderId: Long, authorized: Boolean) {
        setFragmentResult(
            REQ_SELECT_FOLDER,
            Bundle().apply {
                putLong(EXTRA_FOLDER_ID, folderId)
                putBoolean(AuthGateHelper.EXTRA_AUTHORIZED, authorized)
            }
        )
        findNavController().popBackStack()
    }

    companion object {
        const val REQ_SELECT_FOLDER = "req_select_folder"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
        private const val REQ_UNLOCK_PRIVATE_FOLDER = "req_unlock_private_folder_mgr"
        private const val REQ_UNLOCK_PRIVATE_FOLDER_ACTION = "req_unlock_private_folder_action"
        private const val REQ_PIN_BEFORE_PRIVATE_FOLDER = "req_pin_before_private_folder"
        private const val EXTRA_FOLDER_ACTION = "extra_folder_action"
        private const val EXTRA_FOLDER_NAME = "extra_folder_name"
        private const val EXTRA_FOLDER_COLOR = "extra_folder_color"
        private const val MENU_EDIT = 1
        private const val MENU_DELETE = 2
    }
}
