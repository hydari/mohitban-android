package ir.mohitban.app.ui.components

import androidx.compose.foundation.Image
import ir.mohitban.app.ui.theme.Corners
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.ui.theme.MColors

/* ------------------------------ Buttons ------------------------------ */

/** Full-width green capsule button with the icon on the far (left) side, like the reference. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val press = rememberPress()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clayPress(press)
            .clay(color = if (enabled) MColors.Green2 else Color(0xFFA9C2B1), depth = if (enabled) 10.dp else 2.dp)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        if (icon != null && !loading) {
            Icon(
                icon, null, tint = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 22.dp)
                    .size(22.dp),
            )
        }
    }
}

@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = MColors.Green) {
    val press = rememberPress()
    Row(
        modifier = modifier
            .height(52.dp)
            .clayPress(press)
            .clay(depth = 6.dp)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/* ------------------------------ Surfaces ------------------------------ */

/** White card with the soft shadow of the reference. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    radius: Dp = Corners.R,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val press = rememberPress()
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clayPress(press) else Modifier)
            .clay(radius)
            .then(if (onClick != null) Modifier.clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier),
        content = content,
    )
}

/**
 * What the glass cards blur: a pre-blurred copy of the full-screen background picture and the
 * size of the screen area it is drawn into (ContentScale.Crop). Provided by the dashboard.
 */
class GlassBackdrop(val blurred: ImageBitmap, val rootSize: IntSize)

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Draws the blurred backdrop that lies behind a glass element at [position] (screen coordinates). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackdrop(backdrop: GlassBackdrop?, position: Offset) {
    if (backdrop == null) return
    val img = backdrop.blurred
    val root = backdrop.rootSize
    if (root.width == 0 || root.height == 0) return
    val scale = maxOf(root.width.toFloat() / img.width, root.height.toFloat() / img.height)
    val w = img.width * scale
    val h = img.height * scale
    drawImage(
        img,
        dstOffset = IntOffset(((root.width - w) / 2f - position.x).roundToInt(), ((root.height - h) / 2f - position.y).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.Low,
    )
}

/**
 * Liquid-glass card: the blurred photo behind the card at its exact screen position, a translucent
 * tint, a specular sheen on the upper half, a light gradient rim and a soft shadow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    radius: Dp = Corners.R,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    // formerly liquid glass; now the same clay surface as every card (dark = a faint green tint)
    val press = rememberPress()
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clayPress(press) else Modifier)
            .clay(radius, color = if (dark) Color(0xFFF1F6F2) else Clay.Surface)
            .then(if (onClick != null) Modifier.clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Skeuomorphic round icon: a raised disc with a light-to-dark gradient of [background], a glossy
 * highlight on top, a darker rim and a soft drop shadow; the glyph has a subtle embossed shadow.
 */
@Composable
fun CircleIcon(
    icon: ImageVector,
    background: Color,
    tint: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier,
    border: Color? = null,
) {
    // light disc + coloured glyph → turn into a glossy coloured button with a white glyph
    var background = background
    var tint = tint
    if (background.alpha >= 0.5f && background.luminance() > 0.72f && tint.luminance() < 0.45f) {
        background = tint
        tint = Color.White
    }
    val transparent = background.alpha < 0.5f
    Box(
        modifier = modifier
            .size(size)
            .then(if (!transparent) Modifier.shadow(5.dp, CircleShape, ambientColor = Color.Black, spotColor = Color(0x59000000)) else Modifier)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(lerpColor(background, Color.White, 0.35f), background, lerpColor(background, Color.Black, 0.18f)),
                ),
            )
            .drawBehind {
                // glossy cap
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), endY = this.size.height * 0.55f),
                    topLeft = Offset(this.size.width * 0.14f, this.size.height * 0.04f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 0.72f, this.size.height * 0.48f),
                )
            }
            .border(
                1.dp,
                border?.let { Brush.verticalGradient(listOf(it, it)) }
                    ?: Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), lerpColor(background, Color.Black, 0.3f).copy(alpha = 0.5f))),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // embossed glyph: a soft dark copy under the icon
        Icon(icon, null, tint = Color.Black.copy(alpha = 0.18f), modifier = Modifier.size(iconSize).offset(y = 1.dp))
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Linear blend between two colours. */
fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha,
)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = MColors.Green) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun TagBadge(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/* ------------------------------ Bars ------------------------------ */

/** Centered title, back arrow on the right (RTL start), optional action on the left. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(48.dp),
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { CircleIcon(Icons.AutoMirrored.Outlined.ArrowBack, MColors.Green, Color.White, size = 36.dp, iconSize = 19.dp) }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MColors.Ink, modifier = Modifier.align(Alignment.Center))
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}

enum class MainTab(val title: String, val icon: ImageVector, val color: Color) {
    HOME("خانه", Icons.Outlined.Home, Color(0xFF239B56)),
    RECORDS("سوابق", Icons.AutoMirrored.Outlined.FormatListBulleted, Color(0xFF2F7FC1)),
    REPORTS("گزارش‌ها", Icons.Outlined.Assessment, Color(0xFFE08A1E)),
    SETTINGS("تنظیمات", Icons.Outlined.Settings, Color(0xFF5F6F7A)),
}

/** Floating glass navigation bar (blurred photo behind it on the dashboard, frosted green elsewhere). */
@Composable
fun BottomBar(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val glass = false // every screen uses the clay bar now
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clay(depth = 12.dp)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            val on = tab == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (on) Modifier.clayInset(12.dp, Color(0xFFE2EEE5)) else Modifier,
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CircleIcon(
                    tab.icon, if (on) tab.color else lerpColor(tab.color, Color(0xFF9AA5A0), 0.55f), Color.White,
                    size = if (on) 34.dp else 30.dp, iconSize = if (on) 19.dp else 17.dp,
                )
                Text(
                    tab.title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    color = if (glass) (if (on) Color.White else Color(0xBFFFFFFF)) else (if (on) MColors.Green else MColors.Muted),
                )
            }
        }
    }
}

/* ------------------------------ Stepper ------------------------------ */

@Composable
fun StepIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val done = i < current
            val active = i == current
            Box(
                Modifier
                    .then(if (active) Modifier.shadow(0.dp, CircleShape) else Modifier)
                    .size(if (active) 44.dp else 34.dp)
                    .clip(CircleShape)
                    .background(if (active) MColors.Green.copy(alpha = 0.15f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> MColors.Green
                                done -> MColors.GreenLight
                                else -> Color.White
                            },
                        )
                        .border(1.5.dp, if (active) MColors.Green else if (done) MColors.Green2 else Color(0xFFCFD6D1), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Outlined.Check, null, tint = MColors.Green, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            (i + 1).fa(),
                            color = if (active) Color.White else Color(0xFF555555),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            if (i < count - 1) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 6.dp)
                        .background(if (i < current) MColors.Green2 else Color(0xFFDDE3DF)),
                )
            }
        }
    }
}

/* ------------------------------ Misc ------------------------------ */

@Composable
fun ImageOrPlaceholder(
    bitmap: ImageBitmap?,
    modifier: Modifier,
    placeholder: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            placeholder()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "انصراف",
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = MColors.Muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) MColors.Error else MColors.Green, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, color = MColors.Muted) } },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircleIcon(icon, MColors.GreenLight, MColors.Green, size = 72.dp, iconSize = 34.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MColors.Muted, textAlign = TextAlign.Center)
    }
}
