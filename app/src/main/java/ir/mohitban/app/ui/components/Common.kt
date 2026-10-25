package ir.mohitban.app.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = MColors.Green, spotColor = MColors.Green)
            .clip(shape)
            .background(if (enabled) MColors.Green else Color(0xFFA9C2B1))
            .clickable(enabled = enabled && !loading, onClick = onClick),
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
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .border(1.5.dp, color, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/* ------------------------------ Surfaces ------------------------------ */

/** White card with the soft shadow of the reference. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .shadow(5.dp, shape, ambientColor = Color(0x22143C1E), spotColor = Color(0x22143C1E))
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFEEF1EE), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Frosted-glass card used on top of the forest picture. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    radius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier = modifier
            .then(if (!dark) Modifier.shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color(0x66000000)) else Modifier)
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0x8A1B3F2A), Color(0x7A102A1A)))
                else Brush.verticalGradient(listOf(Color(0xE0EEF6F0), Color(0xD2E1EFE5))),
            )
            .border(1.dp, if (dark) MColors.GlassDarkBorder else MColors.GlassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

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
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize)) }
}

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
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت", tint = MColors.Ink) }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MColors.Ink, modifier = Modifier.align(Alignment.Center))
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("خانه", Icons.Outlined.Home),
    RECORDS("سوابق", Icons.AutoMirrored.Outlined.FormatListBulleted),
    REPORTS("گزارش‌ها", Icons.Outlined.Assessment),
    SETTINGS("تنظیمات", Icons.Outlined.Settings),
}

/** Dark green floating navigation bar. */
@Composable
fun BottomBar(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(70.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(MColors.GreenNav),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            val on = tab == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(tab.icon, tab.title, tint = if (on) Color(0xFF7FD39A) else Color(0xA6FFFFFF), modifier = Modifier.size(23.dp))
                Text(
                    tab.title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    color = if (on) Color.White else Color(0xA6FFFFFF),
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
