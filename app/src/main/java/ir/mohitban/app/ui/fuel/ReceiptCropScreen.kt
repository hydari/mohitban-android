package ir.mohitban.app.ui.fuel

import android.graphics.Bitmap
import ir.mohitban.app.ui.theme.Corners
import android.graphics.PointF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ir.mohitban.app.container
import ir.mohitban.app.receipt.ReceiptCropper
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ReceiptCropScreen(rawPath: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val files = context.container.files
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val corners = remember { mutableStateListOf<Offset>() }
    var busy by remember { mutableStateOf(true) }
    /** The straightened receipt waiting for the user's approval. */
    var result by remember { mutableStateOf<Bitmap?>(null) }

    suspend fun detect(b: Bitmap) {
        val c = withContext(Dispatchers.Default) { ReceiptCropper.detect(b) }
        corners.clear()
        corners.addAll(c.map { Offset(it.x, it.y) })
    }

    LaunchedEffect(rawPath) {
        val b = withContext(Dispatchers.IO) { files.decodeFile(File(rawPath), 2000) }
        if (b == null) { onCancel(); return@LaunchedEffect }
        bitmap = b
        detect(b)
        busy = false
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF111512)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت", tint = Color.White,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Corners.R)).clickable(onClick = onCancel).padding(8.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(if (result == null) "برش رسید" else "پیش‌نمایش رسید", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    if (result == null) "گوشه‌ها را روی لبه‌های رسید قرار دهید" else "اگر رسید خوانا است، تأیید کنید تا روی فرم قرار بگیرد",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xB3FFFFFF),
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val b = bitmap
            val r = result
            if (r != null) {
                val img = remember(r) { r.asImageBitmap() }
                val appear = remember(r) { Animatable(0f) }
                LaunchedEffect(r) { appear.animateTo(1f, tween(350)) }
                Image(
                    img, "رسید برش‌خورده", contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = appear.value; scaleX = 0.9f + 0.1f * appear.value; scaleY = 0.9f + 0.1f * appear.value }
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else if (b == null || corners.size != 4) {
                CircularProgressIndicator(color = Color.White)
            } else {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = min(boxW / b.width, boxH / b.height)
                val dw = b.width * scale
                val dh = b.height * scale
                val ox = (boxW - dw) / 2f
                val oy = (boxH - dh) / 2f
                val image = remember(b) { b.asImageBitmap() }
                val touchRadius = with(density) { 44.dp.toPx() }
                var active by remember { mutableStateOf(-1) }

                fun toScreen(p: Offset) = Offset(ox + p.x * scale, oy + p.y * scale)

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(b, scale) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    active = corners.indices.minByOrNull { i ->
                                        val s = toScreen(corners[i]); hypot(s.x - start.x, s.y - start.y)
                                    }?.takeIf { i ->
                                        val s = toScreen(corners[i]); hypot(s.x - start.x, s.y - start.y) < touchRadius * 1.6f
                                    } ?: -1
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 },
                                onDrag = { change, drag ->
                                    if (active >= 0) {
                                        change.consume()
                                        val c = corners[active]
                                        corners[active] = Offset(
                                            (c.x + drag.x / scale).coerceIn(0f, b.width.toFloat()),
                                            (c.y + drag.y / scale).coerceIn(0f, b.height.toFloat()),
                                        )
                                    }
                                },
                            )
                        },
                ) {
                    drawImage(
                        image,
                        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    )
                    val pts = corners.map { toScreen(it) }
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1..3) lineTo(pts[i].x, pts[i].y)
                        close()
                    }
                    drawPath(path, Color(0x2E5BC86E))
                    drawPath(path, Color(0xFF6BD36F), style = Stroke(width = 2.5.dp.toPx()))
                    pts.forEachIndexed { i, p ->
                        drawCircle(Color.White, radius = if (i == active) 15.dp.toPx() else 12.dp.toPx(), center = p)
                        drawCircle(Color(0xFF3E9C46), radius = if (i == active) 15.dp.toPx() else 12.dp.toPx(), center = p, style = Stroke(width = 3.dp.toPx()))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            val r = result
            if (r != null) {
                // ---------- step 2: confirm the straightened receipt ----------
                PillButton(
                    "تأیید و قرار دادن روی فرم",
                    icon = Icons.Outlined.Check,
                    loading = busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val path = withContext(Dispatchers.IO) { files.saveJpeg(r, "receipt", 90) }
                            runCatching { File(rawPath).delete() }
                            onDone(path)
                        }
                    },
                )
                ToolButton(Icons.Outlined.Crop, "ویرایش برش", Modifier.fillMaxWidth().padding(top = 10.dp)) { result = null }
            } else {
                // ---------- step 1: adjust the corners ----------
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolButton(Icons.Outlined.RotateRight, "چرخش", Modifier.weight(1f)) {
                        val b = bitmap ?: return@ToolButton
                        scope.launch {
                            busy = true
                            val rotated = withContext(Dispatchers.Default) { ReceiptCropper.rotate90(b) }
                            bitmap = rotated
                            detect(rotated)
                            busy = false
                        }
                    }
                    ToolButton(Icons.Outlined.CropFree, "کل تصویر", Modifier.weight(1f)) {
                        val b = bitmap ?: return@ToolButton
                        corners.clear()
                        corners.addAll(ReceiptCropper.defaultCorners(b.width, b.height, 0f).map { Offset(it.x, it.y) })
                    }
                }
                PillButton(
                    "برش و پیش‌نمایش",
                    icon = Icons.Outlined.Check,
                    loading = busy,
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = {
                        val b = bitmap ?: return@PillButton
                        if (corners.size != 4) return@PillButton
                        busy = true
                        scope.launch {
                            result = withContext(Dispatchers.Default) { ReceiptCropper.warp(b, corners.map { PointF(it.x, it.y) }) }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(Corners.R))
            .background(Color(0x1FFFFFFF))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
