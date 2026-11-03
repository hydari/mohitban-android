package ir.mohitban.app.ui.gasht

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.container
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.data.local.WaypointEntity
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.gasht.CoordFormat
import ir.mohitban.app.gasht.Fix
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.gasht.PlaceKind
import ir.mohitban.app.gasht.SosCodec
import ir.mohitban.app.gasht.SosKind
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal const val TOOLS_PREFS = "gasht_tools"

fun sosNumbers(context: Context): List<String> =
    context.getSharedPreferences(TOOLS_PREFS, Context.MODE_PRIVATE).getString("sos_numbers", "").orEmpty()
        .split(',', '،', ' ').map { it.trim() }.filter { it.length >= 5 }

/** The point [meters] away from [from] towards [bearing] (degrees from north). */
fun destination(from: GeoPoint, bearing: Double, meters: Double): GeoPoint {
    val r = 6_371_000.0
    val d = meters / r
    val b = Math.toRadians(bearing)
    val la1 = Math.toRadians(from.lat); val lo1 = Math.toRadians(from.lon)
    val la2 = asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
    val lo2 = lo1 + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
    return GeoPoint(Math.toDegrees(la2), Math.toDegrees(lo2))
}

private fun mapLink(p: GeoPoint) = "https://maps.google.com/?q=${String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon)}"

/** The readable text plus the Mohityar line that opens a colleague's app on the place. */
fun reportMessage(kind: SosKind, profile: RangerProfile?, fix: Fix, target: GeoPoint?, note: String): String {
    val name = profile?.fullName.orEmpty()
    val readable = if (target == null) {
        positionMessage(kind.smsTitle, name, fix)
    } else buildString {
        append(kind.smsTitle)
        if (name.isNotBlank()) append(" — $name")
        append("\n${kind.target}: ").append(CoordFormat.decimal(target))
        append("\nUTM: ").append(CoordFormat.utm(target))
        val d = Geo.distance(fix.point, target); val b = Geo.bearing(fix.point, target)
        append("\n${CoordFormat.meters(d).fa()} به سمت ${Geo.direction(b)} از محیط‌بان")
        append("\n").append(mapLink(target))
        append("\nموقعیت محیط‌بان: ").append(CoordFormat.decimal(fix.point))
    }
    return buildString {
        append(readable)
        if (note.isNotBlank()) append("\nتوضیح: ${note.trim()}")
        append("\n\nکد محیط‌یار:\n")
        append(SosCodec.line(kind, name, profile?.grade.orEmpty(), profile?.station.orEmpty(), fix, target, note))
    }
}

fun openSms(context: Context, numbers: List<String>, body: String) {
    runCatching {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";"))).putExtra("sms_body", body)
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Report a poacher or a fire (or ask for help) by SMS. The place can be the ranger's own,
 * a distance along the direction the phone points, or a point held on the map.
 */
@Composable
fun ReportDialog(
    initial: SosKind,
    fix: Fix?,
    heading: Float?,
    picked: GeoPoint?,
    onSavePlace: (WaypointEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initial) }
    // 0: my position, 1: distance + direction, 2: point picked on the map
    var mode by remember { mutableStateOf(if (picked != null) 2 else if (initial == SosKind.HELP) 0 else 1) }
    var dist by remember { mutableStateOf("") }
    var bearing by remember { mutableStateOf<Float?>(null) }
    var note by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf<RangerProfile?>(null) }
    LaunchedEffect(Unit) { profile = withContext(Dispatchers.IO) { context.container.profileRepository.getProfile() } }
    val numbers = remember { sosNumbers(context) }
    val color = Color(kind.color)

    val target: GeoPoint? = when (mode) {
        1 -> {
            val m = dist.toDoubleOrNull()
            val b = bearing ?: heading
            if (fix != null && m != null && m > 0 && b != null) destination(fix.point, b.toDouble(), m) else null
        }
        2 -> picked
        else -> null
    }
    val ready = fix != null && numbers.isNotEmpty() && (mode == 0 || target != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("پیامک ${kind.title}", color = color, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SosKind.entries.forEach { k ->
                        ChoiceChip(if (k == SosKind.HELP) "کمک" else k.title, k == kind, Modifier.weight(1f)) {
                            kind = k
                            if (k == SosKind.HELP && mode == 1) mode = 0
                        }
                    }
                }
                if (kind != SosKind.HELP) {
                    Text("${kind.target} کجاست؟", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("محل من", mode == 0, Modifier.weight(1f)) { mode = 0 }
                        ChoiceChip("فاصله و جهت", mode == 1, Modifier.weight(1.2f)) { mode = 1 }
                        if (picked != null) ChoiceChip("روی نقشه", mode == 2, Modifier.weight(1f)) { mode = 2 }
                    }
                    when (mode) {
                        1 -> {
                            MField(dist, { dist = it.filter(Char::isDigit) }, Modifier.padding(top = 8.dp), label = "فاصله تقریبی (متر)", placeholder = "۵۰۰", numeric = NumericMode.Digits, maxLength = 5)
                            val b = bearing ?: heading
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF2F6F8)).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Explore, null, tint = color, modifier = Modifier.size(22.dp))
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        b?.let { "جهت: ${it.toInt().fa()}° (${Geo.direction(it.toDouble())})" } ?: "قطب‌نمای گوشی در دسترس نیست",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink,
                                    )
                                    Text(
                                        if (bearing == null) "گوشی را صاف رو به محل بگیرید و «ثبت جهت» را بزنید." else "جهت ثبت شد.",
                                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                                    )
                                }
                                if (heading != null) {
                                    SmallAction(Icons.Outlined.Explore, if (bearing == null) "ثبت جهت" else "از نو", color = color) {
                                        bearing = if (bearing == null) heading else null
                                    }
                                }
                            }
                        }
                        2 -> Text(
                            picked?.let { "نقطه‌ی انتخاب‌شده روی نقشه: ${CoordFormat.decimal(it)}" }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall, color = MColors.Green, modifier = Modifier.padding(top = 6.dp),
                        )
                        else -> {}
                    }
                    if (picked == null) {
                        Text(
                            "برای انتخاب محل روی نقشه، در نقشه‌ی گشت‌یار انگشت را روی محل نگه دارید.",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (fix != null && target != null) {
                        val d = Geo.distance(fix.point, target)
                        Text(
                            "${kind.target}: ${CoordFormat.meters(d).fa()} به سمت ${Geo.direction(Geo.bearing(fix.point, target))} از شما",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                MField(
                    note, { note = it }, Modifier.padding(top = 8.dp),
                    label = "توضیح کوتاه (اختیاری)",
                    placeholder = when (kind) { SosKind.POACHER -> "۲ نفر، خودروی سفید، مسلح"; SosKind.FIRE -> "آتش در حال گسترش به سمت جنگل"; else -> "پایم آسیب دیده" },
                    maxLength = 80,
                )
                when {
                    numbers.isEmpty() -> Text("ابتدا شماره‌های گیرنده را در کارت پیامک اضطراری ذخیره کنید.", style = MaterialTheme.typography.labelSmall, color = MColors.Error, modifier = Modifier.padding(top = 6.dp))
                    fix == null -> Text("موقعیت GPS شما هنوز پیدا نشده است.", style = MaterialTheme.typography.labelSmall, color = MColors.Error, modifier = Modifier.padding(top = 6.dp))
                    else -> Text("گیرندگان: ${numbers.joinToString("، ").fa()}", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            ir.mohitban.app.ui.components.DialogActions("ارسال پیامک", onOk = {
                val f = fix ?: return@DialogActions
                val t = if (kind == SosKind.HELP) null else target
                openSms(context, numbers, reportMessage(kind, profile, f, t, note))
                // the reported place is kept on the ranger's own map as well
                if (kind != SosKind.HELP) {
                    val p = t ?: f.point
                    val c = java.util.Calendar.getInstance()
                    onSavePlace(
                        WaypointEntity(
                            name = "${kind.title} ${String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)).fa()}",
                            kind = if (kind == SosKind.FIRE) PlaceKind.FIRE.name else PlaceKind.VIOLATION.name,
                            lat = p.lat, lon = p.lon, note = note.trim(), createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
                onDismiss()
            }, cancel = "انصراف", onCancel = onDismiss, okEnabled = ready)
        },
    )
}
