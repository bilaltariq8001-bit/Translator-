package com.translator.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * The app's own dialog, shown over the current screen (never a full screen). Used for the permission explainers
 * and denied states, and for confirmations. [danger] tints the icon and primary button for destructive actions.
 */
@Composable
fun AppDialog(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit = onSecondary,
    icon: ImageVector? = null,
    danger: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Tk.Surface)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (danger) Tk.DangerContainer else Tk.CoralContainer),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(icon, if (danger) Tk.Danger else Tk.Coral, size = 26.dp) }
            }
            Text(title, style = Type.Headline, color = Tk.Text, modifier = Modifier.padding(top = if (icon != null) 14.dp else 0.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(body, color = Tk.Soft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            extra()
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp)
                    .height(56.dp).clip(RoundedCornerShape(18.dp)).background(if (danger) Tk.Danger else Tk.Primary)
                    .clickable(role = Role.Button, onClick = onPrimary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(primaryLabel, color = Color.White, style = Type.Button) }
            Box(
                Modifier.fillMaxWidth().padding(top = 6.dp).defaultMinSize(minHeight = 48.dp).clip(Radius.Button)
                    .clickable(role = Role.Button, onClick = onSecondary),
                contentAlignment = Alignment.Center,
            ) { Text(secondaryLabel, color = if (danger) Tk.Soft else Tk.Primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        }
    }
}
