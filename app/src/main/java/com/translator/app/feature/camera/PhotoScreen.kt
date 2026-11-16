package com.translator.app.feature.camera

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.i18n.str
import com.translator.app.R
import com.translator.app.core.i18n.localName
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.translator.app.core.audio.rememberTtsPlayer
import com.translator.app.core.data.AppSettings
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.HistoryAction
import com.translator.app.core.designsystem.IconAction
import com.translator.app.core.designsystem.LanguageBarCard
import com.translator.app.core.designsystem.PrimaryButton
import com.translator.app.core.designsystem.Radius
import com.translator.app.core.designsystem.ScreenHeader
import com.translator.app.core.designsystem.ShimmerLine
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import com.translator.app.core.designsystem.pagePadding
import com.translator.app.feature.translate.LanguageSheet
import com.translator.app.feature.translate.TranslateUiState
import com.translator.app.feature.translate.TranslateViewModel
import com.translator.app.feature.translate.message
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Photo tab: take a photo or choose one, and the AI model reads the text in it and translates it into the target
 * language. The system camera or photo picker does the capture, so the app itself needs no camera permission. The photo is
 * only sent after the user takes or picks it, and is never saved by the app.
 */
@Composable
fun PhotoScreen(
    state: TranslateUiState,
    vm: TranslateViewModel,
    photoVm: PhotoViewModel,
    settings: AppSettings,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val tts = rememberTtsPlayer()
    val photo by photoVm.state.collectAsStateWithLifecycle()
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val allowed = settings.cloudConsent

    // Photos come from the in-screen camera or the gallery; camera files are deleted as soon as they are read.
    fun handle(uri: Uri) {
        scope.launch {
            try {
                val prepared = PhotoImage.load(context, uri)
                if (prepared == null) photoVm.onUnreadable() else photoVm.onPhoto(prepared, state.target.code)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                photoVm.onUnreadable()
            } finally {
                if (uri.scheme == "file") uri.path?.let { File(it).delete() }
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::handle) }
    val openGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pad = pagePadding(maxWidth.value)
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenHeader(str(R.string.photo_translator), Modifier.padding(horizontal = pad - 8.dp)) { HistoryAction(onOpenHistory) }
            LanguageBarCard(state.source.localName(), state.target.localName(), { pickingFor = "from" }, { pickingFor = "to" }, vm::onSwap, Modifier.padding(horizontal = pad))
            if (photo is PhotoUiState.Idle) {
                CameraPane(
                    enabled = allowed, targetName = state.target.localName(), onCaptured = ::handle, onGallery = { openGallery() },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = pad, vertical = 14.dp),
                )
            } else Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val p = photo) {
                    PhotoUiState.Idle -> Unit
                    is PhotoUiState.Working -> ScanningPhoto(p.preview)
                    is PhotoUiState.Done -> {
                        if (p.translated != null) {
                            CompareImage(p.preview, p.translated)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    OutlineAction(str(R.string.save_image), AppIcons.DownloadCircle, true, Modifier.weight(1f)) {
                                        scope.launch {
                                            val ok = OverlayRenderer.saveToGallery(context, p.translated)
                                            android.widget.Toast.makeText(context, if (ok) "Saved to Pictures/AI Translator" else str(R.string.could_not_save_the_image), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                OutlineAction(str(R.string.copy_text), AppIcons.Copy, true, Modifier.weight(1f)) { clipboard.setText(AnnotatedString(p.result.translation)); Analytics.copied("photo") }
                            }
                        } else {
                            // No translation could be painted on the photo. Never show text boxes: either say the text is
                            // already in the chosen language, or show the translation as plain text under the photo.
                            Preview(p.preview, 220)
                            val letters = { t: String -> t.filter { it.isLetterOrDigit() } }
                            if (letters(p.result.original).equals(letters(p.result.translation), ignoreCase = true)) {
                                Text(
                                    str(R.string.this_text_is_already_in, state.target.localName()),
                                    color = Tk.Soft, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 6.dp),
                                )
                            } else {
                                Text(
                                    p.result.translation, color = Tk.Text, fontSize = 18.sp, lineHeight = 26.sp,
                                    style = TextStyle(textDirection = if (state.target.rtl) TextDirection.Rtl else TextDirection.Ltr),
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )
                                OutlineAction(str(R.string.copy_text), AppIcons.Copy, true) { clipboard.setText(AnnotatedString(p.result.translation)); Analytics.copied("photo") }
                            }
                        }
                        PrimaryButton(str(R.string.scan_another_photo), photoVm::onReset, Modifier.fillMaxWidth(), icon = AppIcons.Plus, height = 56.dp)
                    }
                    is PhotoUiState.Failed -> {
                        p.preview?.let { Preview(it, 160) }
                        Text(p.error.message(), color = Tk.Danger, fontSize = 15.sp, lineHeight = 22.sp)
                        if (p.preview != null) PrimaryButton(str(R.string.try_again), { photoVm.onRetry(state.target.code) }, Modifier.fillMaxWidth(), height = 52.dp)
                        OutlineAction(str(R.string.choose_another_photo), AppIcons.Gallery, allowed) { photoVm.onReset() }
                    }
                }
            }
        }
    }
    pickingFor?.let { which ->
        LanguageSheet(
            title = if (which == "from") str(R.string.translate_from_2) else str(R.string.translate_to_2),
            recent = listOf(state.source, state.target).distinct(),
            onPick = { lang -> if (which == "from") vm.onSource(lang) else vm.onTarget(lang); pickingFor = null },
            onDismiss = { pickingFor = null },
        )
    }
}

@Composable
private fun Preview(bitmap: android.graphics.Bitmap, heightDp: Int = 220) {
    Image(
        bitmap = androidx.compose.runtime.remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = str(R.string.your_photo), contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun OutlineAction(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Tk.Surface).border(1.dp, Tk.Line, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        AppIcon(icon, if (enabled) Tk.Primary else Tk.Off, size = 20.dp)
        Box(Modifier.size(8.dp))
        Text(label, color = if (enabled) Tk.Primary else Tk.Off, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun Tip(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().clip(Radius.Card).background(Tk.Surface).border(1.dp, Tk.Line, Radius.Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Tk.PrimaryContainer), contentAlignment = Alignment.Center) { AppIcon(icon, Tk.Primary) }
        Column {
            Text(title, color = Tk.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, color = Tk.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/**
 * The photo with its translation painted on. Drag the handle to compare with the original: the translated picture is
 * shown to the left of the handle. "Original" and "Translated" jump the handle to either end.
 */
@Composable
private fun CompareImage(original: android.graphics.Bitmap, translated: android.graphics.Bitmap) {
    var split by rememberSaveable { mutableStateOf(1f) }
    val originalImage = remember(original) { original.asImageBitmap() }
    val translatedImage = remember(translated) { translated.asImageBitmap() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(original.width.toFloat() / original.height).clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) }
                }
                .semantics { contentDescription = str(R.string.your_photo_with_the_translation) },
        ) {
            val handleX = maxWidth * split
            Image(originalImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Image(
                translatedImage, null,
                Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * split) { this@drawWithContent.drawContent() } },
                contentScale = ContentScale.FillBounds,
            )
            Box(Modifier.offset(x = handleX - 1.dp).width(2.dp).fillMaxHeight().background(Tk.Primary))
            Box(
                Modifier.offset(x = (handleX - 20.dp).coerceIn(0.dp, maxWidth - 40.dp), y = (maxHeight - 40.dp) / 2).size(40.dp)
                    .clip(CircleShape).background(Color.White).border(2.dp, Tk.Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("‹ ›", color = Tk.Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.translator.app.core.designsystem.AppChip(str(R.string.original), split <= 0.01f, { split = 0f })
            com.translator.app.core.designsystem.AppChip(str(R.string.translated), split >= 0.99f, { split = 1f })
        }
    }
}

/**
 * Shown while a photo is read and translated: the photo itself with a light band sweeping up and down and corner marks,
 * instead of text. The sweep position is read only while drawing, so the animation does not recompose anything.
 */
@Composable
private fun ScanningPhoto(photo: android.graphics.Bitmap) {
    val image = remember(photo) { photo.asImageBitmap() }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        0.55f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height).clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = str(R.string.translating_your_photo) },
    ) {
        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0x26000000))
            val y = size.height * sweep
            val band = 110.dp.toPx()
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Tk.Primary.copy(alpha = 0.40f), Color.Transparent), startY = y - band / 2, endY = y + band / 2,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(0f, y - band / 2), size = androidx.compose.ui.geometry.Size(size.width, band),
            )
            drawLine(Color.White, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 3.dp.toPx())
            // Corner marks
            val m = 14.dp.toPx(); val l = 30.dp.toPx(); val w = 4.dp.toPx(); val c = Color.White.copy(alpha = pulse)
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            fun corner(x: Float, y0: Float, dx: Float, dy: Float) {
                drawLine(c, androidx.compose.ui.geometry.Offset(x, y0), androidx.compose.ui.geometry.Offset(x + dx * l, y0), w, cap)
                drawLine(c, androidx.compose.ui.geometry.Offset(x, y0), androidx.compose.ui.geometry.Offset(x, y0 + dy * l), w, cap)
            }
            corner(m, m, 1f, 1f); corner(size.width - m, m, -1f, 1f)
            corner(m, size.height - m, 1f, -1f); corner(size.width - m, size.height - m, -1f, -1f)
        }
    }
}
