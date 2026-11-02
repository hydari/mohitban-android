package ir.mohitban.app.ui.gasht

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.gasht.Weather
import ir.mohitban.app.gasht.WeatherService
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val Sky = Color(0xFF2F9BD6)

fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.Bedtime
    2 -> Icons.Outlined.WbCloudy
    3 -> Icons.Outlined.Cloud
    45, 48 -> Icons.Outlined.Dehaze
    51, 53, 55, 56, 57 -> Icons.Outlined.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Outlined.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Outlined.AcUnit
    95, 96, 99 -> Icons.Outlined.Thunderstorm
    else -> Icons.Outlined.Cloud
}

fun weatherTint(code: Int, isDay: Boolean): Color = when (code) {
    0, 1 -> if (isDay) Color(0xFFF2A007) else Color(0xFF5B6FB5)
    2, 3 -> Color(0xFF6E8796)
    45, 48 -> Color(0xFF8C969C)
    71, 73, 75, 77, 85, 86 -> Color(0xFF3FA7D6)
    95, 96, 99 -> Color(0xFF6A4FB5)
    else -> Color(0xFF2F7FC1)
}

private fun d0(v: Double) = v.roundToInt().fa()

private fun hm(millis: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)).fa()
}

private fun dateOf(millis: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return Jalali.fromGregorian(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH)).formatLong().fa()
}

private fun dayLabel(date: String, index: Int): String {
    if (index == 0) return "امروز"
    if (index == 1) return "فردا"
    val p = date.split('-').mapNotNull { it.toIntOrNull() }
    if (p.size != 3) return date
    val j = Jalali.fromGregorian(p[0], p[1], p[2])
    return j.formatLong().substringBefore(' ')
}

/**
 * Weather for where the ranger is (or the home county): live with internet, and with no
 * internet the last forecast saved on the phone with its date and time — including the
 * forecast for the current hour from that saved data.
 */
@Composable
fun WeatherCard(here: GeoPoint?, home: Pair<GeoPoint, String>?) {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<Weather?>(null) }
    var online by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var placeName by remember { mutableStateOf<String?>(null) }

    val point = here ?: home?.first
    // re-ask only after moving about 5 km
    val key = point?.let { (it.lat * 20).toInt() to (it.lon * 20).toInt() }
    LaunchedEffect(Unit) { if (weather == null) weather = withContext(Dispatchers.IO) { WeatherService.saved(context) } }
    LaunchedEffect(key, tick) {
        val p = point ?: return@LaunchedEffect
        val name = if (here != null) withContext(Dispatchers.IO) {
            runCatching {
                val w = ir.mohitban.app.gasht.Whereabouts.locate(context, p)
                listOfNotNull(w.place?.takeIf { (w.placeDistance ?: 1e9) < 15_000 }?.label, w.county?.let { "شهرستان $it" }).joinToString("، ").ifBlank { null }
            }.getOrNull()
        } else home?.second
        placeName = name
        val saved = weather
        val fresh = saved != null && tick == 0 && System.currentTimeMillis() - saved.fetchedAt < 20 * 60_000L && Geo.distance(saved.point, p) < 5_000
        if (fresh) { online = true; return@LaunchedEffect }
        loading = true
        val got = withContext(Dispatchers.IO) { WeatherService.fetch(context, p, name ?: "موقعیت شما") }
        loading = false
        online = got != null
        if (got != null) weather = got
    }

    AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(Icons.Outlined.WbSunny, Sky, Color.White, size = 36.dp, iconSize = 19.dp)
                Text("  هواشناسی", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                val spin = rememberInfiniteTransition(label = "wx").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEAF4FB))
                        .clickable(enabled = !loading) { tick++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Refresh, "به‌روزرسانی", tint = Sky, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (loading) spin.value else 0f })
                }
            }
            val w = weather
            val title = (if (online == true || w == null) placeName else w.place)?.takeIf { it.isNotBlank() } ?: w?.place
            title?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, tint = Color(0xFFE5483B), modifier = Modifier.size(17.dp))
                    Text(" $it", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                }
            }
            if (online == false) OfflineNote(w)
            if (w == null) {
                Text(
                    when {
                        point == null -> "بعد از پیدا شدن موقعیت GPS نشان داده می‌شود."
                        loading -> "در حال دریافت وضعیت هوا…"
                        else -> "هنوز وضعیت هوایی دریافت نشده است."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }
            val nowMs = System.currentTimeMillis()
            // offline: the saved forecast for this hour; online: the current observation
            val hour = if (online == false) w.hourAt(nowMs) else null
            val code = hour?.code ?: w.now.code
            val isDay = hour?.isDay ?: w.now.isDay
            val temp = hour?.temp ?: w.now.temp
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(if (isDay) listOf(Color(0xFFE3F2FC), Color(0xFFF6FAFD)) else listOf(Color(0xFFDDE2F3), Color(0xFFF1F3FA))))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(weatherIcon(code, isDay), null, tint = weatherTint(code, isDay), modifier = Modifier.size(54.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(WeatherService.describe(code, isDay), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = MColors.Ink)
                    val feels = if (hour == null) w.now.feels else null
                    Text(
                        if (hour != null) "پیش‌بینی ساعت ${hm(nowMs).substringBefore(':')}:۰۰ از داده‌ی ذخیره‌شده"
                        else feels?.let { "دمای احساسی ${d0(it)}°" } ?: "",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                    )
                }
                Text("${d0(temp)}°", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 40.sp), color = MColors.Ink)
            }
            // facts
            val wind = hour?.wind ?: w.now.wind
            val windDir = hour?.windDir ?: w.now.windDir
            val humidity = hour?.humidity ?: w.now.humidity
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Fact("رطوبت", humidity?.let { "٪${it.fa()}" } ?: "—", Modifier.weight(1f))
                Fact("باد", wind?.let { "${d0(it)} کیلومتر" } ?: "—", Modifier.weight(1f))
                Fact("جهت باد", windDir?.let { "از ${Geo.direction(it)}" } ?: "—", Modifier.weight(1f))
            }
            if (hour == null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Fact("تندباد", w.now.gust?.let { "${d0(it)} کیلومتر" } ?: "—", Modifier.weight(1f))
                    Fact("فشار هوا", w.now.pressure?.let { "${d0(it)} hPa" } ?: "—", Modifier.weight(1f))
                    Fact("ابرناکی", w.now.cloud?.let { "٪${it.fa()}" } ?: "—", Modifier.weight(1f))
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Fact("احتمال بارش", hour.pop?.let { "٪${it.fa()}" } ?: "—", Modifier.weight(1f))
                }
            }
            // next hours
            val hours = w.hoursFrom(nowMs, 12)
            if (hours.isNotEmpty()) {
                Text("ساعت‌های آینده", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    hours.forEach { h ->
                        Column(
                            Modifier.width(54.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2F6F8)).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(h.time.substring(11, 13).fa(), style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                            Icon(weatherIcon(h.code, h.isDay), null, tint = weatherTint(h.code, h.isDay), modifier = Modifier.padding(vertical = 3.dp).size(22.dp))
                            Text("${d0(h.temp)}°", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                            h.pop?.takeIf { it >= 20 }?.let { Text("٪${it.fa()}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color(0xFF2F7FC1)) }
                        }
                    }
                }
            }
            // next days
            val days = w.daysFrom(nowMs).take(4)
            if (days.isNotEmpty()) {
                Text("روزهای آینده", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                days.forEachIndexed { i, d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(d.date, i), style = MaterialTheme.typography.labelLarge, color = MColors.Ink, modifier = Modifier.width(64.dp))
                        Icon(weatherIcon(d.code, true), null, tint = weatherTint(d.code, true), modifier = Modifier.size(22.dp))
                        Text(
                            "  " + WeatherService.describe(d.code) + (d.pop?.takeIf { it >= 20 }?.let { " (٪${it.fa()})" } ?: ""),
                            style = MaterialTheme.typography.labelMedium, color = MColors.Muted, maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        Text("${d0(d.max)}° / ${d0(d.min)}°", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                    }
                }
            }
            Text(
                if (online == false) "آخرین به‌روزرسانی: ${dateOf(w.fetchedAt)} ساعت ${hm(w.fetchedAt)}"
                else "به‌روز شده: ${dateOf(w.fetchedAt)} ساعت ${hm(w.fetchedAt)} — منبع: Open-Meteo",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun OfflineNote(w: Weather?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF4DF)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = Color(0xFFB7791F), modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 8.dp)) {
            Text("برای مشاهده‌ی وضعیت لحظه‌ای هوا، اینترنت گوشی را روشن کنید.", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF7A4E0F))
            if (w != null) {
                Text(
                    "آخرین وضعیت هوا مربوط به ${dateOf(w.fetchedAt)} ساعت ${hm(w.fetchedAt)} است.",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF7A4E0F), modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = MColors.Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
    }
}
