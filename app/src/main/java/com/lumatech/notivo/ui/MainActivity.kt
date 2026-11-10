package com.lumatech.notivo.ui

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.color.MaterialColors
import com.lumatech.notivo.NotivoApplication
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.ActivityMainBinding
import com.lumatech.notivo.domain.model.AppFontFamily
import com.lumatech.notivo.ui.common.ExpandableFabView
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.util.NoteColorPalette

/**
 * Single-activity host managing edge-to-edge insets, bottom navigation tabs, and screen privacy.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var lastBackPressTimeMillis: Long = 0L

    private val topLevelDestinations = setOf(
        R.id.noteListFragment,
        R.id.taskListFragment,
        R.id.dashboardFragment
    )

    private val topLevelBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val expandedFab = findExpandedFab(binding.navHostFragment)
            if (expandedFab != null) {
                expandedFab.collapse()
                return
            }

            val currentDestId = navController.currentDestination?.id
            if (currentDestId != R.id.noteListFragment) {
                switchBottomTab(R.id.noteListFragment)
                return
            }

            val now = SystemClock.elapsedRealtime()
            if (now - lastBackPressTimeMillis <= DOUBLE_BACK_EXIT_WINDOW_MS) {
                finish()
            } else {
                lastBackPressTimeMillis = now
                NotivoSnackbarHelper.show(
                    view = binding.root,
                    messageRes = R.string.exit_double_tap_msg,
                    iconRes = R.drawable.ic_info
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = (application as NotivoApplication).serviceLocator.preferencesRepository
            .getCurrentPreferences()
        applyFontOverlay(prefs.fontFamily)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        setupWindowInsets()
        observeDestinationChanges()
        onBackPressedDispatcher.addCallback(this, topLevelBackCallback)
    }

    private fun applyFontOverlay(fontFamily: AppFontFamily) {
        val overlayRes = when (fontFamily) {
            AppFontFamily.OUTFIT -> R.style.ThemeOverlay_Notivo_Font_Outfit
            AppFontFamily.PLUS_JAKARTA_SANS -> R.style.ThemeOverlay_Notivo_Font_Jakarta
            AppFontFamily.POPPINS -> R.style.ThemeOverlay_Notivo_Font_Poppins
            AppFontFamily.LATO -> R.style.ThemeOverlay_Notivo_Font_Lato
            AppFontFamily.SYSTEM -> R.style.ThemeOverlay_Notivo_Font_System
        }
        theme.applyStyle(overlayRes, true)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isBottomNavVisible = binding.bottomNavigation.visibility == View.VISIBLE

            val bottomContainerPadding = if (isBottomNavVisible) {
                0
            } else {
                maxOf(systemBars.bottom, imeInsets.bottom)
            }

            binding.navHostFragment.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = bottomContainerPadding
            )

            binding.bottomNavigation.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            (application as NotivoApplication).serviceLocator.privateFolderSession.lockAll()
        }
    }

    private fun observeDestinationChanges() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBottomNav = destination.id in topLevelDestinations
            binding.bottomNavigation.visibility = if (showBottomNav) View.VISIBLE else View.GONE
            topLevelBackCallback.isEnabled = showBottomNav
            if (destination.id != R.id.noteEditorFragment) {
                setSystemBarSurfaceColor(null)
            }
            val keepPrivateSession = destination.id == R.id.noteListFragment ||
                destination.id == R.id.noteEditorFragment ||
                destination.id == R.id.pinVerifyFragment ||
                destination.id == R.id.pinSetupFragment
            if (!keepPrivateSession) {
                (application as NotivoApplication).serviceLocator.privateFolderSession.lockAll()
            }
            if (destination.id != R.id.noteEditorFragment &&
                destination.id != R.id.pinVerifyFragment &&
                destination.id != R.id.pinSetupFragment
            ) {
                setScreenSecurity(false)
            }
            ViewCompat.requestApplyInsets(binding.mainRoot)
        }
    }

    private fun findExpandedFab(root: View): ExpandableFabView? {
        if (root is ExpandableFabView && root.isExpanded) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findExpandedFab(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    fun setSystemBarSurfaceColor(@ColorInt backgroundColor: Int?) {
        val defaultSurface = MaterialColors.getColor(
            binding.mainRoot,
            com.google.android.material.R.attr.colorSurface
        )
        val effectiveColor = backgroundColor ?: defaultSurface
        binding.mainRoot.setBackgroundColor(effectiveColor)

        val useLightBarIcons = if (backgroundColor != null) {
            ColorUtils.calculateLuminance(effectiveColor) >= 0.5
        } else {
            !NoteColorPalette.isDarkThemeActive(this)
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = useLightBarIcons
        controller.isAppearanceLightNavigationBars = useLightBarIcons

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = (backgroundColor == null)
        }
    }

    fun switchBottomTab(@IdRes destinationId: Int) {
        if (binding.bottomNavigation.selectedItemId != destinationId) {
            binding.bottomNavigation.selectedItemId = destinationId
        }
    }

    fun setScreenSecurity(secure: Boolean) {
        if (secure) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        private const val DOUBLE_BACK_EXIT_WINDOW_MS = 2000L
    }
}
