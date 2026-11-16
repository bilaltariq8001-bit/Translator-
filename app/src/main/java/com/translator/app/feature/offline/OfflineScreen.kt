package com.translator.app.feature.offline

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.Ev
import com.translator.app.core.i18n.str
import com.translator.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.CodeBadge
import com.translator.app.core.designsystem.IconAction
import com.translator.app.core.designsystem.SectionLabel
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.ScreenHeader
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.core.model.Language
import com.translator.app.core.model.Languages
import com.translator.app.core.offline.OfflinePacks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfflineViewModel(private val packs: OfflinePacks) : ViewModel() {
    val ready: StateFlow<Set<String>> = packs.ready
    val busy: StateFlow<Set<String>> = packs.busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { viewModelScope.launch { packs.refresh() } }

    fun onDownload(lang: Language) {
        _error.value = null
        val packCode = lang.code
        Analytics.log(Ev.OFFLINE_PACK_DOWNLOAD_STARTED) { lang("lang", packCode) }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        viewModelScope.launch {
            if (!packs.download(lang.code)) {
                Analytics.log(Ev.OFFLINE_PACK_DOWNLOAD_FAILED) { lang("lang", packCode) }
                _error.value = str(R.string.could_not_download_check_your, lang.name)
            } else {
                Analytics.log(Ev.OFFLINE_PACK_DOWNLOAD_SUCCESS) { lang("lang", packCode); duration("t", android.os.SystemClock.elapsedRealtime() - startedAt) }
            }
        }
    }

    fun onDelete(lang: Language) { viewModelScope.launch { packs.delete(lang.code) } }
}

/** Offline languages: download a pack once and translate to or from that language with no internet. */
@Composable
fun OfflineScreen(vm: OfflineViewModel, onBack: () -> Unit) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val list = Languages.downloadable.filter { query.isBlank() || it.name.contains(query, true) || it.nativeName.contains(query, true) }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenHeader(str(R.string.offline_languages), Modifier.padding(horizontal = pad - 8.dp), onBack = onBack)
            Text(
                str(R.string.download_a_language_once_to),
                color = Tk.Soft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 10.dp).clip(Radius.Pill).background(Tk.Surface2).padding(horizontal = 14.dp).defaultMinSize(minHeight = 48.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(AppIcons.Search, Tk.Muted, size = 18.dp)
                BasicTextField(
                    query, { query = it }, singleLine = true, cursorBrush = SolidColor(Tk.Primary),
                    textStyle = Type.Body.copy(color = Tk.Text), modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(str(R.string.search_languages), color = Tk.Muted, style = Type.Body); inner() } },
                )
            }
            error?.let {
                Text(it, color = Tk.Danger, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = pad, vertical = 6.dp)) {
                val (have, available) = list.partition { it.code in ready }
                if (have.isNotEmpty()) {
                    item { SectionLabel(str(R.string.downloaded), Modifier.padding(vertical = 8.dp)) }
                    items(have, key = { "d" + it.code }, contentType = { "row" }) { lang ->
                        PackRow(lang, installed = true, working = lang.code in busy, onDownload = {}, onDelete = { vm.onDelete(lang) })
                    }
                }
                item { SectionLabel(str(R.string.available_languages), Modifier.padding(vertical = 8.dp)) }
                items(available, key = { it.code }, contentType = { "row" }) { lang ->
                    PackRow(lang, installed = false, working = lang.code in busy, onDownload = { vm.onDownload(lang) }, onDelete = {})
                }
            }
        }
    }
}

@Composable
private fun PackRow(lang: Language, installed: Boolean, working: Boolean, onDownload: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CodeBadge(lang.code)
        Column(Modifier.weight(1f)) {
            Text(lang.name, color = Tk.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(lang.nativeName, color = Tk.Muted, fontSize = 13.sp)
        }
        when {
            working -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Tk.Primary, strokeWidth = 2.5.dp) }
            installed -> IconAction(AppIcons.Trash, str(R.string.remove, lang.name), onDelete, tint = Tk.Danger, size = 22.dp)
            else -> com.translator.app.core.designsystem.DownloadCircleButton(str(R.string.download, lang.name), onDownload)
        }
    }
}
