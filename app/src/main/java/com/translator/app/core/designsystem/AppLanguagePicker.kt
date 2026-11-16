package com.translator.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.R
import com.translator.app.core.i18n.AppLocale
import com.translator.app.core.i18n.str
import com.translator.app.core.model.Flags
import com.translator.app.core.model.Languages

/**
 * The list of languages the app's own menus and buttons are available in: a search box, then one card per language with
 * its flag(s), its name in its own script, its English name and a clear selected mark. Only languages that really have a
 * translation are offered here; this is separate from the (much longer) list of languages that can be translated.
 */
@Composable
fun AppLanguagePicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    // English first, then every other language A to Z by its English name.
    // Only languages whose strings really load on this phone are listed (checked once, in the background).
    val codes by androidx.compose.runtime.produceState(listOf("en")) { value = AppLocale.verified() }
    val all = remember(codes) { codes.mapNotNull { Languages.byCodeOrNull(it) }.sortedWith(compareBy({ it.code != "en" }, { it.name })) }
    val shown = all
        .filter { q.isEmpty() || it.name.contains(q, true) || it.nativeName.contains(q, true) || it.code.equals(q, true) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(Radius.Pill).background(Tk.Surface2).padding(horizontal = 14.dp).defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(AppIcons.Search, Tk.Muted, size = 18.dp)
            BasicTextField(
                query, { query = it }, singleLine = true, cursorBrush = SolidColor(Tk.Primary),
                textStyle = Type.Body.copy(color = Tk.Text),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> Box { if (query.isEmpty()) Text(str(R.string.search_languages), color = Tk.Muted, style = Type.Body); inner() } },
            )
        }
        if (shown.isEmpty()) Text(str(R.string.no_language_found), color = Tk.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        // A long list, so only the rows on screen are built. The caller gives this picker a height to fill.
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(shown, key = { it.code }) { lang ->
            val on = lang.code == selected
            val selectedLabel = str(R.string.a11y_selected)
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) Tk.PrimaryContainer else Tk.Surface)
                    .border(2.dp, if (on) Tk.Primary else Tk.Line, RoundedCornerShape(16.dp))
                    .clickable(role = Role.RadioButton) { onSelect(lang.code) }
                    .semantics { contentDescription = lang.name + if (on) ", $selectedLabel" else "" }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) { Text(Flags.of(lang.code), fontSize = 20.sp, maxLines = 1, softWrap = false) }
                Column(Modifier.weight(1f)) {
                    Text(
                        lang.nativeName, color = Tk.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        style = TextStyle(textDirection = if (lang.rtl) TextDirection.Rtl else TextDirection.Ltr),
                    )
                    Text(lang.name, color = Tk.Muted, fontSize = 13.sp, style = TextStyle(textDirection = TextDirection.Ltr))
                }
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (on) Tk.Primary else Tk.Surface).border(BorderStroke(2.dp, if (on) Tk.Primary else Tk.Off), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) AppIcon(AppIcons.Check, Color.White, size = 16.dp) }
            }
        } }
    }
}
