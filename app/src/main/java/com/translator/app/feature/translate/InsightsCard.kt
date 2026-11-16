package com.translator.app.feature.translate

import com.translator.app.core.i18n.str
import com.translator.app.R
import com.translator.app.core.designsystem.IconAction
import com.translator.app.core.designsystem.ShimmerBar
import com.translator.app.core.designsystem.ThinkingDots
import com.translator.app.core.designsystem.SparkleOrb
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.core.ai.AiInsights
import com.translator.app.core.designsystem.AppCard
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.PrimaryButton
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type

private val tabs = listOf(R.string.meaning, R.string.variants, R.string.examples, R.string.synonyms)

/**
 * Meaning, variants, examples and synonyms for the current translation, written by the AI model (Firebase AI Logic).
 * Nothing is sent until the user taps "Get AI insights", because that sends their text to Google.
 */
@Composable
fun InsightsCard(state: InsightsUiState, aiAllowed: Boolean, onLoad: () -> Unit, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AppCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(AppIcons.Sparkle, Tk.Primary, size = 18.dp)
            Text(str(R.string.ai_insights), color = Tk.Primary, style = Type.Label, modifier = Modifier.weight(1f))
            // Once insights are open (or loading, or failed) they can be closed again.
            if (aiAllowed && state !is InsightsUiState.Idle) IconAction(AppIcons.Close, str(R.string.close), onClose, tint = Tk.Muted, size = 20.dp)
        }
        when {
            !aiAllowed -> Note(str(R.string.turn_on_cloud_voice_and_2))
            state is InsightsUiState.Idle -> {
                Note(str(R.string.get_the_meaning_other_ways))
                PrimaryButton(str(R.string.get_ai_insights), onLoad, Modifier.fillMaxWidth().padding(top = 12.dp), icon = AppIcons.Sparkle, height = 48.dp)
            }
            state is InsightsUiState.Loading -> Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { SparkleOrb(size = 30.dp); ThinkingDots() }
                ShimmerBar(Modifier.fillMaxWidth()); ShimmerBar(Modifier.fillMaxWidth(0.8f)); ShimmerBar(Modifier.fillMaxWidth(0.55f))
            }
            state is InsightsUiState.Failed -> {
                Text(state.error.message(), color = Tk.Danger, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
                PrimaryButton(str(R.string.try_again), onLoad, Modifier.fillMaxWidth().padding(top = 12.dp), height = 48.dp)
            }
            state is InsightsUiState.Loaded -> {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(Radius.Segment).background(Tk.Surface2).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tabs.forEachIndexed { i, label ->
                        val on = i == tab
                        Box(
                            Modifier.weight(1f).defaultMinSize(minHeight = 40.dp).clip(Radius.Segment)
                                .background(if (on) Tk.Surface else Color.Transparent)
                                .clickable(role = Role.Tab) { tab = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(str(label), color = if (on) Tk.Text else Tk.Muted, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                InsightsBody(state.value, tab)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Tk.Soft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightsBody(v: AiInsights, tab: Int) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (tab) {
            0 -> Text(v.meaning.ifEmpty { str(R.string.no_explanation_this_time) }, color = Tk.Soft, fontSize = 15.sp, lineHeight = 22.sp)
            1 -> if (v.variants.isEmpty()) Empty(str(R.string.no_other_ways_to_say)) else v.variants.forEach { Pill(it) }
            2 -> if (v.examples.isEmpty()) Empty(str(R.string.no_examples_this_time)) else v.examples.forEach { e ->
                Column(Modifier.fillMaxWidth().clip(Radius.Segment).background(Tk.Surface2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(e.source, color = Tk.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    Text(e.target, color = Tk.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            else -> if (v.synonyms.isEmpty()) Empty(str(R.string.no_synonyms_this_time)) else FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { v.synonyms.forEach { Pill(it, fill = false) } }
        }
        Text(str(R.string.written_by_ai_so_double), color = Tk.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Empty(text: String) = Text(text, color = Tk.Muted, fontSize = 14.sp)

@Composable
private fun Pill(text: String, fill: Boolean = true) {
    Box(
        Modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(Radius.Segment).background(Tk.Surface2).padding(horizontal = 12.dp, vertical = 10.dp),
    ) { Text(text, color = Tk.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp) }
}
