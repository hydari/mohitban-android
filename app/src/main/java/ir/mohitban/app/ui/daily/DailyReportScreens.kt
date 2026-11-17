package ir.mohitban.app.ui.daily

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.daily.DailyReport
import ir.mohitban.app.daily.DailyReportStore
import ir.mohitban.app.daily.Transport
import ir.mohitban.app.daily.WildlifeCount
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.model.VehicleType
import ir.mohitban.app.ui.common.Share
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.OutlinePill
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.PlateView
import ir.mohitban.app.ui.components.SectionTitle
import ir.mohitban.app.ui.components.SignatureField
import ir.mohitban.app.ui.components.SuccessDialog
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.components.rememberSignatureFieldState
import ir.mohitban.app.ui.components.safeBottomPadding
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TEXT_MAX = 2000

// ====================================================================== list

/** All daily reports, newest first; a new one, or open one to see / send it. */
@Composable
fun DailyReportsScreen(onBack: () -> Unit, onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val ver by DailyReportStore.version.collectAsState()
    var list by remember { mutableStateOf<List<DailyReport>?>(null) }
    LaunchedEffect(ver) { list = withContext(Dispatchers.IO) { DailyReportStore.all(context) } }
    var askDelete by remember { mutableStateOf<DailyReport?>(null) }

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("گزارش روزانه گشت", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            PillButton("ثبت گزارش امروز", onNew, Modifier.padding(top = 4.dp), icon = Icons.Outlined.Add)
            val items = list
            when {
                items == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MColors.Green) }
                items.isEmpty() -> Text(
                    "هنوز گزارشی ثبت نشده است. پس از هر گشت، گزارش روزانه را ثبت، امضا و برای اداره ارسال کنید.",
                    style = MaterialTheme.typography.bodyMedium, color = MColors.Muted, modifier = Modifier.padding(top = 20.dp),
                )
                else -> {
                    SectionTitle("گزارش‌های ثبت‌شده (${items.size.fa()})")
                    items.forEach { r -> ReportRow(r, onClick = { onOpen(r.id) }, onDelete = { askDelete = r }) }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    askDelete?.let { r ->
        ConfirmDialog(
            title = "حذف گزارش", text = "گزارش ${JalaliDate.fromJdn(r.jdn).formatLong().fa()} حذف شود؟", confirm = "حذف", danger = true,
            onDismiss = { askDelete = null },
            onConfirm = {
                askDelete = null
                DailyReportStore.delete(context, r.id)?.let { gone -> context.container.files.delete(gone.signaturePath) }
            },
        )
    }
}

@Composable
private fun ReportRow(r: DailyReport, onClick: () -> Unit, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(
                when { r.transport == Transport.FOOT -> Icons.AutoMirrored.Outlined.DirectionsWalk; r.vehicleType == VehicleType.MOTORCYCLE.title -> Icons.Outlined.TwoWheeler; else -> Icons.Outlined.DirectionsCar },
                MColors.GreenLight, MColors.Green, size = 40.dp,
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(JalaliDate.fromJdn(r.jdn).formatLong().fa(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                Text(r.areas.lineSequence().firstOrNull().orEmpty().take(60), style = MaterialTheme.typography.labelSmall, color = MColors.Muted, maxLines = 1)
                val tags = listOfNotNull(
                    if (r.wildlife.isNotEmpty()) "${r.wildlife.size.fa()} گونه" else null,
                    if (r.destruction) "تخریب" else null,
                    if (r.poaching) "شکار" else null,
                ).joinToString(" | ")
                if (tags.isNotBlank()) Text(tags, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (r.hasViolation) MColors.Error else MColors.Green)
            }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DeleteOutline, "حذف", tint = MColors.Muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ====================================================================== form

/**
 * The form: date (automatic), the ranger (from the profile), on foot or which registered vehicle, monitored areas,
 * wildlife with counts, destruction / poaching, description, the day's events and the signature.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyReportEditScreen(
    id: Long,
    newVehicleId: Long?,
    onVehicleConsumed: () -> Unit,
    onAddVehicle: () -> Unit,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf<DailyReport?>(null) }
    var profile by remember { mutableStateOf<RangerProfile?>(null) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    var transport by remember { mutableStateOf(Transport.VEHICLE) }
    var vehicleId by remember { mutableStateOf<Long?>(null) }
    var areas by remember { mutableStateOf("") }
    val wildlife = remember { mutableStateListOf<WildlifeCount>() }
    var destruction by remember { mutableStateOf(false) }
    var poaching by remember { mutableStateOf(false) }
    var violationText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var events by remember { mutableStateOf("") }
    val sig = rememberSignatureFieldState()
    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(id) {
        val c = context.container
        val (p, vs, r) = withContext(Dispatchers.IO) {
            Triple(c.profileRepository.getProfile(), c.profileRepository.getVehicles(), if (id != 0L) DailyReportStore.get(context, id) else null)
        }
        profile = p; vehicles = vs; old = r
        if (r != null) {
            transport = r.transport; vehicleId = r.vehicleId; areas = r.areas
            wildlife.clear(); wildlife.addAll(r.wildlife)
            destruction = r.destruction; poaching = r.poaching; violationText = r.violationText
            description = r.description; events = r.events
            withContext(Dispatchers.IO) { c.files.loadBitmap(r.signaturePath, 900) }?.let { sig.existing = it.asImageBitmap() }
        } else {
            val def = withContext(Dispatchers.IO) { c.settingsRepository.getDefaultVehicleId() }
            vehicleId = vs.firstOrNull { it.id == def }?.id ?: vs.firstOrNull()?.id
            if (vs.isEmpty()) transport = Transport.FOOT
            if (areas.isBlank()) areas = listOfNotNull(
                p?.protectedArea?.let { ir.mohitban.app.domain.model.AreaTypes.title(it) }?.takeIf { it.isNotBlank() },
            ).joinToString("")
        }
        // species sent from the recognition screen wait here until the form opens
        withContext(Dispatchers.IO) { ir.mohitban.app.ai.PendingWildlife.take(context) }.forEach { name ->
            val i = wildlife.indexOfFirst { it.species == name }
            if (i >= 0) wildlife[i] = wildlife[i].copy(count = wildlife[i].count + 1) else wildlife += WildlifeCount(name, 1)
        }
        loaded = true
    }
    // a vehicle just registered from this form is picked at once
    LaunchedEffect(newVehicleId) {
        val nv = newVehicleId ?: return@LaunchedEffect
        vehicles = withContext(Dispatchers.IO) { context.container.profileRepository.getVehicles() }
        vehicleId = nv; transport = Transport.VEHICLE
        onVehicleConsumed()
    }

    val date = old?.let { JalaliDate.fromJdn(it.jdn) } ?: JalaliDate.today()
    val vehicle = vehicles.firstOrNull { it.id == vehicleId }
    val errVehicle = transport == Transport.VEHICLE && vehicle == null
    val errAreas = areas.isBlank()
    val errSig = sig.isEmpty
    val errViolation = (destruction || poaching) && violationText.isBlank()

    fun save() {
        showErrors = true
        val p = profile
        if (p == null) { Toast.makeText(context, "ابتدا اطلاعات فردی را در تنظیمات ثبت کنید", Toast.LENGTH_LONG).show(); return }
        if (errVehicle || errAreas || errSig || errViolation) {
            Toast.makeText(context, "موارد قرمز را کامل کنید", Toast.LENGTH_SHORT).show(); return
        }
        saving = true
        scope.launch {
            val c = context.container
            val newId = withContext(Dispatchers.IO) { runCatching {
                val path = sig.bitmap?.let { b ->
                    c.files.savePng(b, "daily_sig").also { old?.signaturePath?.let { o -> c.files.delete(o) } }
                } ?: old?.signaturePath
                val v = if (transport == Transport.VEHICLE) vehicle else null
                DailyReportStore.save(
                    context,
                    DailyReport(
                        id = old?.id ?: 0L, jdn = date.jdn,
                        name = p.fullName, grade = p.grade, rank = p.rank, nationalId = p.nationalId,
                        station = p.station, area = p.protectedArea, city = p.city, province = p.province,
                        transport = transport, vehicleId = v?.id, vehicleType = v?.type?.title.orEmpty(), vehicleTitle = v?.title.orEmpty(),
                        plate = v?.plate?.encode().orEmpty(),
                        areas = areas.trim(), wildlife = wildlife.filter { it.species.isNotBlank() && it.count > 0 },
                        destruction = destruction, poaching = poaching, violationText = if (destruction || poaching) violationText.trim() else "",
                        description = description.trim(), events = events.trim(), signaturePath = path,
                        createdAt = old?.createdAt ?: System.currentTimeMillis(), editedAt = if (old != null) System.currentTimeMillis() else null,
                    ),
                )
            }.getOrNull() }
            saving = false
            if (newId != null) saved = newId
            else Toast.makeText(context, "ذخیره‌ی گزارش انجام نشد (حافظه‌ی گوشی را بررسی کنید)", Toast.LENGTH_LONG).show()
        }
    }

    Box(Modifier.fillMaxSize().background(MColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(if (old == null) "گزارش روزانه گشت" else "ویرایش گزارش روزانه", onBack = onBack)
            if (!loaded) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MColors.Green) }
                return@Column
            }
            Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // ---------------- date and ranger (automatic)
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            val p = profile
                            Text(p?.fullName ?: "اطلاعات فردی ثبت نشده", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                            if (p != null) {
                                Text(listOf(p.grade, p.rank).filter { it.isNotBlank() }.distinct().joinToString(" | "), style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                                Text("پاسگاه ${p.station}", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(date.format().fa(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
                            Text(ir.mohitban.app.core.jalali.Jalali.WEEKDAY_NAMES[date.dayOfWeek], style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                        }
                    }
                }

                // ---------------- transport
                SectionTitle("نحوه‌ی گشت")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("با وسیله‌ی نقلیه", Icons.Outlined.DirectionsCar, transport == Transport.VEHICLE, Modifier.weight(1f)) { transport = Transport.VEHICLE }
                    Choice("پیاده", Icons.AutoMirrored.Outlined.DirectionsWalk, transport == Transport.FOOT, Modifier.weight(1f)) { transport = Transport.FOOT }
                }
                if (transport == Transport.VEHICLE) {
                    if (vehicles.isEmpty()) {
                        Text(
                            "هنوز خودرو یا موتورسیکلتی ثبت نکرده‌اید. حداقل یک وسیله ثبت کنید تا پلاک آن در گزارش بیاید.",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Error, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    vehicles.forEach { v ->
                        val on = v.id == vehicleId
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) Color(0xFFE3F1E8) else Color.White)
                                .border(if (on) 1.5.dp else 1.dp, if (on) MColors.Green else MColors.Line, RoundedCornerShape(12.dp))
                                .clickable { vehicleId = v.id }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (v.type == VehicleType.MOTORCYCLE) Icons.Outlined.TwoWheeler else Icons.Outlined.DirectionsCar, null, tint = if (on) MColors.Green else MColors.Muted)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(v.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                                Text(v.type.title, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                            }
                            PlateView(v.plate, height = if (v.type == VehicleType.CAR) 30.dp else 26.dp)
                        }
                    }
                    OutlinePill("ثبت وسیله‌ی جدید", onAddVehicle, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Icons.Outlined.Add)
                    if (showErrors && errVehicle) Err("وسیله‌ی نقلیه را انتخاب یا ثبت کنید، یا «پیاده» را بزنید.")
                }

                // ---------------- areas
                SectionTitle("مناطق مورد پایش")
                Area(areas, { areas = it }, "نام مناطق، محدوده‌ها و مسیرهایی که پایش شد", error = showErrors && errAreas)

                // ---------------- wildlife
                SectionTitle("گونه‌های حیات وحش مشاهده‌شده")
                WildlifeEditor(wildlife)

                // ---------------- violations
                SectionTitle("تخلفات (تخریب / شکار)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("تخریب", null, destruction, Modifier.weight(1f), danger = true) { destruction = !destruction }
                    Choice("شکار و صید", null, poaching, Modifier.weight(1f), danger = true) { poaching = !poaching }
                    Choice("تخلفی نبود", null, !destruction && !poaching, Modifier.weight(1f)) { destruction = false; poaching = false }
                }
                if (destruction || poaching) {
                    Area(violationText, { violationText = it }, "شرح تخلف: محل، نوع، تعداد متخلفان، اقدام انجام‌شده…", Modifier.padding(top = 8.dp), error = showErrors && errViolation)
                }

                // ---------------- description and events
                SectionTitle("شرح و توضیحات گشت")
                Area(description, { description = it }, "شرح گشت، مسیر، وضعیت منطقه و مشاهدات")
                SectionTitle("اتفاقات روز جاری")
                Area(events, { events = it }, "رویدادها و اتفاقات امروز (اگر بود)")

                // ---------------- signature
                SectionTitle("امضای محیط‌بان")
                SignatureField(sig, "امضای محیط‌بان", required = true, error = if (showErrors && errSig) "امضا لازم است" else null)

                PillButton("ذخیره و نمایش فرم", { save() }, Modifier.padding(top = 18.dp), icon = Icons.Outlined.Check, enabled = !saving, loading = saving)
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.safeBottomPadding())
            }
        }
    }
    saved?.let { sid -> SuccessDialog("گزارش روزانه ذخیره شد", onFinished = { saved = null; onSaved(sid) }) }
}

@Composable
private fun Err(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = MColors.Error, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun Choice(text: String, icon: ImageVector?, selected: Boolean, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MColors.Error else MColors.Green
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) tint.copy(alpha = 0.12f) else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) tint else MColors.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) tint else MColors.Muted, modifier = Modifier.size(18.dp).padding(end = 2.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), color = if (selected) tint else MColors.Ink, maxLines = 1)
    }
}

@Composable
private fun Area(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it.take(TEXT_MAX)) },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 10,
            placeholder = { Text(placeholder, color = MColors.Hint, style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodyMedium, isError = error, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MColors.Green, unfocusedBorderColor = MColors.Line,
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            ),
        )
        if (error) Err("این بخش را کامل کنید")
    }
}

/** Species with a count each: pick from the common list or type another name; − / + change the count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WildlifeEditor(list: MutableList<WildlifeCount>) {
    var typed by remember { mutableStateOf("") }
    fun add(name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        val i = list.indexOfFirst { it.species == n }
        if (i >= 0) list[i] = list[i].copy(count = list[i].count + 1) else list += WildlifeCount(n, 1)
        typed = ""
    }
    list.forEachIndexed { i, w ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(1.dp, MColors.Line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${(i + 1).fa()}. ${w.species}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.weight(1f))
            RoundBtn(Icons.Outlined.Remove, "کم کردن") { if (w.count > 1) list[i] = w.copy(count = w.count - 1) }
            Text(w.count.fa(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = MColors.Green, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            RoundBtn(Icons.Outlined.Add, "زیاد کردن") { if (w.count < 9999) list[i] = w.copy(count = w.count + 1) }
            Spacer(Modifier.width(6.dp))
            RoundBtn(Icons.Outlined.Close, "حذف", tint = MColors.Error) { list.removeAt(i) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = typed, onValueChange = { typed = it.take(40) }, singleLine = true, modifier = Modifier.weight(1f),
            placeholder = { Text("نام گونه (یا از فهرست زیر)", color = MColors.Hint, style = MaterialTheme.typography.bodySmall) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MColors.Green, unfocusedBorderColor = MColors.Line, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (typed.isBlank()) MColors.Line else MColors.Green)
                .clickable(enabled = typed.isNotBlank()) { add(typed) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Add, "افزودن", tint = Color.White) }
    }
    val q = typed.trim()
    val suggestions = DailyReportStore.COMMON_SPECIES.filter { s -> list.none { it.species == s } && (q.isEmpty() || s.contains(q)) }.take(if (q.isEmpty()) 16 else 30)
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        suggestions.forEach { s ->
            Text(
                "+ $s", style = MaterialTheme.typography.labelMedium, color = MColors.Green,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE8F3EC)).clickable { add(s) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, label: String, tint: Color = MColors.Green, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp)) }
}

// ====================================================================== view / send

/** The finished official form: its pages, and send as PDF or picture, print or edit. */
@Composable
fun DailyReportViewScreen(id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver by DailyReportStore.version.collectAsState()
    var pdf by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<DailyReport?>(null) }
    LaunchedEffect(id, ver) {
        val c = context.container
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val r = DailyReportStore.get(context, id) ?: error("not found")
                val f = c.dailyReport.createPdf(r)
                Triple(r, f, c.quarterReport.renderPages(f, 1240))
            }
        }
        result.onSuccess { (r, f, p) -> report = r; pdf = f; pages = p }.onFailure { failed = true }
    }

    fun shareImage() {
        val ps = pages
        if (ps.isEmpty()) return
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    // all pages one under the other in a single picture (fine for messengers)
                    val shown = ps.take(3)
                    val w = shown.maxOf { it.width }
                    val out = Bitmap.createBitmap(w, shown.sumOf { it.height }, Bitmap.Config.ARGB_8888)
                    val cv = android.graphics.Canvas(out)
                    cv.drawColor(android.graphics.Color.WHITE)
                    var y = 0f
                    shown.forEach { cv.drawBitmap(it, 0f, y, null); y += it.height }
                    val r = report
                    val name = "daily-report-${r?.let { JalaliDate.fromJdn(it.jdn).format().replace('/', '-') } ?: "form"}.png"
                    File(context.container.files.cacheDir, name).also { file -> FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }; out.recycle() }
                }.getOrNull()
            }
            if (f != null) Share.file(context, f, "image/png") else Toast.makeText(context, "ساخت تصویر ناموفق بود", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("فرم گزارش روزانه", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            when {
                failed -> Text("این گزارش پیدا نشد یا فرم ساخته نشد.", style = MaterialTheme.typography.bodyMedium, color = MColors.Error, modifier = Modifier.padding(24.dp))
                pages.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MColors.Green) }
                else -> pages.forEach { b ->
                    Image(
                        b.asImageBitmap(), "صفحه‌ی فرم", contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, MColors.Line, RoundedCornerShape(6.dp)),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (pdf != null) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp).safeBottomPadding(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("ارسال PDF", { pdf?.let { Share.file(context, it, "application/pdf") } }, Modifier.weight(1f), icon = Icons.Outlined.PictureAsPdf)
                    PillButton("ارسال تصویر", { shareImage() }, Modifier.weight(1f), icon = Icons.Outlined.Image)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill("چاپ", { pdf?.let { Share.print(context, it, "گزارش روزانه گشت") } }, Modifier.weight(1f), icon = Icons.Outlined.Print)
                    OutlinePill("ویرایش", { onEdit(id) }, Modifier.weight(1f), icon = Icons.Outlined.Edit)
                }
            }
        }
    }
}
