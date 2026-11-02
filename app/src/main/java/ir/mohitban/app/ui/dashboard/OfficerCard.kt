package ir.mohitban.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mohitban.app.R
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.serviceText
import ir.mohitban.app.ui.components.RankInsignia
import ir.mohitban.app.ui.components.rememberMotionOn
import kotlin.math.PI
import kotlin.math.sin

private val Gold = Color(0xFFE9C25A)
private val GoldSoft = Color(0xFFF3DC95)

/**
 * The ranger's official card: deep organisation green with a gold edge and a faint emblem, the
 * photo in its shift ring, full name, rank with its insignia, post, station and years of service.
 * A slow light sweeps across it now and then.
 */
@Composable
fun OfficerCard(
    profile: RangerProfile?,
    photo: ImageBitmap?,
    today: Int,
    rim: Color?,
    bell: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionOn()
    val t = rememberInfiniteTransition(label = "officer")
    val sweep = t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(7000, delayMillis = 1200, easing = LinearEasing)), label = "sw")
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(178.dp)
            .shadow(14.dp, shape, ambientColor = Color(0xFF0B2414), spotColor = Color(0xFF0B2414))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0F3B22), Color(0xFF17512F), Color(0xFF0C3020)), start = Offset.Zero, end = Offset.Infinite))
            .drawBehind {
                // a faint light from the top and a slow diagonal sheen
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = size.height * 0.5f))
                if (motion) {
                    val x = sweep.value * size.width
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.09f), 1f to Color.Transparent,
                            start = Offset(x - size.width * 0.25f, 0f), end = Offset(x + size.width * 0.25f, size.height),
                        ),
                    )
                }
            }
            .border(1.dp, Brush.verticalGradient(listOf(Gold.copy(alpha = 0.75f), Gold.copy(alpha = 0.25f))), shape)
            .clickable(onClick = onClick),
    ) {
        // the emblem as a watermark on the far side
        Image(
            painterResource(R.drawable.doe_emblem), null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(150.dp).graphicsLayer { alpha = 0.07f },
        )
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // photo inside the shift ring and a thin gold ring
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(rim ?: Gold).padding(3.dp)
                    .clip(CircleShape).background(Gold).padding(2.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                else Image(painterResource(R.drawable.avatar_default), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // name and the alert bell on one line
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile?.fullName.orEmpty(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, shadow = Shadow(Color(0x66000000), Offset(0f, 2f), 6f)),
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    bell()
                }
                val grade = profile?.grade.orEmpty().let { g -> ir.mohitban.app.domain.model.Grades.ALL.getOrNull(ir.mohitban.app.domain.model.Grades.indexOf(g)) ?: g }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (grade.isNotBlank()) {
                        RankInsignia(grade, 24.dp)
                        Text(
                            "  $grade", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Gold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text("درجه را در اطلاعات فردی انتخاب کنید", style = MaterialTheme.typography.labelSmall, color = GoldSoft)
                    }
                }
                val post = profile?.rank?.takeIf { it.isNotBlank() }
                val station = profile?.station?.takeIf { it.isNotBlank() }
                if (post != null || station != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Color(0xFFFF8A7A), modifier = Modifier.size(15.dp))
                        Text(
                            " " + listOfNotNull(post, station?.let { "پاسگاه $it" }).joinToString(" | "),
                            style = MaterialTheme.typography.labelMedium, color = Color(0xE6FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val hire = profile?.hireJdn
                Row(
                    Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x26FFFFFF))
                        .border(0.8.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WorkHistory, null, tint = Gold, modifier = Modifier.size(14.dp))
                    Text(
                        if (hire != null) "  ${serviceText(hire, today).fa()} سابقه‌ی خدمت" else "  تاریخ استخدام را وارد کنید",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Shown on the ranger's birthday: a warm card with drifting confetti and balloons, a burst when
 * it appears, and the greeting.
 */
@Composable
fun BirthdayCard(fullName: String, modifier: Modifier = Modifier) {
    val motion = rememberMotionOn()
    val burst = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
    val t = rememberInfiniteTransition(label = "bday")
    val drift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "d")
    val bob = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val first = fullName.trim().split(" ").firstOrNull().orEmpty()
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.fillMaxWidth().height(132.dp)
            .shadow(10.dp, shape).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFFF3D6), Color(0xFFFFE1C2), Color(0xFFFCD3D9)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val colors = listOf(Color(0xFFE5483B), Color(0xFF2F7FC1), Color(0xFF239B56), Color(0xFFE9C25A), Color(0xFF8E5BD0))
            val w = size.width; val h = size.height
            // confetti: a burst from the cake, then a gentle fall
            for (i in 0 until 46) {
                val r1 = ((i * 73) % 100) / 100f; val r2 = ((i * 37 + 11) % 100) / 100f; val r3 = ((i * 53 + 29) % 100) / 100f
                val b = burst.value
                val sx = w * 0.88f; val sy = h * 0.5f
                val ang = (r1 * 2 * PI).toFloat()
                val bx = sx + kotlin.math.cos(ang) * w * (0.2f + 0.7f * r2) * b
                val by = sy + sin(ang) * h * (0.3f + 0.6f * r3) * b
                val fall = if (motion) ((drift.value + r3) % 1f) else r3
                val x = if (b < 1f) bx else w * r2 + sin((fall * 6f + r1 * 6f).toDouble()).toFloat() * 10f
                val y = if (b < 1f) by else fall * (h + 20f) - 10f
                rotate(fall * 360f * (if (i % 2 == 0) 1 else -1), Offset(x, y)) {
                    drawRect(colors[i % colors.size].copy(alpha = 0.85f), Offset(x - 3f, y - 1.5f), Size(7f, 3.5f))
                }
            }
            // three balloons bobbing on the far (left) side; the text is on the right in Persian
            listOf(0.08f to colors[0], 0.18f to colors[1], 0.28f to colors[3]).forEachIndexed { i, (fx, c) ->
                val y = h * (0.36f + 0.05f * i) - bob.value * 8f * (if (i == 1) -1 else 1)
                val cx = w * fx
                drawLine(Color(0x88555555), Offset(cx, y + 22f), Offset(cx + 6f, h), 1.5f)
                drawOval(c, Offset(cx - 17f, y - 22f), Size(34f, 44f))
                drawOval(Color.White.copy(alpha = 0.45f), Offset(cx - 9f, y - 16f), Size(8f, 13f))
            }
        }
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).graphicsLayer { val sc = 0.6f + 0.4f * burst.value; scaleX = sc; scaleY = sc }
                    .clip(CircleShape).background(Color(0xFFE5483B)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Cake, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Column(Modifier.padding(start = 12.dp).width(210.dp)) {
                Text(
                    "${first.ifEmpty { "همکار" }} عزیز، تولدت مبارک!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = Color(0xFF7A2E1F),
                )
                Text(
                    "سالی پر از سلامتی، آرامش و گشت‌های بی‌خطر برایت آرزو می‌کنیم.",
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF5A3A2E), modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
