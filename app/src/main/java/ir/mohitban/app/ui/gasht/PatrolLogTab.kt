package ir.mohitban.app.ui.gasht

import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.AppContainer
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.data.local.PatrolLogEntity
import ir.mohitban.app.gasht.Finding
import ir.mohitban.app.gasht.MyZones
import ir.mohitban.app.gasht.Sighting
import ir.mohitban.app.gasht.Sightings
import ir.mohitban.app.gasht.PatrolModes
import ir.mohitban.app.gasht.PatrolPeriod
import ir.mohitban.app.gasht.PatrolStats
import ir.mohitban.app.gasht.TrackSource
import ir.mohitban.app.gasht.ZoneStore
import ir.mohitban.app.gasht.clockText
import ir.mohitban.app.gasht.dominantZone
import ir.mohitban.app.gasht.isNight
import ir.mohitban.app.gasht.jalaliFromMillis
import ir.mohitban.app.gasht.minuteOfDay
import ir.mohitban.app.gasht.minutes
import ir.mohitban.app.gasht.minutesText
import ir.mohitban.app.ui.common.Share
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.ComboField
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.JalaliDatePickerDialog
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.components.OutlinePill
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.SuccessDialog
import ir.mohitban.app.ui.components.clay
import ir.mohitban.app.ui.components.clayInset
import ir.mohitban.app.ui.components.clayPress
import ir.mohitban.app.ui.components.rememberPress
import ir.mohitban.app.ui.components.rememberMotionOn
import ir.mohitban.app.ui.components.staggerIn
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val OUTSIDE_ZONES = "خارج از مناطق حفاظت‌شده"

class PatrolViewModel(private val c: AppContainer) : ViewModel() {
    private val dao = c.db.gashtDao()
    val logs: StateFlow<List<PatrolLogEntity>?> = dao.observePatrols().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(p: PatrolLogEntity, done: () -> Unit) = viewModelScope.launch {
        if (p.id == 0L) dao.insertPatrol(p.copy(createdAt = System.currentTimeMillis())) else dao.updatePatrol(p)
        done()
    }

    fun delete(id: Long) = viewModelScope.launch { dao.deletePatrol(id) }
}

private fun km(v: Double): String = String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0").fa()

/** دفتر گشت: every patrol written down, with monthly and yearly coverage of the protected areas. */
@Composable
fun PatrolLogTab(g: GashtViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pv = appViewModel(key = "patrol") { c, _ -> PatrolViewModel(c) }
    val logs by pv.logs.collectAsStateWithLifecycle()
    val tracks by g.tracks.collectAsStateWithLifecycle()
    val fix by g.fix.collectAsStateWithLifecycle()
    val today = remember { JalaliDate.today() }
    var period by remember { mutableStateOf(PatrolPeriod(today.year, today.month)) }
    val all = logs.orEmpty()
    // the ranger's own list of areas (plus any area already written in the logbook)
    var myZones by remember { mutableStateOf(MyZones.list(context)) }
    val known = remember(myZones, all) { (myZones + all.map { it.zone.trim() }).filter { it.isNotEmpty() }.distinct() }
    val stats = remember(all, period) { PatrolStats.of(all, period, emptyList()) }
    val species = remember(stats) { Sightings.totals(stats.logs) }
    var editing by remember { mutableStateOf<PatrolLogEntity?>(null) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun report(then: (java.io.File) -> Unit) {
        busy = true
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                val profile = context.container.profileRepository.getProfile()
                ir.mohitban.app.pdf.PatrolReportRenderer(context, context.container.files).createPdf(stats, profile)
            }
            busy = false
            then(f)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // ---- end of the month: time for the monthly report ----
        val left = Jalali.monthLength(today.year, today.month) - today.day
        if (left <= 3) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(ir.mohitban.app.ui.theme.Corners.R))
                    .background(MColors.AmberBg).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.EventNote, null, tint = MColors.Amber, modifier = Modifier.size(22.dp))
                Text(
                    "  پایان ${Jalali.MONTH_NAMES[today.month - 1]} نزدیک است؛ گزارش ماهانه‌ی گشت را از پایین همین صفحه بفرستید.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MColors.Amber,
                )
            }
        }

        // ---- add ----
        PillButton("ثبت گشت جدید", icon = Icons.Outlined.Add, onClick = {
            editing = PatrolLogEntity(
                jdn = today.jdn, startMin = minuteOfDay(System.currentTimeMillis()), zone = all.firstOrNull()?.zone.orEmpty(),
                mode = PatrolModes.ALL[1], createdAt = 0L,
            )
        })
        val lastRec = tracks.firstOrNull { it.source == TrackSource.RECORDED && it.pointCount > 1 }
        val alreadyLogged = lastRec != null && all.any { it.trackId == lastRec.id }
        if (lastRec != null && !alreadyLogged) {
            OutlinePill(
                "ثبت از آخرین مسیر ضبط‌شده (${lastRec.name})",
                {
                    scope.launch {
                        val pts = withContext(Dispatchers.IO) { context.container.gasht.points(lastRec.id) }
                        val start = pts.firstOrNull()?.time ?: lastRec.createdAt
                        val end = pts.lastOrNull()?.time ?: (lastRec.createdAt + lastRec.duration)
                        editing = PatrolLogEntity(
                            jdn = jalaliFromMillis(start).jdn, startMin = minuteOfDay(start), endMin = minuteOfDay(end),
                            zone = all.firstOrNull()?.zone.orEmpty(), mode = PatrolModes.ALL[0],
                            distanceKm = (lastRec.distance / 100.0).toInt() / 10.0, trackId = lastRec.id, createdAt = 0L,
                        )
                    }
                },
                Modifier.fillMaxWidth().padding(top = 8.dp), Icons.Outlined.Route,
            )
            Text(
                "تاریخ، ساعت و مسافت از مسیر ضبط‌شده پر می‌شود؛ منطقه و موارد مشاهده‌شده را وارد کنید.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        // ---- the ranger's areas ----
        SectionHead("مناطق من")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                var newZone by remember { mutableStateOf("") }
                if (myZones.isEmpty()) {
                    Text("مناطق و محدوده‌هایی را که گشت می‌زنید اینجا وارد کنید تا هنگام ثبت گشت فقط انتخاب شوند.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                }
                myZones.forEach { z ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MColors.Green2))
                        Text("  $z", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.weight(1f))
                        Box(Modifier.size(34.dp).clip(CircleShape).clickable { MyZones.remove(context, z); myZones = MyZones.list(context) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "حذف $z", tint = MColors.Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MField(newZone, { newZone = it }, Modifier.weight(1f), placeholder = "نام منطقه یا محدوده", maxLength = 60)
                    SmallAction(Icons.Outlined.Add, "افزودن", enabled = newZone.isNotBlank()) {
                        MyZones.add(context, newZone); myZones = MyZones.list(context); newZone = ""
                    }
                }
            }
        }

        // ---- period ----
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("گزارش ماهانه", period.month != null, Modifier.weight(1f)) { period = PatrolPeriod(period.year, if (period.year == today.year) today.month else 12) }
            ChoiceChip("گزارش سالانه", period.month == null, Modifier.weight(1f)) { period = PatrolPeriod(period.year, null) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp).clay(12.dp, depth = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { period = period.previous() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "قبلی", tint = MColors.Green)
            }
            Text(period.title.fa(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clickable { period = period.next() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "بعدی", tint = MColors.Green)
            }
        }

        // ---- summary ----
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("گشت", stats.count.fa(), Modifier.weight(1f))
            StatTile("مدت", minutesText(stats.minutes).fa(), Modifier.weight(1.5f))
            StatTile("کیلومتر", km(stats.km), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("روزهای گشت", "${stats.activeDays.fa()} از ${stats.period.days.fa()}", Modifier.weight(1.3f))
            StatTile("گشت شبانه", stats.nights.fa(), Modifier.weight(1f))
            StatTile("تخلف", stats.violations.fa(), Modifier.weight(1f), alert = stats.violations > 0)
        }

        // ---- coverage of protected areas ----
        SectionHead("پوشش گشت در مناطق")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                if (stats.zones.isEmpty()) {
                    Text("در ${period.title.fa()} گشتی ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
                } else {
                    val max = stats.zones.maxOf { it.count }
                    stats.zones.forEachIndexed { i, z ->
                        CoverageBar(z.zone, z.count, max, "${z.count.fa()} گشت | ${minutesText(z.minutes).fa()}${if (z.km > 0) " | ${km(z.km)} کیلومتر" else ""}", i, period)
                    }
                    if (stats.zones.size > 1) {
                        Text(
                            "بیشترین گشت: ${stats.zones.first().zone}\nکمترین گشت: ${stats.zones.last().zone}",
                            style = MaterialTheme.typography.labelMedium, color = MColors.Ink, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        if (species.isNotEmpty()) {
            SectionHead("حیات‌وحش مشاهده‌شده")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    species.forEach { (sp, n, times) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8A6A2E)))
                            Text("  $sp", style = MaterialTheme.typography.bodySmall, color = MColors.Ink, modifier = Modifier.weight(1f))
                            Text("${n.fa()} فرد | ${times.fa()} گشت", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF6E5322))
                        }
                    }
                    Text(
                        "جمع ${species.size.fa()} گونه و ${species.sumOf { it.second }.fa()} فرد در ${period.title.fa()}",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (stats.findings.isNotEmpty()) {
            SectionHead("موارد مشاهده‌شده")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    stats.findings.forEach { (f, n) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (f.problem) MColors.Error else MColors.Green2))
                            Text("  ${f.title}", style = MaterialTheme.typography.bodySmall, color = MColors.Ink, modifier = Modifier.weight(1f))
                            Text("${n.fa()} بار", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (f.problem) MColors.Error else MColors.Green)
                        }
                    }
                }
            }
        }

        // ---- report ----
        Spacer(Modifier.height(14.dp))
        PillButton(
            if (period.month != null) "ارسال گزارش ماهانه (PDF)" else "ارسال گزارش سالانه (PDF)", icon = Icons.Outlined.Share, loading = busy,
            enabled = stats.count > 0,
            onClick = { report { Share.file(context, it, "application/pdf") } },
        )
        if (stats.count > 0) {
            OutlinePill("چاپ گزارش", { report { Share.print(context, it, it.nameWithoutExtension) } }, Modifier.fillMaxWidth().padding(top = 8.dp), Icons.Outlined.Print)
        }

        // ---- the patrols of the period ----
        SectionHead("گشت‌های ${period.title.fa()} (${stats.count.fa()})")
        if (stats.logs.isEmpty()) {
            Text("هنوز گشتی برای این دوره ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(4.dp))
        }
        stats.logs.reversed().forEachIndexed { i, p ->
            PatrolCard(p, Modifier.padding(bottom = 10.dp).staggerIn(i, "patrols-${period}")) { editing = p }
        }
        Spacer(Modifier.height(40.dp))
    }

    editing?.let { draft ->
        PatrolEditor(
            draft, zones = known,
            onDismiss = { editing = null },
            onSave = { p -> MyZones.add(context, p.zone); myZones = MyZones.list(context); pv.save(p) { editing = null; saved = true } },
            onDelete = { pv.delete(draft.id); editing = null },
        )
    }
    if (saved) SuccessDialog("گشت در دفتر ثبت شد", onFinished = { saved = false })
}

@Composable
private fun SectionHead(text: String, color: Color = MColors.Green) {
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp))
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, alert: Boolean = false) {
    Column(modifier.height(64.dp).clay(12.dp, depth = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = if (alert) MColors.Error else MColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
    }
}

/** One protected area's share of the patrols, as a bar that grows in. */
@Composable
private fun CoverageBar(name: String, count: Int, max: Int, detail: String, index: Int, period: PatrolPeriod) {
    val motion = rememberMotionOn()
    val grow = remember(period, count, max) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(period, count, max) {
        if (motion) { delay(60L * index.coerceAtMost(10)); grow.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MColors.Line)) {
            Box(
                Modifier.fillMaxWidth((count.toFloat() / max * grow.value).coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF5CC184), MColors.Green2))),
            )
        }
    }
}

@Composable
private fun PatrolCard(p: PatrolLogEntity, modifier: Modifier, onOpen: () -> Unit) {
    val press = rememberPress()
    Column(
        modifier.fillMaxWidth().clayPress(press).clay().clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), onClick = onOpen).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(Icons.Outlined.EventNote, if (p.isNight()) Color(0xFF34406B) else MColors.Green2, Color.White, size = 36.dp, iconSize = 19.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(p.zone.ifBlank { "منطقه نامشخص" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${JalaliDate.fromJdn(p.jdn).formatLong().fa()} | ${clockText(p.startMin).fa()}${if (p.endMin != null) " تا ${clockText(p.endMin).fa()}" else ""}",
                    style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                )
            }
            Icon(Icons.Outlined.Edit, "ویرایش", tint = MColors.Muted, modifier = Modifier.size(18.dp))
        }
        val parts = listOfNotNull(
            p.mode,
            p.minutes()?.let { minutesText(it).fa() },
            p.distanceKm?.let { "${km(it)} کیلومتر" },
            p.spot.takeIf { it.isNotBlank() },
        )
        Text(parts.joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = MColors.Ink, modifier = Modifier.padding(top = 8.dp))
        val seen = Sightings.parse(p.wildlife).first
        if (seen.isNotEmpty()) {
            Text(
                "حیات‌وحش: " + seen.joinToString("، ") { "${it.species} ${it.count.fa()}" },
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF6E5322), modifier = Modifier.padding(top = 4.dp),
            )
        }
        val f = Finding.parse(p.findings)
        if (f.isNotEmpty() || p.violations > 0) {
            Text(
                (f.map { it.title } + listOfNotNull(if (p.violations > 0) "تخلف: ${p.violations.fa()}" else null)).joinToString("، "),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (f.any { it.problem } || p.violations > 0) MColors.Error else MColors.Green, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Full-screen form for one patrol. */
@Composable
private fun PatrolEditor(
    draft: PatrolLogEntity,
    zones: List<String>,
    onDismiss: () -> Unit,
    onSave: (PatrolLogEntity) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(JalaliDate.fromJdn(draft.jdn)) }
    var start by remember { mutableStateOf(draft.startMin) }
    var end by remember { mutableStateOf(draft.endMin) }
    var zone by remember { mutableStateOf(draft.zone) }
    var spot by remember { mutableStateOf(draft.spot) }
    var mode by remember { mutableStateOf(draft.mode) }
    var dist by remember { mutableStateOf(draft.distanceKm?.let { String.format(java.util.Locale.US, "%.1f", it).removeSuffix(".0") }.orEmpty()) }
    var findings by remember { mutableStateOf(Finding.parse(draft.findings)) }
    var violations by remember { mutableStateOf(if (draft.violations > 0) draft.violations.toString() else "") }
    val parsedWildlife = remember { Sightings.parse(draft.wildlife) }
    var sightings by remember { mutableStateOf(parsedWildlife.first.ifEmpty { listOf(Sighting("", 0)) }) }
    val wildlifeNote = parsedWildlife.second
    var companions by remember { mutableStateOf(draft.companions) }
    var notes by remember { mutableStateOf(draft.notes) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickTime(initial: Int?, set: (Int) -> Unit) {
        val m = initial ?: minuteOfDay(System.currentTimeMillis())
        TimePickerDialog(context, { _, h, min -> set(h * 60 + min) }, m / 60, m % 60, true).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MColors.Bg).statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "بستن", tint = MColors.Ink)
                }
                Text(if (draft.id == 0L) "ثبت گشت" else "ویرایش گشت", style = MaterialTheme.typography.titleLarge, color = MColors.Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                if (draft.id != 0L) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Delete, "حذف", tint = MColors.Error)
                    }
                } else Spacer(Modifier.width(44.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MField(date.formatLong().fa(), {}, label = "تاریخ گشت", leadingIcon = Icons.Outlined.CalendarMonth, onClick = { pickDate = true })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MField(clockText(start).fa(), {}, Modifier.weight(1f), label = "ساعت شروع", leadingIcon = Icons.Outlined.Schedule, onClick = { pickTime(start) { start = it } })
                    MField(clockText(end).fa(), {}, Modifier.weight(1f), label = "ساعت پایان", leadingIcon = Icons.Outlined.Schedule, onClick = { pickTime(end ?: start) { end = it } })
                }
                ComboField(zone, { zone = it; error = null }, zones, label = "منطقه‌ی گشت", placeholder = "از «مناطق من» انتخاب کنید یا بنویسید", error = error)
                MField(spot, { spot = it }, label = "محدوده / نام محل (اختیاری)", placeholder = "مثلاً دره‌ی …، چشمه‌ی …")
                Text("نوع گشت", style = MaterialTheme.typography.labelLarge, color = MColors.Ink)
                PatrolModes.ALL.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m -> ChoiceChip(m, mode == m, Modifier.weight(1f)) { mode = m } }
                    }
                }
                MField(dist, { dist = it }, label = "مسافت پیموده‌شده (کیلومتر)", numeric = NumericMode.Decimal, maxLength = 6)
                Text("موارد مشاهده‌شده و اقدامات", style = MaterialTheme.typography.labelLarge, color = MColors.Ink)
                Finding.entries.toList().chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { f ->
                            FindingChip(f, f in findings, Modifier.weight(1f)) {
                                findings = when {
                                    f in findings -> findings - f
                                    f == Finding.NONE -> setOf(Finding.NONE)
                                    else -> findings - Finding.NONE + f
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                MField(violations, { violations = it }, label = "تعداد تخلف / متخلف برخوردشده", numeric = NumericMode.Digits, maxLength = 3)
                Text("حیات‌وحش مشاهده‌شده", style = MaterialTheme.typography.labelLarge, color = MColors.Ink)
                sightings.forEachIndexed { i, sg ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ComboField(
                            sg.species, { v -> sightings = sightings.toMutableList().also { it[i] = sg.copy(species = v) } },
                            Sightings.COMMON, Modifier.weight(1f), placeholder = "گونه",
                        )
                        MField(
                            if (sg.count > 0) sg.count.toString() else "", { v -> sightings = sightings.toMutableList().also { it[i] = sg.copy(count = v.toIntOrNull() ?: 0) } },
                            Modifier.width(84.dp), placeholder = "تعداد", numeric = NumericMode.Digits, maxLength = 5,
                        )
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable {
                                sightings = (sightings.toMutableList().also { it.removeAt(i) }).ifEmpty { listOf(Sighting("", 0)) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Close, "حذف گونه", tint = MColors.Muted, modifier = Modifier.size(18.dp)) }
                    }
                }
                SmallAction(Icons.Outlined.Add, "افزودن گونه") { sightings = sightings + Sighting("", 0) }
                MField(companions, { companions = it }, label = "همراهان گشت", maxLength = 120)
                MField(notes, { notes = it }, label = "شرح و توضیحات", maxLength = 600)
                Spacer(Modifier.height(4.dp))
                PillButton("ذخیره در دفتر گشت", icon = Icons.Outlined.EventNote, onClick = {
                    if (zone.isBlank()) { error = "منطقه‌ی گشت را مشخص کنید"; return@PillButton }
                    onSave(
                        draft.copy(
                            jdn = date.jdn, startMin = start, endMin = end, zone = zone.trim(), spot = spot.trim(), mode = mode,
                            distanceKm = dist.replace('٫', '.').toDoubleOrNull(), findings = Finding.encode(findings),
                            violations = violations.toIntOrNull() ?: 0, wildlife = Sightings.encode(sightings, wildlifeNote), companions = companions.trim(), notes = notes.trim(),
                        ),
                    )
                })
                Spacer(Modifier.height(40.dp))
            }
        }
    }
    if (pickDate) {
        JalaliDatePickerDialog(initial = date, title = "تاریخ گشت", onDismiss = { pickDate = false }, onConfirm = { date = it; pickDate = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف گشت", text = "این گشت از دفتر گشت حذف شود؟", confirm = "حذف", danger = true,
            onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FindingChip(f: Finding, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (f.problem) MColors.Error else MColors.Green
    Box(
        modifier
            .height(46.dp)
            .then(
                if (selected) Modifier.clayInset(10.dp, if (f.problem) Color(0xFFFBE4E1) else Color(0xFFDCEEE2)).border(1.5.dp, tint, RoundedCornerShape(10.dp))
                else Modifier.clay(10.dp, depth = 3.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            f.title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) tint else MColors.Ink, textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}
