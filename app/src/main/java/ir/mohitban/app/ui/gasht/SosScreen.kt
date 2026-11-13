package ir.mohitban.app.ui.gasht

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import ir.mohitban.app.ui.components.safeBottomPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.gasht.CoordFormat
import ir.mohitban.app.gasht.Compass
import ir.mohitban.app.gasht.Fix
import ir.mohitban.app.gasht.GashtSession
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.gasht.GpsLocation
import ir.mohitban.app.gasht.GuideTarget
import ir.mohitban.app.gasht.MapStore
import ir.mohitban.app.gasht.OfflineMap
import ir.mohitban.app.gasht.SosAlert
import ir.mohitban.app.gasht.SosNotifier
import ir.mohitban.app.gasht.SosStore
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.RankInsignia
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

private val SosRed = Color(0xFFD32F2F)

private fun ago(ms: Long): String {
    val min = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "همین الان"
        min < 60 -> "${min.toInt().fa()} دقیقه پیش"
        min < 60 * 24 -> "${(min / 60).toInt().fa()} ساعت و ${(min % 60).toInt().fa()} دقیقه پیش"
        else -> "${(min / 1440).toInt().fa()} روز پیش"
    }
}

/**
 * Opened by a colleague's emergency SMS: his position blinking on the offline map, who he is,
 * how far and which way, and quick actions (call, message, guide me there).
 */
@Composable
fun SosScreen(id: Long, onBack: () -> Unit, onGuide: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alert by remember { mutableStateOf<SosAlert?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val ver by SosStore.version.collectAsState()
    LaunchedEffect(id, ver) {
        alert = withContext(Dispatchers.IO) { SosStore.get(context, id) ?: SosStore.all(context).firstOrNull() }
        loaded = true
        SosNotifier.cancel(context, id)
    }
    BackHandler(onBack = onBack)

    val gps = remember { GpsLocation.hasPermission(context) }
    val fix by remember { if (gps) GpsLocation.updates(context) else emptyFlow() }.collectAsState(initial = null as Fix?)
    val magnetic by remember { Compass.heading(context) }.collectAsState(initial = null as Float?)
    val heading = trueHeading(magnetic, fix)

    // the downloaded county/province map that covers the place, if any
    var map by remember { mutableStateOf<OfflineMap?>(null) }
    var labels by remember { mutableStateOf<List<ir.mohitban.app.gasht.MapLabel>>(emptyList()) }
    LaunchedEffect(alert?.id) {
        val a = alert ?: return@LaunchedEffect
        val found = withContext(Dispatchers.IO) {
            MapStore.list(context).firstNotNullOfOrNull { f ->
                runCatching { OfflineMap(f) }.getOrNull()?.let { m -> if (m.bounds != null && m.contains(a.focus)) m else { m.close(); null } }
            }
        }
        map = found
        labels = withContext(Dispatchers.IO) { found?.labels().orEmpty() }
    }
    DisposableEffect(Unit) { onDispose { runCatching { map?.close() } } }
    val background = remember(map) { map?.let { TileBackground(it, scope) } ?: GridBackground }

    val t = rememberInfiniteTransition(label = "sos")
    val blink by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "b")

    val tint = alert?.let { Color(it.kind.color) } ?: SosRed
    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        // header in the colour of the report
        Row(
            Modifier.fillMaxWidth().background(if (alert?.resolvedAt != null) Color(0xFF1B7F3B) else tint).statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "بازگشت", tint = Color.White)
            }
            Icon(
                when (alert?.kind) { ir.mohitban.app.gasht.SosKind.POACHER -> Icons.Outlined.Visibility; ir.mohitban.app.gasht.SosKind.FIRE -> Icons.Outlined.LocalFireDepartment; else -> Icons.Outlined.Sos },
                null, tint = Color.White, modifier = Modifier.size(30.dp).graphicsLayer { alpha = if (alert?.resolvedAt != null) 1f else blink },
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    when {
                        alert?.resolvedAt != null -> "${alert?.kind?.title.orEmpty()} — ${alert?.kind?.done.orEmpty()} ✓"
                        alert == null || alert?.kind == ir.mohitban.app.gasht.SosKind.HELP -> "هشدار اضطراری محیط‌بان"
                        else -> "هشدار ${alert?.kind?.title}"
                    }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
                alert?.let { Text("دریافت ${ago(it.receivedAt)}", style = MaterialTheme.typography.labelSmall, color = Color(0xE6FFFFFF)) }
            }
        }
        val a = alert
        if (a == null) {
            if (loaded) Text("این هشدار پیدا نشد.", style = MaterialTheme.typography.bodyMedium, color = MColors.Muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).safeBottomPadding()) {
            // who
            AppCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(a.name.ifBlank { "محیط‌بان" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = MColors.Ink)
                    if (a.grade.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            RankInsignia(a.grade, 24.dp)
                            Text("  ${a.grade}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
                        }
                    }
                    if (a.note.isNotBlank()) {
                        Text(
                            a.note, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = tint,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    if (a.station.isNotBlank()) Text("پاسگاه ${a.station}", style = MaterialTheme.typography.bodyMedium, color = MColors.Ink, modifier = Modifier.padding(top = 6.dp))
                    LtrText(a.from, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = a.sentAt }
                    Text(
                        "موقعیت در ساعت ${String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)).fa()} " +
                            "${jalaliOf(a.sentAt).formatLong(false).fa()} (${ago(a.sentAt)})" + (a.accuracy?.let { " — دقت ±${it.fa()} متر" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // where
            val f = fix
            val pad = 0.004
            val goal = a.focus
            val base = listOfNotNull(goal, a.target?.let { a.point })
            val fit = if (f != null && Geo.distance(f.point, goal) < 60_000) base + f.point
            else if (base.size > 1) base
            else listOf(GeoPoint(goal.lat - pad, goal.lon - pad), GeoPoint(goal.lat + pad, goal.lon + pad))
            val reporter = a.name.ifBlank { "محیط‌بان" }
            TrackCanvas(
                // the line from the reporting ranger to the poacher / fire
                if (a.target != null) listOf(MapTrack(-2, listOf(a.point, a.target), tint.copy(alpha = 0.7f), 4f)) else emptyList(),
                if (a.target != null) listOf(MapPlace(reporter, a.point, Color(0xFF2F7FC1))) else emptyList(),
                f, heading,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(380.dp),
                background = background, labels = labels, attribution = map?.attribution.orEmpty(),
                fitKey = a.id to (f != null), fitPoints = fit,
                alerts = if (a.resolvedAt != null) emptyList() else listOf(MapAlert(goal, tint, if (a.target != null) a.kind.target else reporter)),
            )
            if (map == null) {
                Text(
                    "نقشه‌ی این منطقه روی گوشی نیست؛ از بخش نقشه‌ها در گشت‌یار نقشه‌ی شهرستان را دانلود کنید.",
                    style = MaterialTheme.typography.labelSmall, color = MColors.Amber, modifier = Modifier.padding(top = 4.dp),
                )
            }
            // distance and direction from me
            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (f != null) {
                        val bearing = Geo.bearing(f.point, a.focus)
                        if (heading != null) GuideArrow((bearing - heading).toFloat(), tint, Modifier.size(64.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(CoordFormat.meters(Geo.distance(f.point, a.focus)).fa(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = tint)
                            Text("${if (a.target != null) a.kind.target else "محیط‌بان"} به سمت ${Geo.direction(bearing)} (${bearing.toInt().fa()}°) از موقعیت شما", style = MaterialTheme.typography.labelMedium, color = MColors.Ink)
                        }
                    } else {
                        Text(
                            if (gps) "در حال پیدا کردن موقعیت شما برای محاسبه‌ی فاصله…" else "برای دیدن فاصله و جهت، مکان‌یابی (GPS) را روشن کنید.",
                            style = MaterialTheme.typography.bodySmall, color = MColors.Muted,
                        )
                    }
                }
            }
            if (!a.verified) Text(
                "این پیامک با کد تیم شما تأیید نشد و ممکن است جعلی باشد. قبل از اقدام با فرستنده تماس بگیرید.",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFC62828), modifier = Modifier.padding(top = 10.dp),
            )
            if (a.target != null) Text(a.kind.target, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = tint, modifier = Modifier.padding(top = 10.dp))
            LtrText(CoordFormat.decimal(a.focus), Modifier.fillMaxWidth().padding(top = if (a.target != null) 0.dp else 10.dp), style = MaterialTheme.typography.bodyMedium)
            LtrText("UTM " + CoordFormat.utm(a.focus), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
            // found / dealt with
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (a.resolvedAt != null) Color(0xFFE8F5EC) else Color(0xFF1B7F3B))
                    .clickable { SosStore.resolve(context, a.id, a.resolvedAt == null) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = if (a.resolvedAt != null) Color(0xFF1B7F3B) else Color.White, modifier = Modifier.size(22.dp))
                Text(
                    if (a.resolvedAt != null) "  ${a.kind.done} — برگرداندن" else "  ${a.kind.done}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    color = if (a.resolvedAt != null) Color(0xFF1B7F3B) else Color.White,
                )
            }
            // actions
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SosAction(Icons.Outlined.Call, "تماس", SosRed, Modifier.weight(1f)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${a.from}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                SosAction(Icons.Outlined.Sms, "پیامک", Color(0xFF2F7FC1), Modifier.weight(1f)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${a.from}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SosAction(Icons.Outlined.Directions, "راهنمای رسیدن", MColors.Green, Modifier.weight(1.3f)) {
                    GashtSession.follow(context, GuideTarget.Place(if (a.target != null) a.kind.target else "محل ${a.name.ifBlank { "محیط‌بان" }}", a.focus))
                    onGuide()
                }
                SosAction(Icons.Outlined.ContentCopy, "کپی مختصات", MColors.Muted, Modifier.weight(1f)) {
                    copyText(context, "${CoordFormat.decimal(a.focus)}\nUTM ${CoordFormat.utm(a.focus)}")
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SosAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(50.dp).clip(RoundedCornerShape(14.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text("  $text", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}
