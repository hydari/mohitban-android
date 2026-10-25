package ir.mohitban.app.ui.records

import android.graphics.BitmapFactory
import ir.mohitban.app.ui.theme.Corners
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mohitban.app.ui.components.GreenButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.jalali.Season
import ir.mohitban.app.core.text.amountText
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.FuelType
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.ui.common.Share
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.BottomBar
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.TagBadge
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.EmptyState
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(private val fuel: FuelRepository) : ViewModel() {
    private val today = JalaliDate.today()
    val filter = MutableStateFlow(today.year to today.season)

    val entries = filter.flatMapLatest<Pair<Int, Season>, List<FuelEntry>?> { (y, s) -> fuel.observeSeason(y, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setYear(y: Int) { filter.value = y to filter.value.second }
    fun setSeason(s: Season) { filter.value = filter.value.first to s }
    fun delete(id: Long) = viewModelScope.launch { fuel.delete(id) }
}

@Composable
fun SeasonChips(selected: Season, onSelect: (Season) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Season.entries.forEach { s ->
            val on = s == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(shape)
                    .background(if (on) MColors.Green else Color.White)
                    .border(1.5.dp, if (on) MColors.Green else MColors.Line, shape)
                    .clickable { onSelect(s) },
                contentAlignment = Alignment.Center,
            ) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, color = if (on) Color.White else MColors.Muted)
            }
        }
    }
}

@Composable
fun YearSelector(year: Int, onYear: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = JalaliDate.today().year
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, MColors.Line, RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(year.fa(), style = MaterialTheme.typography.titleSmall)
            Icon(Icons.Outlined.ExpandMore, null, tint = MColors.Muted, modifier = Modifier.padding(start = 6.dp).size(20.dp))
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.background(Color.White)) {
            (current downTo current - 6).forEach { y ->
                DropdownMenuItem(text = { Text(y.fa()) }, onClick = { onYear(y); open = false })
            }
        }
    }
}

@Composable
fun RecordsScreen(onEdit: (Long) -> Unit, onTab: (MainTab) -> Unit, onBack: (() -> Unit)?) {
    val vm = appViewModel { c, _ -> RecordsViewModel(c.fuelRepository) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<FuelEntry?>(null) }
    var preview by remember { mutableStateOf<FuelEntry?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("سوابق سوخت", onBack = onBack, action = { YearSelector(filter.first, vm::setYear) })
            SeasonChips(filter.second, vm::setSeason, Modifier.padding(horizontal = 16.dp))
            val list = entries
            if (list != null) {
                Text(
                    "${filter.second.months} ${filter.first.fa()} | ${list.size.fa()} ثبت | ${list.sumOf { it.total }.rial()} ریال",
                    style = MaterialTheme.typography.labelMedium, color = MColors.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    Icons.AutoMirrored.Outlined.FormatListBulleted, "ثبتی در این فصل نیست",
                    "هزینه‌های سوخت ثبت‌شده در ${filter.second.title} ${filter.first.fa()} اینجا نمایش داده می‌شود.",
                )
                else -> {
                    val grouped = list.groupBy { it.date.month }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        grouped.forEach { (month, monthEntries) ->
                            item(key = "h$month") {
                                Text(
                                    "${Jalali.MONTH_NAMES[month - 1]} ${filter.first.fa()}",
                                    style = MaterialTheme.typography.labelLarge, color = MColors.Muted,
                                    modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                                )
                            }
                            items(monthEntries, key = { it.id }) { e ->
                                EntryCard(
                                    e,
                                    onOpen = { preview = e },
                                    onPdf = {
                                        scope.launch {
                                            val f = withContext(Dispatchers.IO) { context.container.fuelForm.createPdf(e) }
                                            Share.file(context, f, "application/pdf")
                                        }
                                    },
                                    onImage = {
                                        scope.launch {
                                            val f = withContext(Dispatchers.IO) { context.container.fuelForm.createPng(e) }
                                            Share.file(context, f, "image/png")
                                        }
                                    },
                                    onEdit = { onEdit(e.id) },
                                    onDelete = { toDelete = e },
                                )
                            }
                        }
                    }
                }
            }
        }
        BottomBar(MainTab.RECORDS, onTab, Modifier.align(Alignment.BottomCenter))
    }

    preview?.let { e -> FormPreviewDialog(e, onDismiss = { preview = null }) }

    toDelete?.let { e ->
        ConfirmDialog(
            title = "حذف ثبت سوخت",
            text = "ثبت ${e.fuelType.short} تاریخ ${e.date.format().fa()} به مبلغ ${e.total.rial()} ریال حذف شود؟ این کار برگشت‌پذیر نیست.",
            confirm = "حذف",
            danger = true,
            onConfirm = { vm.delete(e.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun EntryCard(e: FuelEntry, onOpen: () -> Unit, onPdf: () -> Unit, onImage: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val gas = e.fuelType == FuelType.CNG
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(
                    if (gas) Icons.Outlined.LocalFireDepartment else Icons.Outlined.WaterDrop,
                    if (gas) Color(0xFFF3E6D3) else MColors.GreenLight,
                    if (gas) Color(0xFF8A6A3F) else MColors.Green,
                    size = 42.dp,
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.fuelType.short} | ${e.amount.amountText()} ${e.fuelType.unit}", style = MaterialTheme.typography.titleSmall)
                        // only the ranger sees this; reports and exports never mention edits
                        if (e.editedAt != null) TagBadge("ویرایش شده", Color(0xFFFFF1D6), Color(0xFFB7791F), Modifier.padding(start = 6.dp))
                    }
                    Text(
                        "${e.date.formatLong().fa()} | کیلومتر ${e.odometer.rial()}",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(e.total.rial(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = MColors.Green)
                    Text("ریال", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.receiptPath != null) Chip(Icons.Outlined.Receipt, "رسید دارد")
                Chip(Icons.Outlined.Draw, if (e.passengerSignaturePath != null) "۲ امضا" else "۱ امضا")
                Chip(null, "راننده: ${e.snapshot.driverName}")
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MColors.Line))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(Icons.Outlined.PictureAsPdf, "PDF", Modifier.weight(1f), onClick = onPdf)
                Action(Icons.Outlined.ImageIcon, "تصویر", Modifier.weight(1f), onClick = onImage)
                Action(Icons.Outlined.Edit, "ویرایش", Modifier.weight(1f), onClick = onEdit)
                Action(Icons.Outlined.Delete, "حذف", Modifier.weight(1f), danger = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector?, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.R)).background(MColors.Bg).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = MColors.Muted, modifier = Modifier.size(13.dp).padding(end = 2.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MColors.Muted, maxLines = 1)
    }
}

@Composable
private fun Action(icon: ImageVector, text: String, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (danger) MColors.ErrorBg else MColors.Bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (danger) MColors.Error else MColors.Green
        Icon(icon, null, tint = c, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = c, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Shows the official form of an entry as an image, with options to send it as image or PDF. */
@Composable
private fun FormPreviewDialog(e: FuelEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val image by produceState<ImageBitmap?>(null, e.id) {
        value = withContext(Dispatchers.IO) {
            val f = context.container.fuelForm.createPng(e)
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(Corners.R))
                .background(Color(0xF7101410))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "فرم هزینه سوخت | ${e.date.format().fa()}",
                    style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, "بستن", tint = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                val img = image
                if (img == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Image(
                        img, "فرم هزینه سوخت", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GreenButton(
                    "ارسال تصویر",
                    onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { context.container.fuelForm.createPng(e) }
                            Share.file(context, f, "image/png")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                GreenButton(
                    "ارسال PDF",
                    onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { context.container.fuelForm.createPdf(e) }
                            Share.file(context, f, "application/pdf")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
