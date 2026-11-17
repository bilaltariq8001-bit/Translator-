package com.translator.app.feature.camera

import com.translator.app.core.i18n.str
import com.translator.app.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.translator.app.core.designsystem.AppIcon
import com.translator.app.core.designsystem.AppIcons
import com.translator.app.core.designsystem.PrimaryButton
import com.translator.app.core.designsystem.Tk
import com.translator.app.core.designsystem.Type
import java.io.File

/**
 * The camera of the Photo tab, live inside the screen: point at text and tap the shutter, and the translation appears on
 * this same screen (the system camera app is not opened). Needs the camera permission, which is asked for here with a
 * short explanation first.
 */
@Composable
fun CameraPane(
    enabled: Boolean,
    targetName: String,
    onCaptured: (Uri) -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }

    if (!granted) {
        Column(
            modifier.clip(RoundedCornerShape(28.dp)).background(Tk.PrimaryContainer).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Tk.Surface), contentAlignment = Alignment.Center) { AppIcon(AppIcons.Camera, Tk.Primary, size = 30.dp) }
            Text(str(R.string.translate_text_with_the_camera), style = Type.Headline, color = Tk.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Text(
                str(R.string.allow_the_camera_to_point, targetName),
                color = Tk.Soft, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            if (denied) {
                PrimaryButton(str(R.string.open_settings), {
                    com.translator.app.core.util.SafeIntents.openAppSettings(context)
                }, Modifier.fillMaxWidth(), height = 52.dp)
            } else {
                PrimaryButton(str(R.string.allow_camera), { permission.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth(), height = 52.dp, icon = AppIcons.Camera)
            }
            Text(
                str(R.string.choose_from_gallery), color = Tk.Primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 10.dp).clickable(enabled = enabled, role = Role.Button, onClick = onGallery).padding(12.dp),
            )
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    // The photo is shrunk to 1600 px before it is read, so capturing the full sensor (50 to 200 MP on many phones) only made
    // each shot slow and memory-hungry, and failed outright on some low-memory phones. Ask for about 2048x1536 instead.
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                    .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        androidx.camera.core.resolutionselector.ResolutionStrategy(
                            android.util.Size(2048, 1536),
                            androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // Opening the camera is heavy; wait a moment so the tab change animation finishes smoothly first.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(220); settled = true }

    DisposableEffect(lifecycleOwner, settled) {
        if (!settled) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider?.unbindAll()
                camera = provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            } catch (e: Exception) {
                failed = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { try { provider?.unbindAll() } catch (_: Throwable) { }; camera = null }
    }
    // Phones differ in how far they zoom (some front-heavy budget models stop below 3x) and whether they have a torch; asking
    // for more than the camera can do is clamped instead of failing.
    LaunchedEffect(camera, zoom) {
        val cam = camera ?: return@LaunchedEffect
        try {
            val max = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            val min = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
            cam.cameraControl.setZoomRatio(zoom.coerceIn(min, maxOf(min, max)))
        } catch (_: Throwable) { }
    }
    LaunchedEffect(camera, torch) {
        val cam = camera ?: return@LaunchedEffect
        try { if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(torch) } catch (_: Throwable) { }
    }

    fun shoot() {
        if (busy || !enabled) return
        if (camera == null) { failed = true; return }
        busy = true
        val file = try {
            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            File.createTempFile("scan", ".jpg", dir)
        } catch (_: Throwable) { busy = false; failed = true; return }
        try { capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { busy = false; onCaptured(Uri.fromFile(file)) }
                override fun onError(exception: ImageCaptureException) { busy = false; file.delete(); failed = true }
            },
        ) } catch (_: Throwable) { busy = false; file.delete(); failed = true }
    }

    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Text(
            if (failed) str(R.string.the_camera_could_not_start)
            else if (!enabled) str(R.string.turn_on_cloud_voice_and)
            else str(R.string.point_at_text_and_tap),
            color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 2f, 3f).forEach { z ->
                    Text(
                        "${z.toInt()}x", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (zoom == z) Tk.Primary else Color(0x66000000))
                            .clickable(role = Role.Button) { zoom = z }.padding(top = 9.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(AppIcons.Gallery, str(R.string.choose_from_gallery), enabled, onGallery)
                Box(
                    Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp).clip(CircleShape)
                        .background(if (enabled && !busy) Color.White else Color(0x88FFFFFF))
                        .clickable(enabled = enabled && !busy, role = Role.Button, onClick = ::shoot)
                        .semantics { contentDescription = str(R.string.take_photo_and_translate) },
                )
                RoundButton(AppIcons.Flash, if (torch) str(R.string.turn_flashlight_off) else str(R.string.turn_flashlight_on), true, { torch = !torch }, active = torch)
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, active: Boolean = false) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(if (active) Tk.Primary else Color(0x66000000))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, Color.White, size = 24.dp) }
}
