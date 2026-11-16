package com.translator.app.core.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.IntentSender
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Owns Play update checks and flow state. Activity lifecycle callbacks are forwarded by MainActivity.
 */
class ImmediateUpdateCoordinator(
    context: Context,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val onCriticalUpdateRejected: () -> Unit,
    wasRejectedThisInstance: Boolean,
    wasCriticalUpdateRejected: Boolean,
) {
    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(context.applicationContext)

    private var isResumed = false
    private var isDestroyed = false
    private var isChecking = false
    private var isLaunching = false

    var wasRejectedThisInstance: Boolean = wasRejectedThisInstance
        private set
    var isCriticalUpdateRequired: Boolean = wasCriticalUpdateRejected
        private set

    /** True while the Play update screen is up or a required update is pending: no splash ad then. */
    val isFlowActive: Boolean get() = isLaunching || isCriticalUpdateRequired

    fun onResume() {
        isResumed = true
        checkForUpdate()
    }

    fun onPause() {
        isResumed = false
    }

    fun onDestroy() {
        isResumed = false
        isDestroyed = true
    }

    fun onActivityResult(resultCode: Int) {
        isLaunching = false
        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "Immediate update flow accepted")
            return
        }

        wasRejectedThisInstance = true
        isCriticalUpdateRequired = lastUpdatePriority >= CRITICAL_UPDATE_PRIORITY
        Log.w(TAG, "Immediate update flow was canceled or failed (resultCode=$resultCode)")
        if (isCriticalUpdateRequired) {
            onCriticalUpdateRejected()
        }
    }

    fun retry() {
        if (isDestroyed) return
        wasRejectedThisInstance = false
        isCriticalUpdateRequired = false
        checkForUpdate()
    }

    private var lastUpdatePriority = 0

    private fun checkForUpdate() {
        if (!isResumed || isDestroyed || isChecking || isLaunching) return
        isChecking = true

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                isChecking = false
                if (!isResumed || isDestroyed || isLaunching) return@addOnSuccessListener

                when (info.updateAvailability()) {
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        lastUpdatePriority = info.updatePriority()
                        launchImmediateUpdate(info, isRecovery = true)
                    }

                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        lastUpdatePriority = info.updatePriority()
                        if (!wasRejectedThisInstance &&
                            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                        ) {
                            launchImmediateUpdate(info, isRecovery = false)
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                isChecking = false
                if (!isDestroyed) {
                    Log.w(TAG, "Could not retrieve Google Play update information", error)
                }
            }
    }

    private fun launchImmediateUpdate(info: AppUpdateInfo, isRecovery: Boolean) {
        if (!isResumed || isDestroyed || isLaunching) return
        isLaunching = true

        try {
            val started = appUpdateManager.startUpdateFlowForResult(
                info,
                updateLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
            if (!started) {
                isLaunching = false
                handleLaunchFailure(
                    info,
                    IllegalStateException("Google Play declined to start the immediate update flow"),
                )
            } else if (isRecovery) {
                Log.i(TAG, "Resumed an interrupted immediate update")
            }
        } catch (error: IntentSender.SendIntentException) {
            isLaunching = false
            handleLaunchFailure(info, error)
        } catch (error: ActivityNotFoundException) {
            isLaunching = false
            handleLaunchFailure(info, error)
        } catch (error: IllegalStateException) {
            isLaunching = false
            handleLaunchFailure(info, error)
        }
    }

    private fun handleLaunchFailure(info: AppUpdateInfo, error: Exception) {
        lastUpdatePriority = info.updatePriority()
        wasRejectedThisInstance = true
        isCriticalUpdateRequired = lastUpdatePriority >= CRITICAL_UPDATE_PRIORITY
        Log.w(TAG, "Could not launch the Google Play immediate update flow", error)
        if (isCriticalUpdateRequired && isResumed && !isDestroyed) {
            onCriticalUpdateRejected()
        }
    }

    private companion object {
        const val TAG = "PlayInAppUpdate"
        const val CRITICAL_UPDATE_PRIORITY = 4
    }
}
