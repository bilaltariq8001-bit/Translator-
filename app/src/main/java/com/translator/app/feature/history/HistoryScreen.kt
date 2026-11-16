package com.translator.app.feature.history

import com.translator.app.core.analytics.Ev
import com.translator.app.core.analytics.Analytics
import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.translator.app.core.data.HistoryItem
import com.translator.app.core.data.HistoryRepository
import com.translator.app.core.designsystem.AppCard
import com.translator.app.core.designsystem.AppDialog
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.IconAction
import com.translator.app.core.designsystem.PrimaryButton
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.core.model.Languages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HistoryUiState(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val all: List<HistoryItem> = emptyList(),
    val visible: List<HistoryItem> = emptyList(),
)

/** Pure filter so it can be unit tested: search matches source text, translation and language names. */
fun filterHistory(items: List<HistoryItem>, query: String, favoritesOnly: Boolean): List<HistoryItem> {
    val q = query.trim()
    return items.filter { item ->
        (!favoritesOnly || item.favorite) && (
            q.isEmpty() ||
                item.sourceText.contains(q, ignoreCase = true) ||
                item.translation.contains(q, ignoreCase = true) ||
                Languages.byCodeOrNull(item.sourceLang)?.name?.contains(q, ignoreCase = true) == true ||
                Languages.byCodeOrNull(item.targetLang)?.name?.contains(q, ignoreCase = true) == true
            )
    }
}

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)

    val state: StateFlow<HistoryUiState> = combine(history.items, query, favoritesOnly) { all, q, fav ->
        HistoryUiState(q, fav, all, filterHistory(all, q, fav))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onQuery(value: String) { query.value = value }
    fun onFavoritesOnly(value: Boolean) { favoritesOnly.value = value }
    fun onToggleFavorite(item: HistoryItem) { if (!item.favorite) Analytics.log(Ev.RESULT_FAVORITED) { token("source", "history") }; viewModelScope.launch { history.setFavorite(item.id, !item.favorite) } }
    fun onDelete(item: HistoryItem) { viewModelScope.launch { history.delete(item.id) } }
    fun onClearAll() { viewModelScope.launch { history.clear() } }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit, onStartTranslating: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = pad - 8.dp)) {
                IconAction(AppIcons.Back, str(R.string.back), onBack, Modifier.align(Alignment.CenterStart))
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(false to str(R.string.history), true to str(R.string.favorites)).forEach { (fav, label) ->
                        val on = state.favoritesOnly == fav
                        Column(Modifier.clickable(role = Role.Tab) { vm.onFavoritesOnly(fav) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = if (on) Tk.Primary else Tk.Muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Box(Modifier.padding(top = 4.dp).size(width = 28.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Tk.Primary else androidx.compose.ui.graphics.Color.Transparent))
                        }
                    }
                }
                if (state.all.isNotEmpty()) IconAction(AppIcons.Trash, str(R.string.clear_all_history), { confirmClear = true }, Modifier.align(Alignment.CenterEnd), tint = Tk.Text)
            }
            if (state.all.isEmpty()) {
                // Keep the native card in a reserved slot below the empty state. Only the empty
                // state scrolls on short screens, so the ad cannot cover its action button.
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(onStartTranslating, Modifier)
                    }
                    com.translator.app.core.ads.NativeAdCard("history")
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = pad, end = pad, top = 12.dp).clip(Radius.Pill).background(Tk.Surface2).padding(horizontal = 14.dp).defaultMinSize(minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(AppIcons.Search, Tk.Muted, size = 18.dp)
                    BasicTextField(
                        state.query, vm::onQuery, singleLine = true,
                        textStyle = Type.Body.copy(color = Tk.Text), cursorBrush = SolidColor(Tk.Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (state.query.isEmpty()) Text(str(R.string.search_history), color = Tk.Muted, style = Type.Body); inner() } },
                    )
                }
                if (state.visible.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(pad),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    if (state.favoritesOnly && state.query.isBlank()) str(R.string.no_favorites_yet_tap_the) else str(R.string.nothing_matches_your_search),
                                    color = Tk.Muted,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        com.translator.app.core.ads.NativeAdCard("history")
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = pad, end = pad, top = 12.dp, bottom = pad),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.visible, key = { it.id }) { item ->
                            HistoryRow(item, onFavorite = { vm.onToggleFavorite(item) }, onCopy = { clipboard.setText(AnnotatedString(item.translation)); Analytics.copied("history") }, onDelete = { vm.onDelete(item) })
                        }
                        item {
                            com.translator.app.core.ads.NativeAdCard("history")
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AppDialog(
            title = str(R.string.clear_all_history_2),
            body = str(R.string.translations_will_be_removed_from, state.all.size),
            primaryLabel = str(R.string.clear_history), onPrimary = { vm.onClearAll(); confirmClear = false },
            secondaryLabel = str(R.string.cancel), onSecondary = { confirmClear = false },
            icon = AppIcons.Close, danger = true,
        )
    }
}

private val RoundedCornerShape10 = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

@Composable
private fun HistoryRow(item: HistoryItem, onFavorite: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val src = Languages.byCodeOrNull(item.sourceLang)
    val tgt = Languages.byCodeOrNull(item.targetLang)
    AppCard(Modifier.fillMaxWidth(), leftEdge = Tk.Primary) {
        Text(str(R.string.s, item.sourceLang.uppercase(), item.targetLang.uppercase(), relativeTime(item.timeMillis)), color = Tk.Muted, fontSize = 13.sp)
        Text(item.sourceText, color = Tk.Text, fontSize = 14.sp, maxLines = 3, modifier = Modifier.padding(top = 2.dp), style = TextStyle(textDirection = if (src?.rtl == true) TextDirection.Rtl else TextDirection.Ltr))
        Text(item.translation, color = Tk.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, modifier = Modifier.padding(top = 2.dp), style = TextStyle(textDirection = if (tgt?.rtl == true) TextDirection.Rtl else TextDirection.Ltr))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(if (item.favorite) AppIcons.StarFilled else AppIcons.Star, if (item.favorite) str(R.string.remove_favorite) else str(R.string.favorite), onFavorite, tint = Tk.Sand)
            IconAction(AppIcons.Copy, str(R.string.copy_translation), onCopy)
            Box(Modifier.weight(1f))
            Box(
                Modifier.defaultMinSize(minHeight = 48.dp).clip(Radius.Pill).clickable(role = Role.Button, onClick = onDelete).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(str(R.string.delete), color = Tk.Danger, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun EmptyState(onStart: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 64.dp).clip(Radius.Card).background(Tk.PrimaryContainer), contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.Clock, Tk.Primary, size = 30.dp)
        }
        Text(str(R.string.no_translations_yet), style = Type.Headline, color = Tk.Text, modifier = Modifier.padding(top = 16.dp))
        Text(str(R.string.your_translations_stay_on_this), color = Tk.Muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        PrimaryButton(str(R.string.start_translating), onStart, Modifier.padding(top = 20.dp))
    }
}

/** "Just now", "Yesterday", a weekday for the last week, otherwise the date. */
fun relativeTime(timeMillis: Long, now: Long = System.currentTimeMillis(), text: (Int) -> String = { str(it) }): String {
    val diff = now - timeMillis
    if (diff < 60_000) return text(R.string.just_now)
    fun dayStart(t: Long) = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val days = ((dayStart(now) - dayStart(timeMillis)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> text(R.string.today)
        days == 1 -> text(R.string.yesterday)
        days < 7 -> java.text.SimpleDateFormat("EEEE", com.translator.app.core.i18n.AppLocale.locale).format(timeMillis)
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.translator.app.core.i18n.AppLocale.locale).format(timeMillis)
    }
}
