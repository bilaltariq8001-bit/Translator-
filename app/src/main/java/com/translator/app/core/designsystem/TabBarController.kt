package com.translator.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a screen that was opened from a bottom tab (a voice mode, an AI tool) hide the tab bar while it is open, so it has the
 * whole screen, and shows it again when the screen closes or the tab changes.
 */
class TabBarController {
    var hidden by mutableStateOf(false)
}

val LocalTabBar = staticCompositionLocalOf { TabBarController() }

/** Hides the bottom tab bar for as long as [hide] is true and this call stays in the composition. */
@Composable
fun HideTabBar(hide: Boolean) {
    val controller = LocalTabBar.current
    DisposableEffect(hide) {
        if (hide) controller.hidden = true
        onDispose { if (hide) controller.hidden = false }
    }
}
