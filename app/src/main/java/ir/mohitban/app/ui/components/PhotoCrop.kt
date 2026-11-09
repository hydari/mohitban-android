package ir.mohitban.app.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crops a personnel photo to exactly the round profile frame: pinch to zoom, drag to place,
 * turn by 90°, then confirm. Returns a square [outSize] × [outSize] picture.
 */
@Composable
fun PhotoCropDialog(source: Bitmap, onDismiss: () -> Unit, onCropped: (Bitmap) -> Unit, outSize: Int = 640) {
    var bmp by remember { mutableStateOf(source) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    val image = remember(bmp) { bmp.asImageBitmap() }
    var view by remember { mutableStateOf(IntSize.Zero) }
    // circle diameter on screen and the zoom at which the picture just covers it
    val d = min(view.width, view.height) * 0.86f
    val base = if (d > 0f) d / min(bmp.width, bmp.height) else 1f
    fun clamp() {
        val s = base * zoom
        val mx = max(0f, (bmp.width * s - d) / 2); val my = max(0f, (bmp.height * s - d) / 2)
        ox = ox.coerceIn(-mx, mx); oy = oy.coerceIn(-my, my)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0E1311)).statusBarsPadding().safeBottomPadding()) {
            Text(
                "عکس را با دو انگشت بزرگ و کوچک کنید و جابه‌جا کنید تا صورت داخل دایره قرار بگیرد.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xCCFFFFFF), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.fillMaxSize().onSizeChanged { view = it }.pointerInput(bmp, view) {
                        detectTransformGestures { _, pan, z, _ ->
                            zoom = (zoom * z).coerceIn(1f, 6f)
                            ox += pan.x; oy += pan.y
                            clamp()
                        }
                    },
                ) {
                    val s = base * zoom
                    val w = bmp.width * s; val h = bmp.height * s
                    val cx = size.width / 2 + ox; val cy = size.height / 2 + oy
                    drawImage(
                        image, dstOffset = IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    )
                    // darken everything outside the round frame
                    val c = Offset(size.width / 2, size.height / 2)
                    val hole = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(c, d / 2))
                    }
                    drawPath(hole, Color.Black.copy(alpha = 0.62f))
                    drawCircle(Color.White, d / 2, c, style = Stroke(2.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircleIcon(Icons.AutoMirrored.Outlined.RotateRight, Color(0xFF2F7FC1), Color.White, size = 44.dp, iconSize = 22.dp,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(90f) }, true)
                                zoom = 1f; ox = 0f; oy = 0f
                            }
                        })
                }
                CancelButton("انصراف", Modifier.weight(1f), onClick = onDismiss)
                OkButton("تأیید و ثبت", Modifier.weight(1.4f)) {
                    // the circle on screen, mapped back onto the picture
                    if (d <= 0f) return@OkButton
                    val s = base * zoom
                    val side = min(d / s, min(bmp.width, bmp.height).toFloat())
                    val cxB = bmp.width / 2f - ox / s; val cyB = bmp.height / 2f - oy / s
                    val left = (cxB - side / 2).coerceIn(0f, bmp.width - side); val top = (cyB - side / 2).coerceIn(0f, bmp.height - side)
                    val sq = Bitmap.createBitmap(bmp, left.roundToInt(), top.roundToInt(), min(side.roundToInt(), bmp.width - left.roundToInt()), min(side.roundToInt(), bmp.height - top.roundToInt()))
                    onCropped(Bitmap.createScaledBitmap(sq, outSize, outSize, true))
                }
            }
        }
    }
}
