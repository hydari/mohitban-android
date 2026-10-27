package ir.mohitban.app.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.domain.model.ShiftPattern
import ir.mohitban.app.domain.model.ShiftStatus
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.JalaliDatePickerDialog
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.components.OutlinePill
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.SectionTitle
import ir.mohitban.app.ui.components.SuccessDialog
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.theme.Corners
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ir.mohitban.app.ui.components.clay
import ir.mohitban.app.ui.components.clayInset

/** Colours shared by the calendar, the dashboard card and the photo rim. */
object ShiftColors {
    val On = Color(0xFF239B56)
    val OnBg = Color(0xFFE3F4E8)
    val Off = Color(0xFF8E9893)
    val OffBg = Color(0xFFF0F2F1)
}

/** Short human text for a status, used here and on the dashboard. */
object ShiftText {
    fun title(s: ShiftStatus): String = if (s is ShiftStatus.OnDuty) "امروز شیفت هستید" else "امروز استراحت هستید"

    fun detail(s: ShiftStatus): String = when (s) {
        is ShiftStatus.OnDuty ->
            if (s.daysLeft == 0) "روز ${s.dayNumber.fa()} از ${s.length.fa()} | آخرین روز شیفت"
            else "روز ${s.dayNumber.fa()} از ${s.length.fa()} | ${s.daysLeft.fa()} روز تا پایان شیفت"
        is ShiftStatus.Resting -> when (s.daysToNext) {
            null -> "شیفتی در پیش رو ثبت نشده است"
            1 -> "فردا شیفت هستید"
            else -> "${s.daysToNext.fa()} روز تا شیفت بعدی"
        }
    }

    fun pattern(work: Int, rest: Int) = "${work.fa()} روز کار، ${rest.fa()} روز استراحت"
}

class ShiftViewModel(private val settings: SettingsRepository) : ViewModel() {
    val today: JalaliDate = JalaliDate.today()
    val shift = settings.shift.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var month by mutableStateOf(JalaliDate(today.year, today.month, 1))
    var loaded by mutableStateOf(false)

    // editor
    var presetIndex by mutableStateOf(1) // -1 = custom
    var customWork by mutableStateOf("4")
    var customRest by mutableStateOf("4")
    var start by mutableStateOf(today)
    var error by mutableStateOf<String?>(null)
    var saved by mutableStateOf(false)

    init {
        viewModelScope.launch {
            settings.shift.first()?.let { p ->
                val i = ShiftPattern.PRESETS.indexOf(p.work to p.rest)
                presetIndex = i
                if (i < 0) { customWork = p.work.toString(); customRest = p.rest.toString() }
                // show the start of the cycle that contains today, so the date reads naturally
                val back = ir.mohitban.app.core.jalali.fmod(today.jdn - p.startJdn, p.cycle)
                start = JalaliDate.fromJdn(today.jdn - back)
            }
            loaded = true
        }
    }

    fun draftWorkRest(): Pair<Int, Int>? =
        if (presetIndex >= 0) ShiftPattern.PRESETS[presetIndex]
        else {
            val w = customWork.toIntOrNull()
            val r = customRest.toIntOrNull()
            if (w == null || r == null || w !in 1..30 || r !in 1..30) null else w to r
        }

    fun save() {
        val wr = draftWorkRest()
        if (wr == null) { error = "تعداد روز کار باید ۱ تا ۳۰ و روز استراحت ۱ تا ۳۰ باشد"; return }
        error = null
        viewModelScope.launch {
            // a new pattern starts clean; manual day changes belong to the old one
            settings.saveShift(ShiftPattern(wr.first, wr.second, start.jdn))
            saved = true
        }
    }

    fun toggle(jdn: Int) {
        val p = shift.value ?: return
        viewModelScope.launch { settings.saveShift(p.toggle(jdn)) }
    }

    fun clearOverrides() {
        val p = shift.value ?: return
        viewModelScope.launch { settings.saveShift(p.copy(overrides = emptyMap())) }
    }

    fun clearAll() {
        viewModelScope.launch { settings.saveShift(null) }
    }
}

@Composable
fun ShiftScreen(onBack: () -> Unit) {
    val vm = appViewModel(key = "shift") { c, _ -> ShiftViewModel(c.settingsRepository) }
    val shift by vm.shift.collectAsStateWithLifecycle()
    var pickStart by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("شیفت کاری", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            StatusCard(shift, vm.today)
            Spacer(Modifier.height(14.dp))
            CalendarCard(
                shift = shift, today = vm.today, month = vm.month,
                onMonth = { vm.month = it }, onDay = { vm.toggle(it) },
            )
            if (shift?.overrides?.isNotEmpty() == true) {
                OutlinePill(
                    "حذف تغییرات دستی و بازگشت به الگو", { vm.clearOverrides() },
                    Modifier.fillMaxWidth().padding(top = 10.dp), Icons.Outlined.Restore,
                )
            }

            SectionTitle(if (shift == null) "ثبت شیفت" else "ویرایش شیفت", Modifier.padding(top = 18.dp, bottom = 8.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("الگوی شیفت", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "روزهای کار و استراحت به ترتیب تکرار می‌شوند.",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    val options = ShiftPattern.PRESETS.map { (w, r) -> "${w.fa()} کار / ${r.fa()} استراحت" } + "الگوی دلخواه"
                    options.chunked(2).forEachIndexed { row, pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEachIndexed { k, label ->
                                val index = row * 2 + k
                                val value = if (index == ShiftPattern.PRESETS.size) -1 else index
                                PatternChip(label, vm.presetIndex == value, Modifier.weight(1f)) { vm.presetIndex = value }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (vm.presetIndex == -1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            MField(
                                vm.customWork, { vm.customWork = it }, Modifier.weight(1f), label = "روز کار",
                                numeric = NumericMode.Digits, maxLength = 2, leadingIcon = Icons.Outlined.WorkHistory,
                            )
                            MField(
                                vm.customRest, { vm.customRest = it }, Modifier.weight(1f), label = "روز استراحت",
                                numeric = NumericMode.Digits, maxLength = 2, leadingIcon = Icons.Outlined.Bedtime,
                            )
                        }
                    }
                    MField(
                        vm.start.formatLong().fa(), {}, Modifier.padding(top = 4.dp),
                        label = "اولین روز شیفت (شروع دوره)", leadingIcon = Icons.Outlined.CalendarMonth,
                        onClick = { pickStart = true },
                    )
                    vm.draftWorkRest()?.let { (w, r) ->
                        Text(
                            "از ${vm.start.formatLong(false).fa()}: ${ShiftText.pattern(w, r)}، به همین ترتیب تکرار",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Green, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    vm.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MColors.Error, modifier = Modifier.padding(top = 6.dp)) }
                    PillButton(
                        "ذخیره الگوی شیفت", { vm.save() }, Modifier.padding(top = 14.dp), icon = Icons.Outlined.Check,
                    )
                    if (shift != null) {
                        OutlinePill(
                            "حذف شیفت", { vm.clearAll() }, Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = MColors.Error,
                        )
                    }
                }
            }
            Text(
                "برای تغییر یک روز خاص (مثلاً جابه‌جایی شیفت)، روی همان روز در تقویم بزنید.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(8.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (pickStart) {
        JalaliDatePickerDialog(
            initial = vm.start, maxDate = null, title = "اولین روز شیفت",
            onDismiss = { pickStart = false },
            onConfirm = { vm.start = it; pickStart = false },
        )
    }
    if (vm.saved) SuccessDialog("الگوی شیفت ذخیره شد", onFinished = { vm.saved = false })
}

@Composable
private fun StatusCard(shift: ShiftPattern?, today: JalaliDate) {
    AppCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (shift == null) {
                CircleIcon(Icons.Outlined.EventRepeat, MColors.Green, Color.White, size = 50.dp, iconSize = 25.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("شیفت کاری ثبت نشده", style = MaterialTheme.typography.titleMedium)
                    Text("الگوی شیفت را از پایین همین صفحه انتخاب کنید.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                }
            } else {
                val st = shift.status(today.jdn)
                val on = st is ShiftStatus.OnDuty
                CircleIcon(
                    if (on) Icons.Outlined.Shield else Icons.Outlined.Bedtime,
                    if (on) ShiftColors.On else ShiftColors.Off, Color.White, size = 50.dp, iconSize = 25.dp,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        ShiftText.title(st), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (on) ShiftColors.On else MColors.Ink,
                    )
                    Text(ShiftText.detail(st), style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        "الگو: ${ShiftText.pattern(shift.work, shift.rest)}",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Hint, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DayBadge(on, big = true)
            }
        }
    }
}

@Composable
private fun DayBadge(on: Boolean, big: Boolean = false) {
    val s = if (big) 40.dp else 18.dp
    Box(
        Modifier.size(s).clip(RoundedCornerShape(if (big) 10.dp else 5.dp))
            .background(if (on) ShiftColors.On else ShiftColors.OffBg)
            .border(1.dp, if (on) ShiftColors.On else Color(0xFFD5DAD7), RoundedCornerShape(if (big) 10.dp else 5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (on) "ش" else "ا",
            color = if (on) Color.White else ShiftColors.Off,
            fontWeight = FontWeight.Bold, fontSize = if (big) 20.sp else 10.sp, lineHeight = if (big) 22.sp else 11.sp,
        )
    }
}

@Composable
private fun CalendarCard(
    shift: ShiftPattern?,
    today: JalaliDate,
    month: JalaliDate,
    onMonth: (JalaliDate) -> Unit,
    onDay: (Int) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            // header: previous month on the right, next on the left (RTL), as in the date picker
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "ماه قبل") { onMonth(month.plusMonths(-1)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${Jalali.MONTH_NAMES[month.month - 1]} ${month.year.fa()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Ink,
                    )
                    if (month.year != today.year || month.month != today.month) {
                        Text(
                            "بازگشت به امروز", style = MaterialTheme.typography.labelSmall, color = MColors.Green,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onMonth(JalaliDate(today.year, today.month, 1)) }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                NavArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "ماه بعد") { onMonth(month.plusMonths(1)) }
            }
            Spacer(Modifier.height(10.dp))
            // weekday header, Saturday first (on the right in RTL)
            Row(Modifier.fillMaxWidth()) {
                Jalali.WEEKDAY_SHORT.forEachIndexed { i, d ->
                    Text(
                        d, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (i == 6) MColors.Error else MColors.Muted,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val length = Jalali.monthLength(month.year, month.month)
            val firstJdn = Jalali.toJdn(month.year, month.month, 1)
            val lead = Jalali.dayOfWeek(firstJdn)
            val cells = lead + length
            val rows = (cells + 6) / 7
            var shiftDays = 0
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val index = r * 7 + c
                        val day = index - lead + 1
                        Box(Modifier.weight(1f).padding(2.dp)) {
                            if (day in 1..length) {
                                val jdn = firstJdn + day - 1
                                val on = shift?.isShift(jdn)
                                if (on == true) shiftDays++
                                DayCell(
                                    day = day, on = on, today = jdn == today.jdn, friday = c == 6,
                                    manual = shift?.isOverridden(jdn) == true,
                                    onClick = if (shift != null) ({ onDay(jdn) }) else null,
                                )
                            }
                        }
                    }
                }
            }
            if (shift != null) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    DayBadge(true)
                    Text(" شیفت", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                    Spacer(Modifier.size(12.dp))
                    DayBadge(false)
                    Text(" استراحت", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                    Spacer(Modifier.size(12.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MColors.Amber))
                    Text(" تغییر دستی", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${shiftDays.fa()} روز شیفت",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ShiftColors.On,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(MColors.Bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = MColors.Ink) }
}

@Composable
private fun DayCell(day: Int, on: Boolean?, today: Boolean, friday: Boolean, manual: Boolean, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(9.dp)
    val bg = when (on) { true -> ShiftColors.OnBg; false -> ShiftColors.OffBg; null -> Color.Transparent }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(shape)
            .background(bg)
            .border(if (today) 2.dp else 0.dp, if (today) MColors.Green else Color.Transparent, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                day.fa(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (today) FontWeight.Black else FontWeight.Bold),
                color = when {
                    friday -> MColors.Error
                    on == false -> MColors.Muted
                    else -> MColors.Ink
                },
            )
            if (on != null) {
                Text(
                    if (on) "ش" else "ا",
                    color = if (on) ShiftColors.On else ShiftColors.Off,
                    fontWeight = FontWeight.Black, fontSize = 13.sp, lineHeight = 14.sp,
                )
            }
        }
        if (manual) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(6.dp).clip(CircleShape).background(MColors.Amber))
        }
    }
}

@Composable
private fun PatternChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Corners.R)
    Box(
        modifier
            .height(46.dp)
            .then(if (selected) Modifier.clayInset(Corners.R, Color(0xFFDCEEE2)).border(1.5.dp, MColors.Green2, RoundedCornerShape(Corners.R)) else Modifier.clay(Corners.R, depth = 4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) MColors.Green else MColors.Ink, textAlign = TextAlign.Center,
        )
    }
}
