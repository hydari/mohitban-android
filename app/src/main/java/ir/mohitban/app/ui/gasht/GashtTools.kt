package ir.mohitban.app.ui.gasht

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import ir.mohitban.app.container
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.gasht.CoordFormat
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.gasht.SunTimes
import ir.mohitban.app.gasht.Utm
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ToolsTab(vm: GashtViewModel) {
    val fix by vm.fix.collectAsStateWithLifecycle()
    val compass by vm.compass.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        EmergencyCard(fix)
        if (vm.zones.isNotEmpty()) ZonesCard(vm, fix?.point)
        CompassCard(trueHeading(compass, fix), compass != null, fix?.point)
        AreaCard(fix?.point, fix?.accuracy)
        ConverterCard(fix?.point)
        SunCard(fix?.point)
        TorchCard()
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ToolCard(icon: ImageVector, color: Color, title: String, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(icon, color, Color.White, size = 36.dp, iconSize = 19.dp)
                Text("  $title", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------- emergency SMS

private const val PREFS = "gasht_tools"

@Composable
private fun EmergencyCard(fix: ir.mohitban.app.gasht.Fix?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var numbers by remember { mutableStateOf(prefs.getString("sos_numbers", "").orEmpty()) }
    var editing by remember { mutableStateOf(numbers.isBlank()) }
    val name = rememberRangerName()
    ToolCard(Icons.Outlined.Sos, MColors.Error, "پیامک اضطراری") {
        if (editing) {
            MField(numbers, { numbers = it.filter { c -> c.isDigit() || c == ',' || c == '،' || c == ' ' } }, label = "شماره‌ها (با ویرگول جدا کنید)", placeholder = "09xxxxxxxxx، 09xxxxxxxxx", maxLength = 60)
            PillButton("ذخیره شماره‌ها", {
                prefs.edit().putString("sos_numbers", numbers).apply(); editing = false
            }, Modifier.padding(top = 8.dp))
        } else {
            Text("گیرندگان: ${numbers.fa()}", style = MaterialTheme.typography.bodySmall, color = MColors.Ink)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("ارسال پیامک اضطراری", {
                    val list = numbers.split(',', '،', ' ').map { it.trim() }.filter { it.length >= 5 }
                    val body = fix?.let { positionMessage("اضطراری! محیط‌بان نیاز به کمک دارد", name, it) }
                        ?: "اضطراری! محیط‌بان ${name} نیاز به کمک دارد. (موقعیت GPS هنوز پیدا نشده است)"
                    runCatching {
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + list.joinToString(";"))).putExtra("sms_body", body)
                        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, Modifier.weight(1f))
            }
            Text(
                "پیام آماده با مختصات در برنامه‌ی پیامک باز می‌شود؛ فقط دکمه‌ی ارسال را بزنید. بدون اینترنت و فقط با آنتن تلفن کار می‌کند.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
            )
            SmallAction(Icons.Outlined.SwapHoriz, "تغییر شماره‌ها", Modifier.padding(top = 6.dp), color = MColors.Muted) { editing = true }
        }
    }
}

// ---------------------------------------------------------------- compass

/** The middle of the county chosen at registration, with its name (for when there is no GPS fix). */
@Composable
private fun rememberHomePlace(): Pair<GeoPoint, String>? {
    val context = LocalContext.current
    var home by remember { mutableStateOf<Pair<GeoPoint, String>?>(null) }
    LaunchedEffect(Unit) {
        home = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val p = context.container.profileRepository.getProfile() ?: return@withContext null
            ir.mohitban.app.gasht.Whereabouts.countyCenter(context, p.city, p.province)?.let { it to "شهرستان ${p.city.trim().removePrefix("شهرستان").trim()}" }
        }
    }
    return home
}

private val KAABA = GeoPoint(21.422487, 39.826206)

@Composable
private fun CompassCard(heading: Float?, available: Boolean, here: GeoPoint?) {
    val home = rememberHomePlace()
    val from = here ?: home?.first
    val qibla = from?.let { Geo.bearing(it, KAABA).toFloat() }
    ToolCard(Icons.Outlined.Explore, Color(0xFF2F7FC1), "قطب‌نما و قبله‌نما") {
        if (!available || heading == null) {
            Text("حسگر قطب‌نما در این گوشی در دسترس نیست.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
            qibla?.let {
                Text(
                    "جهت قبله: ${it.toInt().fa()}° از شمال (${Geo.direction(it.toDouble())})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1F7A4A), modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CompassRose(heading, Modifier.size(240.dp), qibla = qibla)
                Text(
                    "${heading.toInt().fa()}° ${Geo.direction(heading.toDouble())}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), modifier = Modifier.padding(top = 8.dp),
                )
                if (qibla != null) {
                    val off = ((qibla - heading) % 360f + 540f) % 360f - 180f
                    val facing = kotlin.math.abs(off) <= 4f
                    Row(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (facing) Color(0xFF1F7A4A) else Color(0xFFE6F2E9)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (facing) "رو به قبله هستید"
                            else "قبله: ${qibla.toInt().fa()}° — ${kotlin.math.abs(off).toInt().fa()}° به ${if (off > 0) "راست" else "چپ"} بچرخید",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (facing) Color.White else Color(0xFF1F7A4A),
                        )
                    }
                    Text(
                        if (here != null) "قبله برای موقعیت فعلی شما حساب شده است." else "قبله برای مرکز ${home?.second.orEmpty()} حساب شده؛ با روشن شدن GPS دقیق‌تر می‌شود.",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "عقربه‌ی قرمز شمال جغرافیایی (اصلاح‌شده با انحراف مغناطیسی) و علامت سبز کعبه جهت قبله است. گوشی را صاف و دور از فلز و خودرو نگه دارید؛ اگر خطا دارد، گوشی را چند بار به شکل ۸ بچرخانید.",
                    style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- distance & area

@Composable
private fun AreaCard(here: GeoPoint?, accuracy: Float?) {
    var corners by remember { mutableStateOf(listOf<GeoPoint>()) }
    ToolCard(Icons.Outlined.SquareFoot, Color(0xFFE08A1E), "اندازه‌گیری مساحت و محیط") {
        Text(
            "دور محدوده (مثلاً محل آتش‌سوزی یا تصرف) راه بروید و در هر گوشه «افزودن نقطه» را بزنید.",
            style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(Icons.Outlined.AddLocationAlt, "افزودن نقطه", Modifier.weight(1.4f), enabled = here != null) { here?.let { corners = corners + it } }
            SmallAction(Icons.Outlined.Undo, "حذف آخر", Modifier.weight(1f), enabled = corners.isNotEmpty(), color = MColors.Muted) { corners = corners.dropLast(1) }
            SmallAction(Icons.Outlined.Delete, "از نو", Modifier.weight(1f), enabled = corners.isNotEmpty(), color = MColors.Error) { corners = emptyList() }
        }
        accuracy?.let { Text("دقت فعلی GPS: ±${it.toInt().fa()} متر", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
        val perimeter = Geo.pathLength(corners) + if (corners.size > 2) Geo.distance(corners.last(), corners.first()) else 0.0
        val area = Geo.area(corners)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Result("نقاط", corners.size.fa(), Modifier.weight(1f))
            Result("محیط / فاصله", CoordFormat.meters(perimeter).fa(), Modifier.weight(1f))
            Result("مساحت", if (corners.size > 2) "${String.format(java.util.Locale.US, "%.2f", area / 10_000).fa()} هکتار" else "—", Modifier.weight(1f))
        }
        if (corners.size > 2) {
            Text(
                "${String.format(java.util.Locale.US, "%,.0f", area).fa()} متر مربع",
                style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Result(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
    }
}

// ---------------------------------------------------------------- converter

@Composable
private fun ConverterCard(here: GeoPoint?) {
    var input by remember { mutableStateOf("") }
    var utmMode by remember { mutableStateOf(false) }
    var zone by remember { mutableStateOf("39") }
    var east by remember { mutableStateOf("") }
    var north by remember { mutableStateOf("") }
    ToolCard(Icons.Outlined.SwapHoriz, Color(0xFF5F6F7A), "تبدیل مختصات") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip("از درجه", !utmMode, Modifier.weight(1f)) { utmMode = false }
            ChoiceChip("از UTM", utmMode, Modifier.weight(1f)) { utmMode = true }
        }
        Spacer(Modifier.height(6.dp))
        val p: GeoPoint? = if (!utmMode) {
            MField(input, { input = it }, label = "مختصات", placeholder = "35.689200, 51.389000", maxLength = 60)
            if (here != null && input.isBlank()) {
                SmallAction(Icons.Outlined.AddLocationAlt, "موقعیت فعلی", Modifier.padding(top = 4.dp)) { input = CoordFormat.decimal(here) }
            }
            CoordFormat.parse(input)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MField(zone, { zone = it }, Modifier.weight(0.6f), label = "زون", numeric = NumericMode.Digits, maxLength = 2)
                MField(east, { east = it }, Modifier.weight(1f), label = "Easting", numeric = NumericMode.Digits, maxLength = 7)
                MField(north, { north = it }, Modifier.weight(1.1f), label = "Northing", numeric = NumericMode.Digits, maxLength = 8)
            }
            val z = zone.toIntOrNull(); val e = east.toDoubleOrNull(); val n = north.toDoubleOrNull()
            if (z != null && e != null && n != null && z in 1..60) Utm.toLatLon(Utm.Coord(z, true, e, n)) else null
        }
        if (p != null) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                LtrText(CoordFormat.decimal(p), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                LtrText(CoordFormat.dms(p), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                LtrText("UTM " + CoordFormat.utm(p), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                here?.let {
                    Text(
                        "از موقعیت شما: ${CoordFormat.meters(Geo.distance(it, p)).fa()} به سمت ${Geo.direction(Geo.bearing(it, p))} (${Geo.bearing(it, p).toInt().fa()}°)",
                        style = MaterialTheme.typography.labelMedium, color = MColors.Green, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sunrise / sunset

@Composable
private fun SunCard(here: GeoPoint?) {
    val context = LocalContext.current
    // the place the times are for: the phone's position (named from the built-in data), or else
    // the county chosen at registration
    var home by remember { mutableStateOf<Pair<GeoPoint, String>?>(null) }
    LaunchedEffect(Unit) {
        home = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val p = context.container.profileRepository.getProfile() ?: return@withContext null
            ir.mohitban.app.gasht.Whereabouts.countyCenter(context, p.city, p.province)?.let { it to "شهرستان ${p.city.trim().removePrefix("شهرستان").trim()}" }
        }
    }
    var hereName by remember { mutableStateOf<String?>(null) }
    // re-name only after moving about a kilometre
    val key = here?.let { (it.lat * 100).toInt() to (it.lon * 100).toInt() }
    LaunchedEffect(key) {
        val p = here ?: return@LaunchedEffect
        hereName = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val w = ir.mohitban.app.gasht.Whereabouts.locate(context, p)
                listOfNotNull(
                    w.place?.takeIf { (w.placeDistance ?: 1e9) < 15_000 }?.label,
                    w.county?.let { "شهرستان $it" },
                ).joinToString("، ").ifBlank { null }
            }.getOrNull()
        }
    }
    val point = here ?: home?.first
    val title = when {
        here != null -> hereName ?: "موقعیت فعلی شما"
        home != null -> home!!.second
        else -> null
    }
    ToolCard(Icons.Outlined.LightMode, Color(0xFFE0A21E), "طلوع و غروب آفتاب") {
        if (point == null) {
            Text("بعد از پیدا شدن موقعیت GPS محاسبه می‌شود.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
        } else {
            title?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, tint = Color(0xFFE5483B), modifier = Modifier.size(18.dp))
                    Text(" $it", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                }
            }
            val cal = java.util.Calendar.getInstance()
            val offset = java.util.TimeZone.getDefault().getOffset(cal.timeInMillis) / 60_000
            val s = SunTimes.compute(cal.get(java.util.Calendar.DAY_OF_YEAR), point.lat, point.lon, offset)
            fun hm(m: Int?) = m?.let { String.format(java.util.Locale.US, "%02d:%02d", (it / 60) % 24, it % 60).fa() } ?: "—"
            Row(Modifier.fillMaxWidth()) {
                Result("طلوع", hm(s.sunrise), Modifier.weight(1f))
                Result("ظهر شرعی", hm(s.noon), Modifier.weight(1f))
                Result("غروب", hm(s.sunset), Modifier.weight(1f))
            }
            Text(
                if (here != null) "${JalaliDate.today().formatLong().fa()} — برای موقعیت فعلی شما، بدون اینترنت (با تقریب حدود ۲ دقیقه)."
                else "${JalaliDate.today().formatLong().fa()} — برای مرکز شهرستان محل خدمت (ثبت‌نام). با روشن شدن GPS برای موقعیت دقیق شما حساب می‌شود.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- flashlight / SOS light

@Composable
private fun TorchCard() {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
    val camId = remember {
        runCatching {
            cm?.cameraIdList?.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }
    var mode by remember { mutableStateOf(0) } // 0 off, 1 on, 2 SOS
    fun torch(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 23 && camId != null) runCatching { cm?.setTorchMode(camId, on) }
    }
    LaunchedEffect(mode) {
        when (mode) {
            0 -> torch(false)
            1 -> torch(true)
            2 -> {
                // ... --- ... repeated
                val dot = 220L; val dash = 660L
                val pattern = listOf(dot, dot, dot, dash, dash, dash, dot, dot, dot)
                while (isActive) {
                    for (d in pattern) { torch(true); delay(d); torch(false); delay(dot) }
                    delay(1400)
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { torch(false) } }
    ToolCard(Icons.Outlined.FlashlightOn, Color(0xFF3D4A42), "چراغ‌قوه و علامت SOS") {
        if (Build.VERSION.SDK_INT < 23 || camId == null) {
            Text("فلاش دوربین در این گوشی قابل کنترل نیست.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("خاموش", mode == 0, Modifier.weight(1f)) { mode = 0 }
                ChoiceChip("روشن", mode == 1, Modifier.weight(1f)) { mode = 1 }
                ChoiceChip("SOS چشمک‌زن", mode == 2, Modifier.weight(1.3f)) { mode = 2 }
            }
            Text("علامت SOS نوری در شب از فاصله‌ی دور دیده می‌شود.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------------------------------------------------------- protected areas

@Composable
private fun ZonesCard(vm: GashtViewModel, here: GeoPoint?) {
    ToolCard(Icons.Outlined.Explore, Color(0xFF1B7F3B), "مناطق تحت مدیریت (${vm.zones.size.fa()})") {
        val rows = androidx.compose.runtime.remember(vm.zones, here?.let { (it.lat * 200).toInt() to (it.lon * 200).toInt() }) {
            vm.zones.map { z -> z to (here?.let { p -> if (z.contains(p)) 0.0 else z.roughDistance(p) }) }
                .sortedBy { it.second ?: Double.MAX_VALUE }
        }
        rows.forEach { (z, d) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .clickable { vm.showZone(z) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(ir.mohitban.app.gasht.ZoneTypes.color(z.type))),
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(z.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        z.type + (z.hectares?.let { " | ${String.format(java.util.Locale.US, "%,.0f", it).fa()} هکتار" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                    )
                }
                Text(
                    when { d == null -> ""; d == 0.0 -> "داخل منطقه"; else -> CoordFormat.meters(d).fa() },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (d == 0.0) Color(0xFF1B7F3B) else MColors.Muted,
                )
            }
        }
        Text("با زدن روی هر منطقه، مرز آن روی نقشه نشان داده می‌شود.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp))
    }
}
