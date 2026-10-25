package ir.mohitban.app.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.R
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.amountText
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.FuelType
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.model.VehicleType
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.reminder.ReportReminder
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.BottomBar
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.GlassBackdrop
import ir.mohitban.app.ui.components.GlassCard
import ir.mohitban.app.ui.components.LocalGlassBackdrop
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.components.PlateView
import ir.mohitban.app.ui.components.ShimmerLogo
import ir.mohitban.app.ui.components.SkeuoBell
import ir.mohitban.app.ui.components.rememberStoredImage
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardState(
    val profile: RangerProfile? = null,
    val vehicle: Vehicle? = null,
    val vehicleCount: Int = 0,
    val seasonEntries: List<FuelEntry> = emptyList(),
    val due: ReportReminder.Due? = null,
    val lastBackupAt: Long? = null,
)

class DashboardViewModel(profiles: ProfileRepository, fuel: FuelRepository, settings: SettingsRepository) : ViewModel() {
    val today = JalaliDate.today()
    val season = today.season
    val year = today.year
    val state: StateFlow<DashboardState> = combine(
        combine(profiles.profile, profiles.vehicles, settings.defaultVehicleId) { p, vs, d -> Triple(p, vs, d) },
        fuel.observeSeason(today.year, today.season),
        settings.reminder,
        settings.lastBackupAt,
    ) { pvd, e, reminder, backup ->
        val (p, vs, defaultId) = pvd
        DashboardState(
            profile = p,
            vehicle = vs.firstOrNull { it.id == defaultId } ?: vs.firstOrNull(),
            vehicleCount = vs.size,
            seasonEntries = e,
            due = ReportReminder.due(today, reminder.reportedSeasonKey),
            lastBackupAt = backup,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

/** One line in the alerts section. */
private class Alert(val icon: ImageVector, val title: String, val text: String, val color: Color, val urgent: Boolean, val onClick: () -> Unit)

@Composable
fun DashboardScreen(
    onFuel: () -> Unit,
    onRecords: () -> Unit,
    onReport: () -> Unit,
    onVehicle: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onTab: (MainTab) -> Unit,
) {
    val vm = appViewModel { c, _ -> DashboardViewModel(c.profileRepository, c.fuelRepository, c.settingsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var alertsY by remember { mutableFloatStateOf(0f) }

    // Android 13+: ask once for permission to show the quarterly-report reminder on the lock screen
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scopeLaunchCheck(context)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ReportReminder.check(context.applicationContext)
        }
    }

    val profile = state.profile
    val photo by rememberStoredImage(profile?.photoPath, 300)
    val alerts = buildAlerts(state, vm, onReport, onSettings)
    val urgentCount = alerts.count { it.urgent }

    val blurred = ImageBitmap.imageResource(R.drawable.bg_forest_blur)
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxSize().background(MColors.GreenDark).onSizeChanged { rootSize = it }) {
        Image(painterResource(R.drawable.bg_forest), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xB3061A10),
                    0.3f to Color(0x33061A10),
                    0.62f to Color(0x26061A10),
                    1f to Color(0xB3041209),
                ),
            ),
        )
        CompositionLocalProvider(LocalGlassBackdrop provides GlassBackdrop(blurred, rootSize)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scroll)
                    .padding(horizontal = 18.dp),
            ) {
                // ---------- welcome + skeuomorphic bell ----------
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${greeting()}، ${firstName(profile?.fullName)} عزیز",
                            style = MaterialTheme.typography.titleMedium, color = Color.White,
                        )
                        Text(
                            "به محیط‌یار خوش آمدید | ${vm.today.formatLong().fa()}",
                            style = MaterialTheme.typography.labelSmall, color = Color(0xD9FFFFFF), modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    SkeuoBell(
                        ringing = urgentCount > 0, count = urgentCount,
                        onClick = { scope.launch { scroll.animateScrollTo(alertsY.toInt()) } },
                    )
                }

                // ---------- profile header: photo (right), name, rank | station, DoE logo (left) ----------
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(74.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .border(3.dp, Brush.linearGradient(listOf(Color(0xFFB7F5C9), Color(0xFF3FA866))), CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFD9CFB4), Color(0xFFA68A5F))))
                            .clickable(onClick = onProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        val p = photo
                        if (p != null) {
                            Image(p, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        } else {
                            Text(initials(profile?.fullName), style = MaterialTheme.typography.titleLarge, color = Color(0xFF4B3517))
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            profile?.fullName ?: "", style = MaterialTheme.typography.headlineSmall, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                profile?.rank?.takeIf { it.isNotBlank() },
                                profile?.station?.takeIf { it.isNotBlank() }?.let { "پاسگاه محیط‌بانی $it" },
                            ).joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xE6FFFFFF),
                            modifier = Modifier.padding(top = 3.dp), maxLines = 2,
                        )
                        profile?.protectedArea?.takeIf { it.isNotBlank() }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(Icons.Outlined.LocationOn, null, tint = MColors.Leaf, modifier = Modifier.size(14.dp))
                                Text(" منطقه حفاظت‌شده $it", style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF))
                            }
                        }
                    }
                    ShimmerLogo(size = 62.dp)
                }

                // ---------- main action ----------
                GlassCard(Modifier.fillMaxWidth().padding(top = 22.dp), radius = 28.dp, onClick = onFuel) {
                    Box(
                        Modifier
                            .padding(14.dp)
                            .size(70.dp)
                            .shadow(14.dp, CircleShape, spotColor = MColors.Green)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF45C173), MColors.GreenBright, Color(0xFF16592F))))
                            .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x1AFFFFFF))), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.LocalGasStation, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                    Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text("ثبت هزینه سوخت", style = MaterialTheme.typography.titleLarge, color = MColors.Ink)
                        Text("بنزین یا گاز، رسید و امضا", style = MaterialTheme.typography.bodySmall, color = Color(0xFF3E4A44), modifier = Modifier.padding(top = 4.dp))
                        Text("فرم رسمی آماده ارسال", style = MaterialTheme.typography.bodySmall, color = Color(0xFF3E4A44))
                    }
                    CircleIcon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, MColors.Green.copy(alpha = 0.14f), MColors.Green,
                        size = 34.dp, iconSize = 19.dp, modifier = Modifier.padding(end = 16.dp),
                    )
                }

                // ---------- default vehicle ----------
                state.vehicle?.let { v ->
                    GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), dark = true, onClick = onVehicle) {
                        CircleIcon(
                            if (v.type == VehicleType.MOTORCYCLE) Icons.Outlined.TwoWheeler else Icons.Outlined.DirectionsCar,
                            Color(0x33FFFFFF), Color.White, size = 40.dp, iconSize = 21.dp, modifier = Modifier.padding(start = 14.dp),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp)) {
                            Text(v.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "آخرین کیلومتر: ${v.odometer.rial()}",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Box(Modifier.padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                            PlateView(v.plate, height = 34.dp)
                        }
                    }
                }

                // ---------- quick tiles ----------
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(Icons.AutoMirrored.Outlined.FormatListBulleted, "سوابق سوخت", "${state.seasonEntries.size.fa()} ثبت در ${vm.season.title}", Modifier.weight(1f), onRecords)
                    QuickTile(Icons.Outlined.Assessment, "گزارش فصلی", "${vm.season.title} ${vm.year.fa()}", Modifier.weight(1f), onReport)
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(Icons.Outlined.DirectionsCar, "وسایل نقلیه", "${state.vehicleCount.fa()} وسیله | پلاک‌ها", Modifier.weight(1f), onVehicle)
                    QuickTile(Icons.Outlined.Settings, "تنظیمات", "نرخ‌ها و پشتیبان", Modifier.weight(1f), onSettings)
                }

                // ---------- alerts section ----------
                Column(Modifier.padding(top = 18.dp).onGloballyPositioned { alertsY = it.positionInParent().y }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = Color(0xFFFFD36B), modifier = Modifier.size(20.dp))
                        Text(" اعلان‌ها و یادآوری‌ها", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    GlassCard(Modifier.fillMaxWidth(), dark = true) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            alerts.forEachIndexed { i, a ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(onClick = a.onClick)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape).background(a.color.copy(alpha = 0.22f))
                                            .border(1.dp, a.color.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(a.icon, null, tint = a.color, modifier = Modifier.size(21.dp)) }
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(a.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text(a.text, style = MaterialTheme.typography.labelSmall, color = Color(0xD9FFFFFF), modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(18.dp))
                                }
                                if (i < alerts.lastIndex) {
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0x26FFFFFF)))
                                }
                            }
                        }
                    }
                }

                // ---------- season summary ----------
                val entries = state.seasonEntries
                if (entries.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), dark = true) {
                        Column(Modifier.padding(16.dp).weight(1f)) {
                            Text("جمع ${vm.season.title} ${vm.year.fa()}", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF))
                            Text("${entries.sumOf { it.total }.rial()} ریال", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(top = 2.dp))
                            val g = entries.filter { it.fuelType == FuelType.GASOLINE }.sumOf { it.amount }
                            val c = entries.filter { it.fuelType == FuelType.CNG }.sumOf { it.amount }
                            Text(
                                "بنزین ${g.amountText()} لیتر  |  گاز ${c.amountText()} کیلوگرم",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                // ---------- credit ----------
                Text(
                    "طراح و برنامه‌نویس: مهندس محمدرضا حیدری",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xE6FFFFFF),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
                Spacer(Modifier.height(124.dp))
            }
            BottomBar(MainTab.HOME, onTab, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Reminders shown in the alerts section (the bell counts the urgent ones). */
private fun buildAlerts(state: DashboardState, vm: DashboardViewModel, onReport: () -> Unit, onSettings: () -> Unit): List<Alert> {
    val list = mutableListOf<Alert>()
    val name = state.profile?.fullName.orEmpty()
    state.due?.let { d ->
        list += Alert(
            Icons.Outlined.NotificationsActive, ReportReminder.title(d), ReportReminder.message(name, d),
            if (d.overdue) Color(0xFFFF8A80) else Color(0xFFFFD36B), urgent = true, onClick = onReport,
        )
    }
    if (state.due == null) {
        val range = vm.season.jdnRange(vm.year)
        val left = range.last - vm.today.jdn
        val end = JalaliDate.fromJdn(range.last)
        list += Alert(
            Icons.Outlined.Event, "مهلت گزارش ${vm.season.title} ${vm.year.fa()}",
            "تا ${end.day.fa()} ${Jalali.MONTH_NAMES[end.month - 1]} | ${left.fa()} روز مانده است",
            Color(0xFF9BF0B6), urgent = false, onClick = onReport,
        )
    }
    val backup = state.lastBackupAt
    if (backup == null || System.currentTimeMillis() - backup > 14L * 24 * 3600 * 1000) {
        list += Alert(
            Icons.Outlined.Backup, "پشتیبان‌گیری",
            if (backup == null) "هنوز از اطلاعات پشتیبان نگرفته‌اید" else "بیش از دو هفته از آخرین پشتیبان گذشته است",
            Color(0xFF8FD3FF), urgent = false, onClick = onSettings,
        )
    }
    return list
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..11 -> "صبح بخیر"
    in 12..16 -> "ظهر بخیر"
    in 17..19 -> "عصر بخیر"
    else -> "شب بخیر"
}

private fun firstName(full: String?): String = full?.trim()?.split(" ")?.firstOrNull().orEmpty()

private fun initials(name: String?): String {
    if (name.isNullOrBlank()) return ""
    return name.trim().split(" ", "‌").filter { it.isNotBlank() }.take(2).joinToString(" ") { it.first().toString() }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.height(86.dp), dark = true, radius = 22.dp, onClick = onClick) {
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6F2E9)))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MColors.Green, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Runs the reminder check right after the permission was granted. */
private fun scopeLaunchCheck(context: Context) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { ReportReminder.check(context.applicationContext) }
}
