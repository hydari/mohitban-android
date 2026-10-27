package ir.mohitban.app.ui.gasht

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
        CompassCard(trueHeading(compass, fix), compass != null)
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

@Composable
private fun CompassCard(heading: Float?, available: Boolean) {
    ToolCard(Icons.Outlined.Explore, Color(0xFF2F7FC1), "قطب‌نما") {
        if (!available || heading == null) {
            Text("حسگر قطب‌نما در این گوشی در دسترس نیست.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CompassRose(heading, Modifier.size(230.dp))
                Text(
                    "${heading.toInt().fa()}° ${Geo.direction(heading.toDouble())}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "جهت شمال جغرافیایی (اصلاح‌شده با انحراف مغناطیسی). گوشی را صاف نگه دارید و دور از فلز و خودرو. اگر خطا دارد، گوشی را چند بار به شکل ۸ بچرخانید.",
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
    ToolCard(Icons.Outlined.LightMode, Color(0xFFE0A21E), "طلوع و غروب آفتاب") {
        if (here == null) {
            Text("بعد از پیدا شدن موقعیت GPS محاسبه می‌شود.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
        } else {
            val cal = java.util.Calendar.getInstance()
            val offset = java.util.TimeZone.getDefault().getOffset(cal.timeInMillis) / 60_000
            val s = SunTimes.compute(cal.get(java.util.Calendar.DAY_OF_YEAR), here.lat, here.lon, offset)
            fun hm(m: Int?) = m?.let { String.format(java.util.Locale.US, "%02d:%02d", (it / 60) % 24, it % 60).fa() } ?: "—"
            Row(Modifier.fillMaxWidth()) {
                Result("طلوع", hm(s.sunrise), Modifier.weight(1f))
                Result("ظهر شرعی", hm(s.noon), Modifier.weight(1f))
                Result("غروب", hm(s.sunset), Modifier.weight(1f))
            }
            Text(
                "${JalaliDate.today().formatLong().fa()} — برای همین نقطه، بدون اینترنت (با تقریب حدود ۲ دقیقه).",
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
