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
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import ir.mohitban.app.ui.components.Developer
import ir.mohitban.app.ui.components.DeveloperDialog
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
import ir.mohitban.app.ui.components.SuccessDialog
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
    onHelp: () -> Unit = {},
) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c.settingsRepository) }
    val lastBackup by vm.lastBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var working by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    var askExportPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var askRestorePassword by remember { mutableStateOf<android.net.Uri?>(null) }
    var restorePassword by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            working = true
            scope.launch {
                val pw = exportPassword
                val ok = withContext(Dispatchers.IO) { runCatching { context.container.backup.export(uri, pw) }.isSuccess }
                working = false
                exportPassword = null
                if (ok) { vm.markBackup(); success = if (pw.isNullOrEmpty()) "فایل پشتیبان ذخیره شد" else "فایل پشتیبان رمزگذاری‌شده ذخیره شد" }
                else Toast.makeText(context, "ساخت پشتیبان ناموفق بود", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val protectedFile = withContext(Dispatchers.IO) { runCatching { context.container.backup.isEncrypted(uri) }.getOrDefault(false) }
            restorePassword = null
            if (protectedFile) askRestorePassword = uri else confirmRestore = uri
        }
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
                    SettingRow(Icons.Outlined.DirectionsCar, "وسایل نقلیه و پلاک‌ها", "خودرو و موتورسیکلت، پلاک قرمز، کیلومتر", onEditVehicle)
                }

                SectionTitle("نرخ سوخت (ریال)")
                if (vm.loaded) {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MField(vm.g1, { vm.g1 = it }, label = "بنزین نرخ ۱", numeric = NumericMode.Grouped, maxLength = 9, modifier = Modifier.weight(1f), leadingIcon = Icons.Outlined.WaterDrop)
                                MField(vm.g2, { vm.g2 = it }, label = "بنزین نرخ ۲", numeric = NumericMode.Grouped, maxLength = 9, modifier = Modifier.weight(1f), leadingIcon = Icons.Outlined.WaterDrop)
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
                                    if (ok) success = "نرخ‌ها ذخیره شد"
                                    else Toast.makeText(context, "همه نرخ‌ها باید بزرگ‌تر از صفر باشند", Toast.LENGTH_SHORT).show()
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
                        askExportPassword = true
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

                SectionTitle("امنیت")
                AppCard(Modifier.fillMaxWidth()) {
                    var lockOn by remember { mutableStateOf(ir.mohitban.app.security.AppLockPrefs.isEnabled(context)) }
                    val canLock = remember { ir.mohitban.app.security.AppLock.available(context) }
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleIcon(Icons.Outlined.Fingerprint, MColors.GreenLight, MColors.Green, size = 42.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("قفل برنامه", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (canLock) "ورود با اثر انگشت یا چهره؛ در گوشی‌های بدون آن، با الگو یا رمز گوشی"
                                else "ابتدا در تنظیمات گوشی یک قفل صفحه (الگو، رمز یا اثر انگشت) تعریف کنید",
                                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = lockOn && canLock, enabled = canLock,
                            onCheckedChange = { lockOn = it; ir.mohitban.app.security.AppLockPrefs.setEnabled(context, it) },
                        )
                    }
                }

                AppCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    var teamCode by remember { mutableStateOf(ir.mohitban.app.gasht.SosTeam.code(context)) }
                    Column(Modifier.padding(14.dp)) {
                        Text("کد تیم برای پیامک اضطراری", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "همه‌ی همکاران یک کد یکسان وارد کنند. پیامک‌های اضطراری امضا می‌شوند و پیامکی که امضای درست نداشته باشد هشدار جعلی حساب می‌شود. خالی بگذارید تا بررسی خاموش باشد.",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        )
                        ir.mohitban.app.ui.components.MField(
                            teamCode, { teamCode = it; ir.mohitban.app.gasht.SosTeam.setCode(context, it) },
                            placeholder = "کد طولانی و غیرقابل‌حدس", maxLength = 40,
                            error = if (teamCode.isNotBlank() && teamCode.trim().length < ir.mohitban.app.gasht.SosTeam.MIN_LENGTH) "حداقل ${ir.mohitban.app.gasht.SosTeam.MIN_LENGTH.fa()} نویسه" else null,
                        )
                    }
                }

                SectionTitle("درباره")
                var showDev by remember { mutableStateOf(false) }
                val ctx = LocalContext.current
                AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(Icons.Outlined.Lightbulb, "راهنمای استفاده", "آموزش همه‌ی بخش‌های برنامه و روش کار با هرکدام", onHelp)
                        Divider()
                        SettingRow(Icons.Outlined.Info, "محیط‌یار — نسخه ${ir.mohitban.app.BuildConfigCompat.versionName(ctx).fa()}", "طراح و برنامه‌نویس: ${Developer.NAME}") { showDev = true }
                    }
                }
                if (showDev) DeveloperDialog(onDismiss = { showDev = false })
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

    if (askExportPassword) PasswordDialog(
        title = "رمز برای فایل پشتیبان",
        text = "اگر رمز بگذارید، فایل بدون آن باز نمی‌شود. رمز را فراموش نکنید؛ قابل بازیابی نیست. برای ساخت فایل بدون رمز، «بدون رمز» را بزنید.",
        confirm = "رمزگذاری", minLength = 6, secondary = "بدون رمز",
        onSecondary = { askExportPassword = false; exportPassword = null; exportLauncher.launch(backupFileName()) },
        onDismiss = { askExportPassword = false },
        onConfirm = { askExportPassword = false; exportPassword = it; exportLauncher.launch(backupFileName()) },
    )
    askRestorePassword?.let { uri ->
        PasswordDialog(
            title = "رمز فایل پشتیبان", text = "این فایل رمزگذاری شده است. رمز آن را وارد کنید.",
            confirm = "ادامه", minLength = 1, secondary = null, onSecondary = {},
            onDismiss = { askRestorePassword = null },
            onConfirm = { pw ->
                // a wrong password is said at once and the box stays open for another try
                scope.launch {
                    val right = withContext(Dispatchers.IO) { context.container.backup.passwordWorks(uri, pw) }
                    if (right) { askRestorePassword = null; restorePassword = pw; confirmRestore = uri }
                    else Toast.makeText(context, "رمز اشتباه است یا فایل آسیب دیده است", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
    success?.let { msg -> SuccessDialog(msg, onFinished = { success = null }) }
    if (restored) SuccessDialog("اطلاعات بازیابی شد", onFinished = { restored = false; onRestored() })

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
                    val pw = restorePassword
                    val result = withContext(Dispatchers.IO) { runCatching { context.container.backup.restore(uri, pw) } }
                    working = false
                    restorePassword = null
                    if (result.isSuccess) {
                        restored = true
                    } else {
                        val wrong = result.exceptionOrNull() is ir.mohitban.app.data.backup.BackupWrongPassword
                        Toast.makeText(context, if (wrong) "رمز اشتباه است یا فایل آسیب دیده است" else "فایل پشتیبان معتبر نیست", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}

private fun backupFileName() = "mohityar-backup-${JalaliDate.today().format().replace('/', '-')}.zip"

@Composable
private fun PasswordDialog(
    title: String, text: String, confirm: String, minLength: Int, secondary: String?,
    onSecondary: () -> Unit, onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MColors.Muted)
                androidx.compose.material3.OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (pw.isNotEmpty() && pw.length < minLength) {
                    Text("حداقل ${minLength.fa()} نویسه", style = MaterialTheme.typography.labelSmall, color = MColors.Error, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(pw) }, enabled = pw.length >= minLength) { Text(confirm) }
        },
        dismissButton = {
            Row {
                if (secondary != null) androidx.compose.material3.TextButton(onClick = onSecondary) { Text(secondary) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
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
