package com.translator.app.feature.history

import com.translator.app.core.analytics.Ev
import com.translator.app.core.analytics.Analytics
import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.flowOn
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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default) // filtering a long history never holds up the screen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    val currentQuery: String get() = query.value
    fun onQuery(value: String) { query.value = value }
    fun onFavoritesOnly(value: Boolean) { favoritesOnly.value = value }
    fun onToggleFavorite(item: HistoryItem) { if (!item.favorite) Analytics.log(Ev.RESULT_FAVORITED) { token("source", "history") }; viewModelScope.launch { history.setFavorite(item.id, !item.favorite) } }
    fun onDelete(item: HistoryItem) { viewModelScope.launch { history.delete(item.id) } }
    fun onClearAll() { viewModelScope.launch { history.clear() } }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit, onStartTranslating: () -> Unit) {
    com.translator.app.core.ads.BannerFrame(com.translator.app.core.config.RcKeys.SLOT_HISTORY) {
        val state by vm.state.collectAsStateWithLifecycle()
        var confirmClear by rememberSaveable { mutableStateOf(false) }
        // The search box keeps its own text, so typing never waits for the filtered list.
        var query by rememberSaveable { mutableStateOf(vm.currentQuery) }
        val clipboard = LocalClipboardManager.current
        // History and Favorites each have their own native ad, shown only while that tab has something in it. Starring a
        // translation makes the Favorites ad appear; un-starring the last one takes it away. The two never depend on each other.
        val nativeSlot = if (state.favoritesOnly) com.translator.app.core.config.RcKeys.NATIVE_FAVORITES else com.translator.app.core.config.RcKeys.NATIVE_HISTORY
        val tabHasItems = if (state.favoritesOnly) state.all.any { it.favorite } else state.all.isNotEmpty()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pad = pagePadding(maxWidth.value)
            Column(Modifier.fillMaxSize()) {
                // Same look as the AI chat history: a soft gradient header with the title, a line about privacy, the
                // History / Favorites pill switch and a rounded search field.
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Tk.PrimaryContainer, Tk.Bg)))) {
                    Row(Modifier.fillMaxWidth().padding(start = pad - 8.dp, end = pad - 8.dp, top = pad), verticalAlignment = Alignment.CenterVertically) {
                        IconAction(AppIcons.Back, str(R.string.back), onBack)
                        Column(Modifier.weight(1f)) {
                            Text(str(R.string.history), style = Type.Headline, color = Tk.Text)
                            Text(str(R.string.history_sub), color = Tk.Muted, fontSize = 13.sp)
                        }
                        if (state.all.isNotEmpty()) IconAction(AppIcons.Trash, str(R.string.clear_all_history), { confirmClear = true }, tint = Tk.Text)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = pad, end = pad, top = 14.dp, bottom = 10.dp).clip(Radius.Pill).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Pill).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(false to str(R.string.history), true to str(R.string.favorites)).forEach { (fav, label) ->
                            val on = state.favoritesOnly == fav
                            Box(
                                Modifier.weight(1f).height(40.dp).clip(Radius.Pill)
                                    .background(if (on) Brush.linearGradient(listOf(Tk.Primary, Color(0xFF4C86F0))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                    .clickable(role = Role.Tab) { vm.onFavoritesOnly(fav) },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (on) Color.White else Tk.Soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    if (state.all.isNotEmpty()) Row(
                        Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 10.dp).clip(Radius.Pill).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Pill)
                            .padding(horizontal = 14.dp).defaultMinSize(minHeight = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppIcon(AppIcons.Search, Tk.Muted, size = 18.dp)
                        BasicTextField(
                            query, { query = it; vm.onQuery(it) }, singleLine = true,
                            textStyle = Type.Body.copy(color = Tk.Text), cursorBrush = SolidColor(Tk.Primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner -> Box { if (query.isEmpty()) Text(str(R.string.search_history), color = Tk.Muted, style = Type.Body); inner() } },
                        )
                    }
                }
                if (state.all.isEmpty()) {
                    // Only the empty state scrolls on short screens, so nothing can cover its button. No ad on an empty tab.
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                        EmptyState(onStartTranslating, Modifier)
                    }
                } else if (state.visible.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                            val noFavs = state.favoritesOnly && state.query.isBlank()
                            Column(Modifier.fillMaxWidth().padding(horizontal = pad + 12.dp, vertical = pad), horizontalAlignment = Alignment.CenterHorizontally) {
                                EmptyBadge(if (noFavs) AppIcons.Star else AppIcons.Search)
                                Text(
                                    if (noFavs) str(R.string.no_favorites_yet_tap_the) else str(R.string.nothing_matches_your_search),
                                    color = Tk.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 18.dp),
                                )
                            }
                        }
                        // Search found nothing, but the tab itself has items: its ad stays.
                        if (tabHasItems) com.translator.app.core.ads.NativeAdCard(nativeSlot)
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = pad, end = pad, top = 6.dp, bottom = pad),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.visible, key = { it.id }) { item ->
                            HistoryRow(item, onFavorite = { vm.onToggleFavorite(item) }, onCopy = { clipboard.setText(AnnotatedString(item.translation)); Analytics.copied("history") }, onDelete = { vm.onDelete(item) })
                        }
                        // Native card below the list, not above it.
                        item(key = nativeSlot) { com.translator.app.core.ads.NativeAdCard(nativeSlot) }
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
}

private val CardShape = RoundedCornerShape(24.dp)

/** One saved translation, drawn like a chat in the AI chat history: a rounded card, a gradient tile, a time pill. */
@Composable
private fun HistoryRow(item: HistoryItem, onFavorite: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val src = Languages.byCodeOrNull(item.sourceLang)
    val tgt = Languages.byCodeOrNull(item.targetLang)
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(Tk.Surface).border(1.dp, if (item.favorite) Tk.Sand.copy(alpha = 0.7f) else Tk.Line, CardShape)
            .padding(start = 14.dp, top = 14.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The language pair in a gradient tile, where the chat list shows its sparkle.
        Column(
            Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Tk.Primary, Color(0xFF7B5CF0)))),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(item.sourceLang.uppercase().take(3), color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp, maxLines = 1)
            Text(item.targetLang.uppercase().take(3), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.sourceText, color = Tk.Soft, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = if (src?.rtl == true) TextDirection.Rtl else TextDirection.Ltr),
            )
            Text(
                item.translation, color = Tk.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp), style = TextStyle(textDirection = if (tgt?.rtl == true) TextDirection.Rtl else TextDirection.Ltr),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    relativeTime(item.timeMillis), color = Tk.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.clip(Radius.Pill).background(Tk.PrimaryContainer).padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Text(
                    listOfNotNull(src?.name ?: item.sourceLang.uppercase(), tgt?.name ?: item.targetLang.uppercase()).joinToString(" \u2192 "),
                    color = Tk.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconAction(if (item.favorite) AppIcons.StarFilled else AppIcons.Star, if (item.favorite) str(R.string.remove_favorite) else str(R.string.favorite), onFavorite, tint = Tk.Sand, size = 22.dp)
            IconAction(AppIcons.Copy, str(R.string.copy_translation), onCopy, tint = Tk.Muted, size = 20.dp)
            IconAction(AppIcons.Trash, str(R.string.delete), onDelete, tint = Tk.Muted, size = 20.dp)
        }
    }
}

@Composable
private fun EmptyBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Tk.PrimaryContainer, Color.White))).border(1.dp, Tk.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, Tk.Primary, size = 38.dp) }
}

@Composable
private fun EmptyState(onStart: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        EmptyBadge(AppIcons.Clock)
        Text(str(R.string.no_translations_yet), color = Tk.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
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
