package ir.mohitban.app.ui.shift

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.reminder.CalNote
import ir.mohitban.app.reminder.NoteAlarm
import ir.mohitban.app.reminder.NoteStore
import ir.mohitban.app.reminder.Reminders
import ir.mohitban.app.ui.components.CancelButton
import ir.mohitban.app.ui.components.DialogActions
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.theme.MColors

private val NoteBlue = Color(0xFF2F7FC1)

/** The notes of every day, reloaded whenever one changes. */
@Composable
fun rememberNotes(): List<CalNote> {
    val context = LocalContext.current
    val ver by NoteStore.version.collectAsStateWithLifecycle()
    return remember(ver) { NoteStore.all(context) }
}

/** One day: its shift (with the switch), its environmental days and its notes. */
@Composable
fun DayDialog(
    jdn: Int,
    shiftOn: Boolean?,
    occasions: List<String>,
    onToggleShift: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val notes = rememberNotes().filter { it.jdn == jdn }
    var editing by remember { mutableStateOf<CalNote?>(null) }
    val date = JalaliDate.fromJdn(jdn)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(date.formatLong().fa(), fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (shiftOn != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (shiftOn) "روز شیفت" else "روز استراحت",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (shiftOn) ShiftColors.On else ShiftColors.Off, modifier = Modifier.weight(1f),
                        )
                        if (onToggleShift != null) {
                            Row(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(MColors.Bg).clickable(onClick = onToggleShift).padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.SwapHoriz, null, tint = MColors.Ink, modifier = Modifier.size(18.dp))
                                Text(" ${if (shiftOn) "استراحت شود" else "شیفت شود"}", style = MaterialTheme.typography.labelMedium, color = MColors.Ink)
                            }
                        }
                    }
                }
                occasions.forEach {
                    Text("• $it", style = MaterialTheme.typography.labelLarge, color = Color(0xFF1F8A4C), modifier = Modifier.padding(top = 6.dp))
                }
                Text("یادداشت‌ها و یادآوری‌ها", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.padding(top = 12.dp))
                if (notes.isEmpty()) {
                    Text("برای این روز یادداشتی ندارید.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                notes.forEach { n -> NoteRow(n, onClick = { editing = n }, onDone = { NoteStore.save(context, n.copy(done = !n.done)) }) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(NoteBlue)
                        .clickable { editing = CalNote(System.currentTimeMillis(), jdn, "") }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.EditNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("  یادداشت یا یادآوری تازه", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }
        },
        confirmButton = { CancelButton("بستن", Modifier.fillMaxWidth(), onClick = onDismiss) },
    )
    editing?.let { n -> NoteEditor(n, isNew = notes.none { it.id == n.id }, onDismiss = { editing = null }) }
}

@Composable
fun NoteRow(n: CalNote, onClick: () -> Unit, onDone: () -> Unit, showDate: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFF5FB)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (n.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (n.done) "انجام شد" else "انجام نشده",
            tint = if (n.done) Color(0xFF1B7F3B) else NoteBlue,
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onDone),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                n.text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (n.done) MColors.Muted else MColors.Ink, maxLines = 2,
            )
            val meta = listOfNotNull(
                if (showDate) JalaliDate.fromJdn(n.jdn).formatLong().fa() else null,
                n.timeText()?.let { "ساعت $it" },
                n.remindBefore.takeIf { it >= 0 }?.let { "یادآوری: ${Reminders.label(it)}" },
            ).joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
        }
        if (n.remindBefore >= 0 && !n.done) Icon(Icons.Outlined.Alarm, null, tint = NoteBlue, modifier = Modifier.size(18.dp))
    }
}

/** Write or change a note: text, optional time, and when to be reminded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(note: CalNote, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(note.text) }
    var minute by remember { mutableStateOf(note.minute) }
    var before by remember { mutableStateOf(note.remindBefore.takeIf { !isNew } ?: 1440) }
    var custom by remember { mutableStateOf(Reminders.PRESETS.none { it.second == before }) }
    var customNum by remember { mutableStateOf(if (custom) (if (before % 1440 == 0) before / 1440 else before / 60).toString() else "") }
    var customDays by remember { mutableStateOf(before % 1440 == 0) }
    var pickTime by remember { mutableStateOf(false) }
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    val effectiveBefore = if (custom) customNum.toIntOrNull()?.let { if (customDays) it * 1440 else it * 60 } ?: -1 else before
    val draft = note.copy(text = text.trim(), minute = minute, remindBefore = effectiveBefore, done = if (isNew) false else note.done)
    val remindAt = draft.remindAt()
    val past = remindAt != null && remindAt <= System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (isNew) "یادداشت تازه" else "ویرایش یادداشت") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(JalaliDate.fromJdn(note.jdn).formatLong().fa(), style = MaterialTheme.typography.labelLarge, color = NoteBlue)
                MField(text, { text = it }, Modifier.padding(top = 6.dp), label = "کار یا یادداشت", placeholder = "تحویل گزارش فصلی به اداره", maxLength = 200)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(MColors.Bg).clickable { pickTime = true }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, null, tint = MColors.Ink, modifier = Modifier.size(20.dp))
                    Text(
                        "  ساعت: " + (draft.timeText() ?: "ندارد (یادآوری از ساعت ۸ صبح حساب می‌شود)"),
                        style = MaterialTheme.typography.labelLarge, color = MColors.Ink, modifier = Modifier.weight(1f),
                    )
                    if (minute != null) Text("حذف", style = MaterialTheme.typography.labelMedium, color = MColors.Error, modifier = Modifier.clickable { minute = null })
                }
                Text("یادآوری", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                (Reminders.PRESETS.map { it.first to it.second } + ("دلخواه" to Int.MIN_VALUE)).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (label, v) ->
                            val on = if (v == Int.MIN_VALUE) custom else !custom && before == v
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (on) NoteBlue else MColors.Bg)
                                    .clickable { if (v == Int.MIN_VALUE) custom = true else { custom = false; before = v } }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) Color.White else MColors.Ink, maxLines = 1)
                            }
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
                if (custom) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MField(customNum, { customNum = it.filter(Char::isDigit) }, Modifier.weight(1f), label = "چند", numeric = NumericMode.Digits, maxLength = 3)
                        listOf(true to "روز قبل", false to "ساعت قبل").forEach { (d, label) ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (customDays == d) NoteBlue else MColors.Bg)
                                    .clickable { customDays = d }.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (customDays == d) Color.White else MColors.Ink) }
                        }
                    }
                }
                if (remindAt != null) {
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = remindAt }
                    val jd = ir.mohitban.app.core.jalali.Jalali.fromGregorian(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
                    Text(
                        if (past) "زمان این یادآوری گذشته است؛ یادآوری ارسال نمی‌شود."
                        else "اعلان: ${jd.formatLong().fa()} ساعت ${String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)).fa()}",
                        style = MaterialTheme.typography.labelSmall, color = if (past) MColors.Amber else Color(0xFF1B7F3B), modifier = Modifier.padding(top = 6.dp),
                    )
                    if (!past && !NoteAlarm.canExact(context) && Build.VERSION.SDK_INT >= 31) {
                        Text(
                            "برای اعلان دقیق سر ساعت، اجازه‌ی «هشدار و یادآوری» را بدهید.",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Amber, modifier = Modifier.padding(top = 4.dp)
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                        )
                    }
                }
                if (!isNew) {
                    Row(
                        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).clickable { NoteStore.delete(context, note.id); onDismiss() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Delete, null, tint = MColors.Error, modifier = Modifier.size(18.dp))
                        Text(" حذف یادداشت", style = MaterialTheme.typography.labelMedium, color = MColors.Error)
                    }
                }
            }
        },
        confirmButton = {
            DialogActions("ذخیره", onOk = {
                if (draft.remindBefore >= 0 && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                NoteStore.save(context, draft)
                onDismiss()
            }, onCancel = onDismiss, okEnabled = text.isNotBlank())
        },
    )

    if (pickTime) {
        val st = rememberTimePickerState(initialHour = (minute ?: 480) / 60, initialMinute = (minute ?: 480) % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = Color.White,
            title = { Text("ساعت") },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    TimePicker(st, colors = TimePickerDefaults.colors(selectorColor = NoteBlue, timeSelectorSelectedContainerColor = NoteBlue.copy(alpha = 0.18f)))
                }
            },
            confirmButton = {
                DialogActions("تأیید", onOk = { minute = st.hour * 60 + st.minute; pickTime = false }, onCancel = { pickTime = false })
            },
        )
    }
}

/** The month's notes under the calendar. */
@Composable
fun MonthNotesCard(month: JalaliDate, today: Int, onOpenDay: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val first = ir.mohitban.app.core.jalali.Jalali.toJdn(month.year, month.month, 1)
    val last = first + ir.mohitban.app.core.jalali.Jalali.monthLength(month.year, month.month) - 1
    val notes = rememberNotes().filter { it.jdn in first..last }
    ir.mohitban.app.ui.components.AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, tint = NoteBlue, modifier = Modifier.size(22.dp))
                Text("  یادداشت‌ها و یادآوری‌های ماه", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.weight(1f))
                Text(
                    "+ افزودن", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = NoteBlue,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenDay(if (today in first..last) today else first) }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (notes.isEmpty()) {
                Text("روی هر روز تقویم بزنید تا یادداشت یا یادآوری بگذارید؛ چند روز یا چند ساعت قبل اعلان می‌گیرید.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            notes.forEach { n -> NoteRow(n, onClick = { onOpenDay(n.jdn) }, onDone = { NoteStore.save(context, n.copy(done = !n.done)) }, showDate = true) }
        }
    }
}
