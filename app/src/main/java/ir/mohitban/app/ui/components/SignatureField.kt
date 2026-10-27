package ir.mohitban.app.ui.components

import android.graphics.Bitmap
import ir.mohitban.app.ui.theme.Corners
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mohitban.app.ui.theme.MColors

/** The signature shown in a form: a new drawing ([bitmap]) or an already saved one ([existing]). */
@Stable
class SignatureFieldState {
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var existing by mutableStateOf<ImageBitmap?>(null)
    /** True once the user drew a new signature or cleared the old one. */
    var changed by mutableStateOf(false)
        private set
    private var preview by mutableStateOf<ImageBitmap?>(null)

    val shown: ImageBitmap? get() = preview ?: existing
    val isEmpty: Boolean get() = bitmap == null && (changed || existing == null)
    val hasNewDrawing: Boolean get() = bitmap != null

    fun set(b: Bitmap?) {
        bitmap = b
        preview = b?.asImageBitmap()
        changed = true
        if (b == null) existing = null
    }
}

@Composable
fun rememberSignatureFieldState(): SignatureFieldState = remember { SignatureFieldState() }

/**
 * Compact signature box. Tapping it opens a large full-screen pad; after "تأیید" the signature
 * shrinks back into this box.
 */
@Composable
fun SignatureField(
    state: SignatureFieldState,
    title: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    height: Int = 104,
) {
    var open by remember { mutableStateOf(false) }
    val border = if (error != null) MColors.Error else Color(0xFFC9D1CB)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(Corners.R))
                .background(Color(0xFFFBFCFB))
                .drawBehind {
                    drawRoundRect(
                        color = border,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                }
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            val img = state.shown
            if (img != null) {
                Image(img, title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 22.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircleIcon(Icons.Outlined.Draw, Color(0xFF7D8C84), Color.White, size = 34.dp, iconSize = 18.dp)
                    Text("برای امضا لمس کنید", style = MaterialTheme.typography.labelSmall, color = MColors.Hint, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 6.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Muted)
                if (required) Text(" *", style = MaterialTheme.typography.labelSmall, color = MColors.Error)
            }
        }
        FieldMessage(error = error, success = null)
    }

    if (open) {
        val pad = rememberSignatureState()
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // a centred card (not edge to edge) so the buttons are never hidden behind the navigation bar
            Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .clip(RoundedCornerShape(Corners.R))
                    .background(Clay.Surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text("با انگشت در کادر زیر امضا کنید", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { pad.clear() }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, null, tint = MColors.Error, modifier = Modifier.size(20.dp))
                        Text("پاک کردن", style = MaterialTheme.typography.labelLarge, color = MColors.Error, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                BigSignatureCanvas(pad, Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinePill("انصراف", { open = false }, Modifier.weight(1f), color = MColors.Muted)
                    PillButton(
                        "تأیید امضا", icon = Icons.Outlined.Check, modifier = Modifier.weight(2f),
                        onClick = {
                            if (pad.hasNewDrawing) state.set(pad.toBitmap(9f)) else if (pad.isEmpty && state.shown == null) state.set(null)
                            open = false
                        },
                    )
                }
                if (state.shown != null) {
                    Text(
                        "حذف امضای فعلی",
                        style = MaterialTheme.typography.labelMedium, color = MColors.Error,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
                            .clickable { state.set(null); open = false }.padding(6.dp),
                    )
                }
            }
            }
        }
    }
}
