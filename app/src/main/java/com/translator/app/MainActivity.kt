package com.translator.app

import android.os.Bundle
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.translator.app.core.update.ImmediateUpdateCoordinator
import com.translator.app.core.designsystem.TranslatorTheme
import com.translator.app.core.designsystem.StartupIntro
import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import com.translator.app.core.ads.LocalAds
import com.translator.app.core.ads.StartupFlow
import com.translator.app.core.backpress.LocalBackAds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private var updateCoordinator: ImmediateUpdateCoordinator? = null
    private var criticalUpdateDialog: AlertDialog? = null

    // The Activity's own resources follow the app language too (the app's own text already does, through AppLocale).
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.translator.app.core.i18n.AppLocale.wrap(newBase))
    }

    // Android 13+: the app's language changed in the phone's Settings while the app was open (the screen is kept, not rebuilt,
    // since the manifest handles locale changes). Every string redraws in the new language through AppLocale.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.translator.app.core.i18n.AppLocale.syncFromSystem(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash (brand blue with the logo) stays up until the saved settings have loaded, so the first thing
        // drawn after it is the real screen and not a blank one. It never waits longer than a second and a half.
        val splash = installSplashScreen()
        // It also waits for the intro's first frame, so the hand-over is blue to blue with nothing in between.
        var settingsLoaded = false
        var introShown = false
        val startedAt = android.os.SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !(settingsLoaded && introShown) && android.os.SystemClock.uptimeMillis() - startedAt < 2_500 }
        // Hand straight over to the animated intro, which starts exactly like the system splash.
        splash.setOnExitAnimationListener { it.remove() }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val updateLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            updateCoordinator?.onActivityResult(result.resultCode)
        }
        updateCoordinator = ImmediateUpdateCoordinator(
            context = applicationContext,
            updateLauncher = updateLauncher,
            onCriticalUpdateRejected = ::showCriticalUpdateDialog,
            wasRejectedThisInstance = savedInstanceState?.getBoolean(STATE_UPDATE_REJECTED) ?: false,
            wasCriticalUpdateRejected = savedInstanceState?.getBoolean(STATE_CRITICAL_UPDATE_REJECTED) ?: false,
        )
        val container = (application as TranslatorApp).container
        lifecycleScope.launch {
            // The app language is already applied (TranslatorApp.onCreate); this only waits for the rest of the settings.
            withTimeoutOrNull(1_500) { container.settings.settings.first() }
            settingsLoaded = true
        }
        val startup = StartupFlow(container, lifecycleScope)
        val allowStartupAds = savedInstanceState == null
        val updateActive = { updateCoordinator?.isFlowActive == true }
        setContent {
            TranslatorTheme {
                var intro by rememberSaveable { mutableStateOf(true) }
                if (!intro) introShown = true
                // After the system recreated the activity (the intro was already over): run the same startup steps now.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (!intro) { startup.begin(this@MainActivity); startup.finish(this@MainActivity, updateActive, allowStartupAds) }
                }
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalAds provides container.ads, LocalBackAds provides container.ads.backAds) {
                        AppRoot(container)
                    }
                    if (intro) StartupIntro(
                        onShown = { introShown = true; startup.begin(this@MainActivity) },
                        onHold = { startup.finish(this@MainActivity, updateActive, allowStartupAds) },
                        onFinished = { intro = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCoordinator?.onResume()
        // If the consent check at start did not answer, ask again so ads can start this session.
        (application as? TranslatorApp)?.container?.ads?.consent?.let { consent ->
            if (!consent.checkedThisProcess) lifecycleScope.launch { consent.retryIfNeeded(this@MainActivity) }
        }
        if (updateCoordinator?.isCriticalUpdateRequired == true) {
            showCriticalUpdateDialog()
        }
    }

    override fun onPause() {
        updateCoordinator?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            STATE_UPDATE_REJECTED,
            updateCoordinator?.wasRejectedThisInstance ?: false,
        )
        outState.putBoolean(
            STATE_CRITICAL_UPDATE_REJECTED,
            updateCoordinator?.isCriticalUpdateRequired ?: false,
        )
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        criticalUpdateDialog?.dismiss()
        criticalUpdateDialog = null
        updateCoordinator?.onDestroy()
        updateCoordinator = null
        super.onDestroy()
    }

    internal fun canShowAppOpenAd(): Boolean {
        val ads = (application as? TranslatorApp)?.container?.ads ?: return false
        return updateCoordinator?.isFlowActive != true &&
            updateCoordinator?.isCriticalUpdateRequired != true &&
            !ads.interstitials.showing &&
            !ads.backAds.isHandling &&
            !ads.backAds.busy.isBusy
    }

    private fun showCriticalUpdateDialog() {
        if (isFinishing || isDestroyed || criticalUpdateDialog?.isShowing == true) return

        criticalUpdateDialog = AlertDialog.Builder(this)
            .setTitle(str(R.string.critical_update_required_title))
            .setMessage(str(R.string.critical_update_required_message))
            .setCancelable(false)
            .setPositiveButton(str(R.string.retry_update)) { dialog, _ ->
                dialog.dismiss()
                criticalUpdateDialog = null
                updateCoordinator?.retry()
            }
            .setNegativeButton(str(R.string.exit_app)) { dialog, _ ->
                dialog.dismiss()
                criticalUpdateDialog = null
                finish()
            }
            .create()
            .also { it.show() }
    }

    private companion object {
        const val STATE_UPDATE_REJECTED = "play_update_rejected"
        const val STATE_CRITICAL_UPDATE_REJECTED = "play_critical_update_rejected"
    }
}
