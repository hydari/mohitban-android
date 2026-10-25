package ir.mohitban.app.ui.report

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share as ShareIcon
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.R
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.jalali.Season
import ir.mohitban.app.core.text.amountText
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.domain.model.QuarterSummary
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.reminder.ReportReminder
import ir.mohitban.app.ui.common.Share
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.BottomBar
import ir.mohitban.app.ui.components.EmptyState
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.components.OutlinePill
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.SectionTitle
import ir.mohitban.app.ui.components.SuccessDialog
import ir.mohitban.app.ui.components.SignatureField
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.components.rememberSignatureFieldState
import ir.mohitban.app.ui.components.rememberStoredImage
import ir.mohitban.app.ui.records.SeasonChips
import ir.mohitban.app.ui.records.YearSelector
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(profiles: ProfileRepository, fuel: FuelRepository, private val settings: SettingsRepository) : ViewModel() {
    private val today = JalaliDate.today()
    val filter = MutableStateFlow(today.year to today.season)
    val profile = profiles.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val summary = filter.flatMapLatest<Pair<Int, Season>, QuarterSummary?> { (y, s) ->
        fuel.observeSeason(y, s).map { list -> QuarterSummary(y, s, list.sortedWith(compareBy({ it.date.jdn }, { it.odometer }, { it.id }))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setYear(y: Int) { filter.value = y to filter.value.second }
    fun setSeason(s: Season) { filter.value = filter.value.first to s }

    /** The ranger's own latest signature in this season, used as the default report signature. */
    /** The report of this season was sent / saved / printed: stop the reminders. */
    fun markReported() {
        val (y, s) = filter.value
        viewModelScope.launch { settings.markSeasonReported(ReportReminder.key(y, s)) }
    }

    fun defaultSignature(summary: QuarterSummary?, profile: RangerProfile?): String? =
        summary?.entries?.lastOrNull { it.snapshot.driverName == profile?.fullName }?.driverSignaturePath
}

@Composable
fun ReportScreen(onTab: (MainTab) -> Unit, onBack: (() -> Unit)?) {
    val vm = appViewModel { c, _ -> ReportViewModel(c.profileRepository, c.fuelRepository, c.settingsRepository) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sig = rememberSignatureFieldState()
    var viewReceipt by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<Bitmap>?>(null) }
    var pendingSave by remember { mutableStateOf<File?>(null) }
    var savedOk by remember { mutableStateOf(false) }

    val defaultSigPath = vm.defaultSignature(summary, profile)
    val defaultSig by rememberStoredImage(defaultSigPath, 600)
    LaunchedEffect(defaultSig) { if (defaultSig != null && !sig.changed) sig.existing = defaultSig }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pendingSave
        if (uri != null && f != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            }.onSuccess { savedOk = true }
        }
        pendingSave = null
    }

    fun build(then: (File) -> Unit) {
        val s = summary ?: return
        val p = profile ?: return
        busy = true
        scope.launch {
            val sigBitmap = sig.bitmap
            val file = withContext(Dispatchers.IO) {
                context.container.quarterReport.createPdf(s, p, sigBitmap, if (sig.hasNewDrawing || sig.existing == null) null else defaultSigPath)
            }
            busy = false
            then(file)
        }
    }

    val fileName = "گزارش-سوخت-${filter.second.title}-${filter.first}.pdf"

    Box(Modifier.fillMaxSize().background(MColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                "گزارش فصلی", onBack = onBack,
                action = {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { build { vm.markReported(); pendingSave = it; saveLauncher.launch(fileName) } }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.FileDownload, "ذخیره PDF", tint = MColors.Green)
                    }
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("سال", style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.padding(end = 8.dp))
                    YearSelector(filter.first, vm::setYear)
                }
                Spacer(Modifier.height(10.dp))
                SeasonChips(filter.second, vm::setSeason)
                Spacer(Modifier.height(14.dp))

                val s = summary
                // ---------- header card ----------
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.doe_logo_full), null, modifier = Modifier.size(78.dp))
                        Text("گزارش فصلی هزینه سوخت", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            "پاسگاه محیط‌بانی ${profile?.station ?: ""} | منطقه ${profile?.protectedArea ?: ""}",
                            style = MaterialTheme.typography.bodySmall, color = MColors.Muted, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(MColors.Line))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            InfoPair("سال", filter.first.fa())
                            InfoPair("فصل", filter.second.title)
                            InfoPair("تاریخ تهیه", JalaliDate.today().format().fa())
                        }
                    }
                }
                if (s == null) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MColors.Green) }
                } else if (s.entries.isEmpty()) {
                    EmptyState(
                        Icons.Outlined.Visibility, "ثبتی برای این فصل وجود ندارد",
                        "${s.season.months} ${s.year.fa()} — پس از ثبت هزینه‌های سوخت، گزارش اینجا آماده می‌شود.",
                    )
                } else {
                    SectionTitle("خلاصه عملکرد")
                    AppCard(Modifier.fillMaxWidth()) {
                        TableHeader(listOf("شاخص", "مقدار"))
                        TableRow(listOf("تعداد ثبت سوخت", s.count.fa()))
                        TableRow(listOf("مجموع بنزین (لیتر)", s.gasolineLiters.amountText()))
                        TableRow(listOf("مجموع گاز (کیلوگرم)", s.cngKg.amountText()))
                        TableRow(listOf("کیلومتر ابتدای فصل", s.firstOdometer?.rial() ?: "—"))
                        TableRow(listOf("کیلومتر آخرین ثبت", s.lastOdometer?.rial() ?: "—"), last = true)
                    }
                    SectionTitle("جزئیات هزینه‌های سوخت")
                    AppCard(Modifier.fillMaxWidth()) {
                        TableHeader(listOf("نوع سوخت", "مقدار", "مبلغ (ریال)"))
                        TableRow(listOf("گاز CNG", "${s.cngKg.amountText()} کیلوگرم", s.cngTotal.rial()))
                        TableRow(listOf("بنزین", "${s.gasolineLiters.amountText()} لیتر", s.gasolineTotal.rial()))
                        TableRow(listOf("جمع کل", "—", s.total.rial()), last = true, highlight = true)
                    }
                    val withReceipts = s.withReceipts
                    SectionTitle("فیش‌های پرداختی (${withReceipts.size.fa()})")
                    if (withReceipts.isEmpty()) {
                        Text("برای این فصل عکس رسیدی ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(horizontal = 6.dp))
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            withReceipts.forEach { e -> ReceiptThumb(e) { viewReceipt = e.receiptPath } }
                        }
                        Text(
                            "تصویر فیش‌ها در صفحات پیوست گزارش PDF هم قرار می‌گیرد.",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SignatureField(sig, "امضای محیط‌بان", Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(110.dp)
                                .drawBehind {
                                    drawCircle(
                                        Color(0xFF9DB3A4), radius = size.minDimension / 2 - 2.dp.toPx(),
                                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("محل مهر\nرئیس اداره", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8DA294), textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    PillButton("مشاهده گزارش A4", icon = Icons.Outlined.Visibility, loading = busy, onClick = {
                        build { f -> scope.launch { preview = withContext(Dispatchers.IO) { context.container.quarterReport.renderPages(f) } } }
                    })
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinePill("ارسال", { build { vm.markReported(); Share.file(context, it, "application/pdf") } }, Modifier.weight(1f), Icons.Outlined.ShareIcon)
                        OutlinePill("ذخیره", { build { vm.markReported(); pendingSave = it; saveLauncher.launch(fileName) } }, Modifier.weight(1f), Icons.Outlined.FileDownload)
                        OutlinePill("چاپ", { build { vm.markReported(); Share.print(context, it, fileName) } }, Modifier.weight(1f), Icons.Outlined.Print)
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
        }
        BottomBar(MainTab.REPORTS, onTab, Modifier.align(Alignment.BottomCenter))
    }

    if (savedOk) SuccessDialog("گزارش ذخیره شد", onFinished = { savedOk = false })

    viewReceipt?.let { path ->
        val img by rememberStoredImage(path, 1800)
        Dialog(onDismissRequest = { viewReceipt = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color(0xF0101410)).clickable { viewReceipt = null }, contentAlignment = Alignment.Center) {
                img?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp)) }
            }
        }
    }

    preview?.let { pages ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color(0xF0101410))) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(40.dp))
                    pages.forEach { bmp ->
                        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                    }
                }
                Box(
                    Modifier.align(Alignment.TopStart).padding(16.dp).size(44.dp).clip(CircleShape).background(Color.White).clickable { preview = null },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, "بستن", tint = MColors.Ink) }
            }
        }
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
    }
}

@Composable
private fun TableHeader(cells: List<String>) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFEEF3EF)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        cells.forEachIndexed { i, c ->
            Text(
                c, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF3D4A42),
                modifier = Modifier.weight(if (i == 0) 1.3f else 1f),
                textAlign = if (i == cells.lastIndex && i > 0) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, last: Boolean = false, highlight: Boolean = false) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(if (highlight) Color(0xFFF4F8F5) else Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            cells.forEachIndexed { i, c ->
                Text(
                    c,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal),
                    color = if (highlight) MColors.Green else MColors.Ink,
                    modifier = Modifier.weight(if (i == 0) 1.3f else 1f),
                    textAlign = if (i == cells.lastIndex && i > 0) TextAlign.End else TextAlign.Start,
                )
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEF1EE)))
    }
}

@Composable
private fun ReceiptThumb(e: ir.mohitban.app.domain.model.FuelEntry, onClick: () -> Unit) {
    val img by rememberStoredImage(e.receiptPath, 400)
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE4E3DE)), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(e.date.format().fa(), style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp))
        Text("${e.total.rial()} ریال", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
    }
}
