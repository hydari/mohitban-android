package ir.mohitban.app.ui.components

import androidx.compose.foundation.background
import ir.mohitban.app.ui.theme.Corners
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.ui.theme.MColors

/** A Jalali month-grid date picker (Saturday first, RTL). */
@Composable
fun JalaliDatePickerDialog(
    initial: JalaliDate,
    onDismiss: () -> Unit,
    onConfirm: (JalaliDate) -> Unit,
    maxDate: JalaliDate? = JalaliDate.today(),
) {
    val today = remember { JalaliDate.today() }
    var selected by remember { mutableStateOf(initial) }
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }

    fun shift(delta: Int) {
        val d = JalaliDate(year, month, 1).plusMonths(delta)
        year = d.year; month = d.month
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(Corners.R))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Text("تاریخ سوخت‌گیری", style = MaterialTheme.typography.labelMedium, color = MColors.Muted)
            Text(selected.formatLong().fa(), style = MaterialTheme.typography.titleLarge, color = MColors.Green)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { shift(-1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "ماه قبل", tint = MColors.Ink)
                }
                Text(
                    "${Jalali.MONTH_NAMES[month - 1]} ${year.fa()}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { shift(1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "ماه بعد", tint = MColors.Ink)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Jalali.WEEKDAY_SHORT.forEachIndexed { i, d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (i == 6) MColors.Error else MColors.Muted,
                    )
                }
            }
            val first = JalaliDate(year, month, 1)
            val offset = first.dayOfWeek
            val len = Jalali.monthLength(year, month)
            val cells = offset + len
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..len) {
                                val d = JalaliDate(year, month, day)
                                val isSel = d == selected
                                val isToday = d == today
                                val disabled = maxDate != null && d > maxDate
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) MColors.Green else Color.Transparent)
                                        .then(if (isToday && !isSel) Modifier.border(1.5.dp, MColors.Green2, CircleShape) else Modifier)
                                        .clickable(enabled = !disabled) { selected = d },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.fa(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal),
                                        color = when {
                                            isSel -> Color.White
                                            disabled -> Color(0xFFC5CCC7)
                                            c == 6 -> MColors.Error
                                            else -> MColors.Ink
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { selected = today; year = today.year; month = today.month }) {
                    Text("امروز", color = MColors.Green2)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("انصراف", color = MColors.Muted) }
                TextButton(onClick = { onConfirm(selected) }) {
                    Text("تأیید", color = MColors.Green, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
