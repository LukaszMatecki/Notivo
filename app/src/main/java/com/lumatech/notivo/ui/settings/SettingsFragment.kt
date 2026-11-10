package com.lumatech.notivo.ui.settings

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.lumatech.notivo.BuildConfig
import com.lumatech.notivo.NotivoApplication
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentSettingsBinding
import com.lumatech.notivo.domain.model.AppFontFamily
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.NoteLayoutMode
import com.lumatech.notivo.domain.model.SortOrder
import com.lumatech.notivo.domain.model.ThemeMode
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.NotivoConfirmDialog
import com.lumatech.notivo.ui.common.NotivoOptionSheet
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.serviceLocator
import com.lumatech.notivo.ui.security.AuthGateHelper

/**
 * Grouped settings screen covering appearance, hardware security, data export, and trash.
 */
class SettingsFragment : BindingFragment<FragmentSettingsBinding>(
    FragmentSettingsBinding::inflate
) {
    private val viewModel: SettingsViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    private var pendingExportJson: String? = null
    private var suppressSwitchCallbacks = false

    private val createJsonDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri == null || json == null) return@registerForActivityResult

        val success = runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }
        }.isSuccess

        val msgRes = if (success) R.string.settings_export_success else R.string.settings_export_error
        NotivoSnackbarHelper.show(
            view = binding.root,
            messageRes = msgRes,
            isError = !success
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(REQ_PIN_CHANGED) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                viewModel.refreshPinStatus()
            }
        }

        setFragmentResultListener(REQ_VERIFY_BEFORE_CHANGE_PIN) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                AuthGateHelper.navigateToPinSetup(this, REQ_PIN_CHANGED)
            }
        }

        setFragmentResultListener(REQ_VERIFY_BEFORE_REMOVE_PIN) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                viewModel.removePin()
                NotivoSnackbarHelper.show(
                    view = binding.root,
                    messageRes = R.string.settings_pin_removed,
                    iconRes = R.drawable.ic_lock_open
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSettingsBack.setOnClickListener { findNavController().popBackStack() }
        binding.tvAppVersion.text = BuildConfig.VERSION_NAME

        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPinStatus()
    }

    private fun setupClickListeners() {
        binding.rowFontFamily.setOnClickListener { showFontFamilySheet() }
        binding.rowFontSize.setOnClickListener { showFontSizeSheet() }
        binding.rowSortOrder.setOnClickListener { showSortOrderSheet() }
        binding.rowLayoutMode.setOnClickListener { showLayoutModeSheet() }
        binding.rowThemeMode.setOnClickListener { showThemeSheet() }

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchCallbacks) return@setOnCheckedChangeListener
            viewModel.setBiometricEnabled(isChecked)
        }

        binding.switchHighPriority.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchCallbacks) return@setOnCheckedChangeListener
            viewModel.setHighPriorityNotifications(isChecked)
        }

        binding.rowChangePin.setOnClickListener {
            if (viewModel.uiState.value.isPinConfigured) {
                AuthGateHelper.requestUnlock(
                    fragment = this,
                    requestKey = REQ_VERIFY_BEFORE_CHANGE_PIN,
                    subtitle = getString(R.string.settings_change_pin),
                    onBiometricSuccess = {
                        AuthGateHelper.navigateToPinSetup(this, REQ_PIN_CHANGED)
                    }
                )
            } else {
                AuthGateHelper.navigateToPinSetup(this, REQ_PIN_CHANGED)
            }
        }

        binding.btnRemovePinText.setOnClickListener {
            AuthGateHelper.requestUnlock(
                fragment = this,
                requestKey = REQ_VERIFY_BEFORE_REMOVE_PIN,
                subtitle = getString(R.string.settings_remove_pin),
                onBiometricSuccess = {
                    viewModel.removePin()
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.settings_pin_removed,
                        iconRes = R.drawable.ic_lock_open
                    )
                }
            )
        }

        binding.rowExportJson.setOnClickListener {
            viewModel.prepareNotesExport()
        }

        binding.rowTrash.setOnClickListener {
            findNavController().navigate(R.id.trashFragment)
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            NotivoConfirmDialog.show(
                context = requireContext(),
                title = getString(R.string.settings_privacy_info),
                message = getString(R.string.settings_privacy_dialog_msg),
                positiveText = getString(R.string.action_ok),
                iconRes = R.drawable.ic_lock
            )
        }
    }

    private fun observeViewModel() {
        collectWithLifecycle(viewModel.uiState) { state ->
            val prefs = state.preferences
            binding.tvValueFontFamily.setText(
                when (prefs.fontFamily) {
                    AppFontFamily.OUTFIT -> R.string.font_family_outfit
                    AppFontFamily.PLUS_JAKARTA_SANS -> R.string.font_family_jakarta
                    AppFontFamily.POPPINS -> R.string.font_family_poppins
                    AppFontFamily.LATO -> R.string.font_family_lato
                    AppFontFamily.SYSTEM -> R.string.font_family_system
                }
            )
            binding.tvValueFontSize.setText(
                when (prefs.fontSizeOption) {
                    FontSizeOption.SMALL -> R.string.font_small
                    FontSizeOption.MEDIUM -> R.string.font_medium
                    FontSizeOption.LARGE -> R.string.font_large
                }
            )
            binding.tvValueSortOrder.setText(
                when (prefs.sortOrder) {
                    SortOrder.UPDATED_DESC -> R.string.sort_updated_desc
                    SortOrder.CREATED_DESC -> R.string.sort_created_desc
                    SortOrder.TITLE_ASC -> R.string.sort_title_asc
                    SortOrder.COLOR -> R.string.sort_color
                }
            )
            binding.tvValueLayoutMode.setText(
                when (prefs.noteLayoutMode) {
                    NoteLayoutMode.GRID -> R.string.layout_grid
                    NoteLayoutMode.LIST -> R.string.layout_list
                }
            )
            binding.tvValueTheme.setText(
                when (prefs.themeMode) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                }
            )

            binding.tvValuePinStatus.setText(
                if (state.isPinConfigured) R.string.settings_pin_status_set else R.string.settings_pin_status_unset
            )
            binding.rowRemovePin.visibility = if (state.isPinConfigured) View.VISIBLE else View.GONE

            val bioAvailable = serviceLocator.biometricHelper.isBiometricAvailable(requireContext())
            binding.switchBiometric.isEnabled = bioAvailable
            binding.tvBiometricSubtitle.setText(
                if (bioAvailable) R.string.settings_biometric_desc else R.string.settings_biometric_unavailable
            )

            suppressSwitchCallbacks = true
            binding.switchBiometric.isChecked = bioAvailable && prefs.biometricEnabled
            binding.switchHighPriority.isChecked = prefs.highPriorityNotifications
            suppressSwitchCallbacks = false
        }

        collectWithLifecycle(viewModel.events) { event ->
            when (event) {
                is SettingsEvent.ExportJsonReady -> {
                    pendingExportJson = event.jsonContent
                    createJsonDocumentLauncher.launch("notivo-notes-export.json")
                }
                is SettingsEvent.ThemeChanged -> {
                    NotivoApplication.applyThemeMode(event.mode)
                }
                is SettingsEvent.ShowMessage -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        message = event.message,
                        iconRes = R.drawable.ic_info
                    )
                }
            }
        }
    }

    private fun showFontFamilySheet() {
        val current = viewModel.uiState.value.preferences.fontFamily
        val entries = AppFontFamily.entries
        val options = entries.mapIndexed { index, family ->
            val (titleRes, descRes, fontRes) = when (family) {
                AppFontFamily.OUTFIT -> Triple(
                    R.string.font_family_outfit,
                    R.string.font_family_outfit_desc,
                    R.font.outfit_semibold
                )
                AppFontFamily.PLUS_JAKARTA_SANS -> Triple(
                    R.string.font_family_jakarta,
                    R.string.font_family_jakarta_desc,
                    R.font.jakarta_semibold
                )
                AppFontFamily.POPPINS -> Triple(
                    R.string.font_family_poppins,
                    R.string.font_family_poppins_desc,
                    R.font.poppins_semibold
                )
                AppFontFamily.LATO -> Triple(
                    R.string.font_family_lato,
                    R.string.font_family_lato_desc,
                    R.font.lato_bold
                )
                AppFontFamily.SYSTEM -> Triple(
                    R.string.font_family_system,
                    R.string.font_family_system_desc,
                    0
                )
            }
            NotivoOptionSheet.OptionItem(
                id = index,
                title = getString(titleRes),
                subtitle = getString(descRes),
                iconRes = R.drawable.ic_font,
                isSelected = family == current,
                previewFontRes = fontRes
            )
        }
        NotivoOptionSheet.show(
            context = requireContext(),
            title = getString(R.string.settings_font_family),
            options = options
        ) { selected ->
            val chosen = entries[selected.id]
            if (chosen != current) {
                viewModel.updateFontFamily(chosen)
                requireActivity().recreate()
            }
        }
    }

    private fun showFontSizeSheet() {
        val current = viewModel.uiState.value.preferences.fontSizeOption
        val entries = FontSizeOption.entries
        val labelRes = listOf(R.string.font_small, R.string.font_medium, R.string.font_large)
        showEnumOptionSheet(
            titleRes = R.string.settings_font_size,
            entries = entries,
            current = current,
            labels = labelRes.map { getString(it) },
            iconRes = R.drawable.ic_font,
            onChosen = viewModel::updateFontSize
        )
    }

    private fun showSortOrderSheet() {
        val current = viewModel.uiState.value.preferences.sortOrder
        val entries = SortOrder.entries
        val labelRes = listOf(
            R.string.sort_updated_desc,
            R.string.sort_created_desc,
            R.string.sort_title_asc,
            R.string.sort_color
        )
        showEnumOptionSheet(
            titleRes = R.string.settings_sort_order,
            entries = entries,
            current = current,
            labels = labelRes.map { getString(it) },
            iconRes = R.drawable.ic_sort,
            onChosen = viewModel::updateSortOrder
        )
    }

    private fun showLayoutModeSheet() {
        val current = viewModel.uiState.value.preferences.noteLayoutMode
        val entries = NoteLayoutMode.entries
        val options = entries.mapIndexed { index, mode ->
            NotivoOptionSheet.OptionItem(
                id = index,
                title = getString(
                    if (mode == NoteLayoutMode.GRID) R.string.layout_grid else R.string.layout_list
                ),
                iconRes = if (mode == NoteLayoutMode.GRID) R.drawable.ic_grid_view else R.drawable.ic_list_view,
                isSelected = mode == current
            )
        }
        NotivoOptionSheet.show(
            context = requireContext(),
            title = getString(R.string.settings_layout_mode),
            options = options
        ) { selected ->
            viewModel.updateLayoutMode(entries[selected.id])
        }
    }

    private fun showThemeSheet() {
        val current = viewModel.uiState.value.preferences.themeMode
        val entries = ThemeMode.entries
        val labelRes = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
        showEnumOptionSheet(
            titleRes = R.string.settings_theme,
            entries = entries,
            current = current,
            labels = labelRes.map { getString(it) },
            onChosen = viewModel::updateThemeMode
        )
    }

    private fun <T> showEnumOptionSheet(
        titleRes: Int,
        entries: List<T>,
        current: T,
        labels: List<String>,
        iconRes: Int? = null,
        onChosen: (T) -> Unit
    ) {
        val options = entries.mapIndexed { index, item ->
            NotivoOptionSheet.OptionItem(
                id = index,
                title = labels[index],
                iconRes = iconRes,
                isSelected = item == current
            )
        }
        NotivoOptionSheet.show(
            context = requireContext(),
            title = getString(titleRes),
            options = options
        ) { selected ->
            onChosen(entries[selected.id])
        }
    }

    companion object {
        private const val REQ_PIN_CHANGED = "req_pin_changed"
        private const val REQ_VERIFY_BEFORE_CHANGE_PIN = "req_verify_before_change_pin"
        private const val REQ_VERIFY_BEFORE_REMOVE_PIN = "req_verify_before_remove_pin"
    }
}
