package com.translator.app.feature.settings

import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.core.data.AppSettings
import com.translator.app.core.designsystem.AppDialog
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.AppSwitch
import com.translator.app.core.designsystem.GroupCard
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.SectionLabel
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.core.model.Languages

/**
 * The Me tab: a welcome card, then grouped settings. Built here: app language, text size, read-aloud speed, speak aloud,
 * haptics, AI translation, cloud consent, clear history, delete my data. Plan and quota rows are left out (they need a server).
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    historyCount: Int,
    offlineCount: Int,
    onOpenOffline: () -> Unit,
    onAppLanguage: (String) -> Unit,
    onSpeakAloud: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onTextSize: (String) -> Unit,
    onReadSpeed: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteAll: () -> Unit,
    analyticsOn: Boolean = false,
    onAnalytics: (Boolean) -> Unit = {},
    showPrivacyOptions: Boolean = false,
    onPrivacyOptions: () -> Unit = {},
) {
    var pickLanguage by rememberSaveable { mutableStateOf(false) }
    var pickTextSize by rememberSaveable { mutableStateOf(false) }
    var pickReadSpeed by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pad = pagePadding(maxWidth.value)
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = pad),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Tk.PrimaryContainer).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Tk.Primary), contentAlignment = Alignment.Center) {
                    Text("文", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(str(R.string.hi_welcome), style = Type.Headline, color = Tk.Text)
                    Text(str(R.string.no_account_needed_your_history), color = Tk.Soft, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }

            SectionLabel(str(R.string.general), Modifier.padding(top = 6.dp))
            GroupCard {
                NavRow(AppIcons.Globe, str(R.string.app_language), Languages.byCodeOrNull(settings.appLanguage)?.nativeName ?: "English") { pickLanguage = true }
                Divider()
                NavRow(AppIcons.Globe, str(R.string.offline_languages), if (offlineCount == 0) str(R.string.none_downloaded) else str(R.string.downloaded_2, offlineCount), onClick = onOpenOffline)
                Divider()
                NavRow(AppIcons.Text, str(R.string.text_size), if (settings.textSize == "large") str(R.string.large) else str(R.string.standard)) { pickTextSize = true }
                Divider()
                NavRow(AppIcons.Speaker, str(R.string.read_aloud_speed), if (settings.readSpeed == "slower") str(R.string.slower) else str(R.string.standard)) { pickReadSpeed = true }
                Divider()
                SwitchRow(AppIcons.Wave, str(R.string.speak_translations_aloud), str(R.string.plays_voice_translations_automatically), settings.speakAloud, onSpeakAloud)
                Divider()
                SwitchRow(AppIcons.Dots, str(R.string.haptics), str(R.string.light_tap_on_mic_start), settings.haptics, onHaptics)
            }

            SectionLabel(str(R.string.ai_and_privacy), Modifier.padding(top = 6.dp))
            GroupCard {
                SwitchRow(AppIcons.Shield, str(R.string.analytics_share_title), str(R.string.analytics_share_body), analyticsOn, onAnalytics)
                Divider()
                if (showPrivacyOptions) {
                    NavRow(AppIcons.Info, str(R.string.ad_privacy_choices), null, onClick = onPrivacyOptions)
                    Divider()
                }
                NavRow(AppIcons.Clock, str(R.string.clear_history), if (historyCount == 1) str(R.string.item) else str(R.string.items, historyCount)) { confirmClear = true }
                Divider()
                NavRow(AppIcons.Trash, str(R.string.delete_my_data), null, danger = true) { confirmDelete = true }
            }

            SectionLabel(str(R.string.about), Modifier.padding(top = 6.dp))
            GroupCard {
                NavRow(AppIcons.Info, str(R.string.app_version), "0.1.0", chevron = false) {}
                Divider()
                val appContext = androidx.compose.ui.platform.LocalContext.current
                NavRow(AppIcons.Mail, str(R.string.feedback_title), null) { sendFeedback(appContext) }
            }
        }
    }
    if (pickLanguage) {
        AppDialog(
            title = str(R.string.app_language), body = str(R.string.this_is_the_language_of_2),
            primaryLabel = str(R.string.done), onPrimary = { pickLanguage = false }, secondaryLabel = str(R.string.cancel), onSecondary = { pickLanguage = false },
        ) {
            com.translator.app.core.designsystem.AppLanguagePicker(
                settings.appLanguage, onAppLanguage,
                Modifier.padding(top = 12.dp).height(380.dp),
            )
        }
    }
    if (pickTextSize) ChoiceDialog(str(R.string.text_size), str(R.string.makes_text_in_this_app), listOf("standard" to str(R.string.standard), "large" to str(R.string.large)), settings.textSize, onTextSize) { pickTextSize = false }
    if (pickReadSpeed) ChoiceDialog(str(R.string.read_aloud_speed), str(R.string.how_fast_translations_are_spoken), listOf("standard" to str(R.string.standard), "slower" to str(R.string.slower)), settings.readSpeed, onReadSpeed) { pickReadSpeed = false }
    if (confirmClear) {
        AppDialog(
            title = str(R.string.clear_history_2), body = str(R.string.your_translations_and_favorites_on),
            primaryLabel = str(R.string.clear_history), onPrimary = { onClearHistory(); confirmClear = false },
            secondaryLabel = str(R.string.cancel), onSecondary = { confirmClear = false }, icon = AppIcons.Close, danger = true,
        )
    }
    if (confirmDelete) {
        AppDialog(
            title = str(R.string.delete_all_your_data),
            // The reference mentions an anonymous server account; this app has no account or server data.
            body = str(R.string.we_delete_your_history_favorites),
            primaryLabel = str(R.string.delete_everything), onPrimary = { onDeleteAll(); confirmDelete = false },
            secondaryLabel = str(R.string.cancel), onSecondary = { confirmDelete = false }, icon = AppIcons.Close, danger = true,
        )
    }
}

/** Opens the email app with the feedback address and subject filled in, plus device details at the end of the message. */
private fun sendFeedback(context: android.content.Context) {
    val body = "\n\n\n----\n" + com.translator.app.core.util.CrashLog.report(context)
    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply {
        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.feedback_email)))
        putExtra(android.content.Intent.EXTRA_SUBJECT, str(R.string.feedback_subject))
        putExtra(android.content.Intent.EXTRA_TEXT, body)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(android.content.Intent.createChooser(intent, str(R.string.feedback_title)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, str(R.string.feedback_no_email, context.getString(R.string.feedback_email)), android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Tk.Line)

@Composable
private fun IconTile(icon: ImageVector, danger: Boolean) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(if (danger) Tk.DangerContainer else Tk.PrimaryContainer), contentAlignment = Alignment.Center) {
        AppIcon(icon, if (danger) Tk.Danger else Tk.Primary, size = 20.dp)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String?, danger: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).clickable(enabled = chevron, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, danger)
        Text(title, color = if (danger) Tk.Danger else Tk.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Tk.Muted, fontSize = 14.sp)
        if (chevron) AppIcon(AppIcons.Chevron, Tk.Muted, size = 18.dp)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, false)
        Column(Modifier.weight(1f)) {
            Text(title, color = Tk.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(subtitle, color = Tk.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        AppSwitch(checked, onChange, title)
    }
}

@Composable
private fun ChoiceDialog(title: String, body: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onDone: () -> Unit) {
    AppDialog(title = title, body = body, primaryLabel = str(R.string.done), onPrimary = onDone, secondaryLabel = str(R.string.cancel), onSecondary = onDone) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, label) ->
                val on = key == selected
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clip(Radius.Segment).background(if (on) Tk.PrimaryContainer else Tk.Surface2)
                        .clickable(role = Role.RadioButton) { onPick(key) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(label, color = if (on) Tk.Primary else Tk.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
        }
    }
}
