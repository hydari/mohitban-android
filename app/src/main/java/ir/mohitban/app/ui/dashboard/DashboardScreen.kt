package ir.mohitban.app.ui.dashboard

import android.Manifest
import ir.mohitban.app.ui.theme.Corners
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
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
import ir.mohitban.app.ui.alerts.Alerts
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.BottomBar
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.GlassBackdrop
import ir.mohitban.app.ui.components.GlassCard
import androidx.compose.foundation.layout.width
import ir.mohitban.app.ui.components.GlassRing
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import ir.mohitban.app.ui.components.Developer
import ir.mohitban.app.ui.components.DeveloperDialog
import ir.mohitban.app.ui.components.glossSweep
import ir.mohitban.app.domain.model.ShiftStatus
import ir.mohitban.app.ui.shift.ShiftColors
import ir.mohitban.app.ui.shift.ShiftText
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.navigationBarsPadding

data class DashboardState(
    val profile: RangerProfile? = null,
    val vehicle: Vehicle? = null,
    val vehicleCount: Int = 0,
    val seasonEntries: List<FuelEntry> = emptyList(),
    val due: ReportReminder.Due? = null,
    val lastBackupAt: Long? = null,
    val reportedKey: Int? = null,
    val shift: ir.mohitban.app.domain.model.ShiftPattern? = null,
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
        settings.shift,
    ) { pvd, e, reminder, backup, shift ->
        val (p, vs, defaultId) = pvd
        DashboardState(
            profile = p,
            vehicle = vs.firstOrNull { it.id == defaultId } ?: vs.firstOrNull(),
            vehicleCount = vs.size,
            seasonEntries = e,
            due = ReportReminder.due(today, reminder.reportedSeasonKey),
            lastBackupAt = backup,
            reportedKey = reminder.reportedSeasonKey,
            shift = shift,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

@Composable
fun DashboardScreen(
    onFuel: () -> Unit,
    onRecords: () -> Unit,
    onReport: () -> Unit,
    onVehicle: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onAlerts: () -> Unit,
    onShift: () -> Unit,
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
    val alerts = Alerts.build(vm.today, profile?.fullName.orEmpty(), state.reportedKey, state.lastBackupAt)
    val urgentCount = alerts.count { it.urgent }
    val shiftStatus = state.shift?.status(vm.today.jdn)

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
                // ---------- organisation: DoE logo + name plates, no background ----------
                val province = profile?.province.orEmpty().trim().removePrefix("استان").trim()
                Column(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ShimmerLogo(size = 66.dp, modifier = Modifier.padding(bottom = 4.dp))
                    OrgPlate("سازمان حفاظت محیط زیست", 9.5.sp)
                    if (province.isNotEmpty()) OrgPlate("اداره کل حفاظت محیط زیست استان $province", 8.5.sp)
                }

                // ---------- personal card (liquid glass): photo | name, rank · station | bell ----------
                GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), dark = true) {
                    GlassRing(
                        70.dp, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), onClick = onProfile,
                        rim = shiftStatus?.let { if (it is ShiftStatus.OnDuty) ShiftColors.On else ShiftColors.Off },
                    ) {
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFD9CFB4), Color(0xFFA68A5F)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            val p = photo
                            if (p != null) {
                                Image(p, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            } else {
                                Text(initials(profile?.fullName), style = MaterialTheme.typography.titleLarge, color = Color(0xFF4B3517))
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            profile?.fullName ?: "",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 8f),
                            ),
                            color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val rank = profile?.rank?.takeIf { it.isNotBlank() }
                        val station = profile?.station?.takeIf { it.isNotBlank() }
                        if (rank != null || station != null) {
                            // rank and station on one line, each icon on its right side
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                                if (rank != null) {
                                    VerifiedBadge()
                                    Text(
                                        " $rank", style = MaterialTheme.typography.bodySmall, color = Color(0xF2FFFFFF),
                                        maxLines = 1,
                                    )
                                }
                                if (rank != null && station != null) Spacer(Modifier.width(10.dp))
                                if (station != null) {
                                    CircleIcon(Icons.Outlined.LocationOn, Color(0xFFE5483B), Color.White, size = 17.dp, iconSize = 11.dp)
                                    Text(
                                        " پاسگاه $station", style = MaterialTheme.typography.bodySmall, color = Color(0xE6FFFFFF),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                    }
                    MinimalBell(ringing = urgentCount > 0, count = urgentCount, onClick = onAlerts, modifier = Modifier.padding(end = 8.dp))
                }

                // ---------- today's shift ----------
                val onDuty = shiftStatus is ShiftStatus.OnDuty
                GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp), dark = true, onClick = onShift) {
                    CircleIcon(
                        when {
                            shiftStatus == null -> Icons.Outlined.EventRepeat
                            onDuty -> Icons.Outlined.Shield
                            else -> Icons.Outlined.Bedtime
                        },
                        if (shiftStatus == null || onDuty) ShiftColors.On else ShiftColors.Off, Color.White,
                        size = 42.dp, iconSize = 21.dp, modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            if (shiftStatus == null) "شیفت کاری" else ShiftText.title(shiftStatus),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (onDuty) Color(0xFF7CF0A6) else Color.White,
                        )
                        Text(
                            if (shiftStatus == null) "الگوی شیفت خود را ثبت کنید" else ShiftText.detail(shiftStatus),
                            style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (shiftStatus != null) {
                        // status pill: green and lit when on duty, grey and "off" when resting
                        Box(
                            Modifier
                                .padding(end = 14.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (onDuty) ShiftColors.On else Color(0x40FFFFFF))
                                .border(1.dp, if (onDuty) Color(0x99FFFFFF) else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                if (onDuty) "شیفت" else "استراحت",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (onDuty) Color.White else Color(0xB3FFFFFF),
                            )
                        }
                    }
                }

                // ---------- main action ----------
                GlassCard(Modifier.fillMaxWidth().padding(top = 22.dp), radius = Corners.R, onClick = onFuel) {
                    CircleIcon(Icons.Outlined.LocalGasStation, Color(0xFF239B56), Color.White, size = 70.dp, iconSize = 34.dp, modifier = Modifier.padding(14.dp))
                    Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text("ثبت هزینه سوخت", style = MaterialTheme.typography.titleLarge, color = MColors.Ink)
                        Text("بنزین یا گاز، رسید و امضا", style = MaterialTheme.typography.bodySmall, color = Color(0xFF3E4A44), modifier = Modifier.padding(top = 4.dp))
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
                            Color(0xFF2F7FC1), Color.White, size = 40.dp, iconSize = 21.dp, modifier = Modifier.padding(start = 14.dp),
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
                    QuickTile(Icons.AutoMirrored.Outlined.FormatListBulleted, "سوابق سوخت", "${state.seasonEntries.size.fa()} ثبت در ${vm.season.title}", Modifier.weight(1f), onRecords, Color(0xFF2F7FC1))
                    QuickTile(Icons.Outlined.Assessment, "گزارش فصلی", "${vm.season.title} ${vm.year.fa()}", Modifier.weight(1f), onReport, Color(0xFFE08A1E))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(Icons.Outlined.DirectionsCar, "وسایل نقلیه", "${state.vehicleCount.fa()} وسیله | پلاک‌ها", Modifier.weight(1f), onVehicle, Color(0xFF1F9A8A))
                    QuickTile(Icons.Outlined.Settings, "تنظیمات", "نرخ‌ها و پشتیبان", Modifier.weight(1f), onSettings, Color(0xFF5F6F7A))
                }

                // ---------- alerts entry (details in their own page) ----------
                GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), dark = true, onClick = onAlerts) {
                    CircleIcon(
                        Icons.Outlined.NotificationsActive, Color(0xFFD99A1E), Color.White, size = 42.dp, iconSize = 21.dp,
                        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    )
                    Text(
                        "اعلان‌ها و یادآوری‌ها", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    if (alerts.isNotEmpty()) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape)
                                .background(if (urgentCount > 0) Color(0xFFE5483B) else Color(0x40FFFFFF)),
                            contentAlignment = Alignment.Center,
                        ) { Text(alerts.size.fa(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White,
                        modifier = Modifier.padding(start = 8.dp, end = 14.dp).size(20.dp),
                    )
                }

                // ---------- season summary ----------
                val entries = state.seasonEntries
                if (entries.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), dark = true, onClick = onReport) {
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
                var showDev by remember { mutableStateOf(false) }
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(Corners.R)).clickable { showDev = true }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "طراح و برنامه‌نویس: ${Developer.NAME}",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xE6FFFFFF), textAlign = TextAlign.Center,
                    )
                    Text(
                        "محیط‌یار نسخه ${ir.mohitban.app.BuildConfigCompat.versionName(context).fa()}",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xB3FFFFFF), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (showDev) DeveloperDialog(onDismiss = { showDev = false })
                Spacer(Modifier.height(124.dp))
            }
            BottomBar(MainTab.HOME, onTab, Modifier.align(Alignment.BottomCenter))
            // the dashboard is the root screen: back twice within 2 s to leave the app
            var lastBack by remember { mutableStateOf(0L) }
            var exitHint by remember { mutableStateOf(false) }
            androidx.activity.compose.BackHandler {
                val now = android.os.SystemClock.elapsedRealtime()
                if (exitHint && now - lastBack < 2000) context.findActivity()?.finish()
                else { lastBack = now; exitHint = true }
            }
            LaunchedEffect(lastBack) {
                if (exitHint) { kotlinx.coroutines.delay(2000); exitHint = false }
            }
            if (exitHint) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 104.dp, start = 24.dp, end = 24.dp)
                        .shadow(10.dp, RoundedCornerShape(Corners.R))
                        .clip(RoundedCornerShape(Corners.R))
                        .background(Color(0xF21A2620))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(Corners.R))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = Color(0xFFFFC857), modifier = Modifier.size(20.dp))
                    Text(
                        "  برای خروج از برنامه، دوبار دکمه بازگشت را بزنید",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    )
                }
            }
        }
    }
}

private fun initials(name: String?): String {
    if (name.isNullOrBlank()) return ""
    return name.trim().split(" ", "‌").filter { it.isNotBlank() }.take(2).joinToString(" ") { it.first().toString() }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit, color: Color) {
    GlassCard(modifier.height(86.dp), dark = true, radius = Corners.R, onClick = onClick) {
        CircleIcon(icon, color, Color.White, size = 44.dp, iconSize = 22.dp, modifier = Modifier.padding(start = 12.dp))
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

/** Small green "verified" seal with a white check. */
@Composable
private fun VerifiedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(16.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF4CD07D), Color(0xFF1E8C47)))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Check, "تأیید شده", tint = Color.White, modifier = Modifier.size(11.dp)) }
}

/** White glossy plate with green text, used for the organisation names under the logo. */
@Composable
private fun OrgPlate(text: String, size: androidx.compose.ui.unit.TextUnit) {
    Box(
        Modifier
            .shadow(4.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEFF6F1))))
            .border(1.dp, Color(0xFFCFE3D5), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text, color = MColors.Green, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = size, fontWeight = FontWeight.Bold),
        )
    }
}

/** A simple white outline bell that swings when something needs attention; red dot shows the count. */
@Composable
private fun MinimalBell(ringing: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bell")
    val swing by t.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 2600
                0f at 0
                -18f at 120
                14f at 260
                -10f at 400
                6f at 540
                0f at 680
                0f at 2600
            },
        ),
        label = "swing",
    )
    Box(modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.NotificationsNone, "اعلان‌ها", tint = Color.White,
            modifier = Modifier
                .size(27.dp)
                .graphicsLayer {
                    rotationZ = if (ringing) swing else 0f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f)
                },
        )
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(15.dp).clip(CircleShape)
                    .background(Color(0xFFE5483B)).border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.fa(), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp))
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
