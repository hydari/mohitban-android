package ir.mohitban.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WaterDrop
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.digitsOnly
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.domain.model.FuelRates
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.BottomBar
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.components.NumericMode
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.SectionTitle
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val lastBackup = settings.lastBackupAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var g1 by mutableStateOf("")
    var g2 by mutableStateOf("")
    var g3 by mutableStateOf("")
    var cng by mutableStateOf("")
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val r = settings.getRates()
            g1 = r.gasoline[0].toString(); g2 = r.gasoline[1].toString(); g3 = r.gasoline[2].toString()
            cng = r.cng.toString()
            loaded = true
        }
    }

    fun saveRates(onDone: (Boolean) -> Unit) {
        val values = listOf(g1, g2, g3, cng).map { it.digitsOnly().toLongOrNull() ?: 0L }
        if (values.any { it <= 0 }) { onDone(false); return }
        viewModelScope.launch {
            settings.saveRates(FuelRates(values.take(3), values[3]))
            onDone(true)
        }
    }

    fun markBackup() = viewModelScope.launch { settings.markBackup(System.currentTimeMillis()) }
}

@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit,
    onEditVehicle: () -> Unit,
    onTab: (MainTab) -> Unit,
    onBack: (() -> Unit)?,
    onRestored: () -> Unit,
) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c.settingsRepository) }
    val lastBackup by vm.lastBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var working by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            working = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { context.container.backup.export(uri) }.isSuccess }
                working = false
                if (ok) vm.markBackup()
                Toast.makeText(context, if (ok) "فایل پشتیبان ذخیره شد" else "ساخت پشتیبان ناموفق بود", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Box(Modifier.fillMaxSize().background(MColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("تنظیمات", onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                SectionTitle("اطلاعات")
                AppCard(Modifier.fillMaxWidth()) {
                    SettingRow(Icons.Outlined.Person, "اطلاعات فردی و محل خدمت", "نام، کد ملی، استان، شهرستان، پاسگاه", onEditProfile)
                    Divider()
                    SettingRow(Icons.Outlined.DirectionsCar, "اطلاعات خودرو", "نوع، مدل، پلاک قرمز، کیلومتر", onEditVehicle)
                }

                SectionTitle("نرخ سوخت (ریال)")
                if (vm.loaded) {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MField(vm.g1, { vm.g1 = it }, label = "بنزین نرخ ۱", numeric = NumericMode.Grouped, maxLength = 9, modifier = Modifier.weight(1f), leadingIcon = Icons.Outlined.WaterDrop)
                                MField(vm.g2, { vm.g2 = it }, label = "بنزین نرخ ۲", numeric = NumericMode.Grouped, maxLength = 9, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MField(vm.g3, { vm.g3 = it }, label = "بنزین نرخ ۳", numeric = NumericMode.Grouped, maxLength = 9, modifier = Modifier.weight(1f), leadingIcon = Icons.Outlined.WaterDrop)
                                MField(vm.cng, { vm.cng = it }, label = "گاز CNG (هر کیلو)", numeric = NumericMode.Grouped, maxLength = 9, modifier = Modifier.weight(1f), leadingIcon = Icons.Outlined.LocalFireDepartment)
                            }
                            Text(
                                "تغییر نرخ فقط روی ثبت‌های جدید اثر دارد؛ فرم‌های قبلی با نرخ خودشان باقی می‌مانند.",
                                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(vertical = 10.dp),
                            )
                            PillButton("ذخیره نرخ‌ها", icon = Icons.Outlined.Check, onClick = {
                                vm.saveRates { ok ->
                                    Toast.makeText(context, if (ok) "نرخ‌ها ذخیره شد" else "همه نرخ‌ها باید بزرگ‌تر از صفر باشند", Toast.LENGTH_SHORT).show()
                                }
                            }, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                }

                SectionTitle("پشتیبان‌گیری (بدون اینترنت)")
                AppCard(Modifier.fillMaxWidth()) {
                    SettingRow(
                        Icons.Outlined.Backup, "ساخت فایل پشتیبان",
                        lastBackup?.let { "آخرین پشتیبان: ${formatMillis(it)}" } ?: "همه اطلاعات، امضاها و رسیدها در یک فایل",
                    ) {
                        exportLauncher.launch("mohitban-backup-${JalaliDate.today().format().replace('/', '-')}.zip")
                    }
                    Divider()
                    SettingRow(Icons.Outlined.Restore, "بازیابی از فایل پشتیبان", "اطلاعات فعلی با محتوای فایل جایگزین می‌شود") {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                }
                Text(
                    "پیشنهاد: هر هفته یک فایل پشتیبان بسازید و روی کارت حافظه یا رایانه نگه دارید تا با گم شدن گوشی اطلاعات از بین نرود.",
                    style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(8.dp),
                )

                SectionTitle("درباره")
                AppCard(Modifier.fillMaxWidth()) {
                    SettingRow(Icons.Outlined.Info, "محیط‌بان — نسخه ۱٫۰", "همراه محیط‌بانان، برای طبیعت ایران | کاملاً آفلاین", null)
                }
                Spacer(Modifier.height(120.dp))
            }
        }
        BottomBar(MainTab.SETTINGS, onTab, Modifier.align(Alignment.BottomCenter))
        if (working) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }

    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = "بازیابی اطلاعات",
            text = "همه اطلاعات فعلی این گوشی حذف و با اطلاعات فایل پشتیبان جایگزین می‌شود. ادامه می‌دهید؟",
            confirm = "بازیابی",
            danger = true,
            onDismiss = { confirmRestore = null },
            onConfirm = {
                confirmRestore = null
                working = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { context.container.backup.restore(uri) } }
                    working = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "اطلاعات بازیابی شد", Toast.LENGTH_LONG).show()
                        onRestored()
                    } else {
                        Toast.makeText(context, "فایل پشتیبان معتبر نیست", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}

private fun formatMillis(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val d = Jalali.fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    return d.formatLong(withWeekday = false).fa()
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(MColors.Line))

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, MColors.GreenLight, MColors.Green, size = 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MColors.Muted, modifier = Modifier.size(20.dp))
    }
}
