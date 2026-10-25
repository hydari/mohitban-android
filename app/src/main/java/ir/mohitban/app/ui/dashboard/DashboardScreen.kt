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
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.R
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.amountText
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.domain.model.VehicleType
import ir.mohitban.app.reminder.ReportReminder
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.BottomBar
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.GlassCard
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.components.PlateView
import ir.mohitban.app.ui.components.rememberStoredImage
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val profile: RangerProfile? = null,
    val vehicle: Vehicle? = null,
    val vehicleCount: Int = 0,
    val seasonEntries: List<FuelEntry> = emptyList(),
    val due: ReportReminder.Due? = null,
)

class DashboardViewModel(profiles: ProfileRepository, fuel: FuelRepository, settings: SettingsRepository) : ViewModel() {
    private val today = JalaliDate.today()
    val season = today.season
    val year = today.year
    val state: StateFlow<DashboardState> = combine(
        profiles.profile,
        profiles.vehicles,
        settings.defaultVehicleId,
        fuel.observeSeason(today.year, today.season),
        settings.reminder,
    ) { p, vs, defaultId, e, reminder ->
        DashboardState(
            profile = p,
            vehicle = vs.firstOrNull { it.id == defaultId } ?: vs.firstOrNull(),
            vehicleCount = vs.size,
            seasonEntries = e,
            due = ReportReminder.due(today, reminder.reportedSeasonKey),
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
    onTab: (MainTab) -> Unit,
) {
    val vm = appViewModel { c, _ -> DashboardViewModel(c.profileRepository, c.fuelRepository, c.settingsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
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

    Box(Modifier.fillMaxSize().background(MColors.GreenDark)) {
        Image(painterResource(R.drawable.bg_forest), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x8008241A),
                    0.3f to Color(0x1F08241A),
                    0.6f to Color(0x0D08241A),
                    1f to Color(0x8C06180C),
                ),
            ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Menu, "منو", tint = Color.White, modifier = Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onSettings))
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).clickable(onClick = onReport).padding(4.dp)) {
                    Icon(Icons.Outlined.NotificationsNone, "اعلان‌ها", tint = Color.White, modifier = Modifier.size(25.dp))
                    if (state.due != null) {
                        Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(Color(0xFFF2B233)))
                    }
                }
            }
            // ---------- profile header ----------
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.doe_emblem_white), null, modifier = Modifier.size(46.dp).alpha(0.55f))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile?.fullName ?: "", style = MaterialTheme.typography.headlineSmall, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Outlined.Eco, null, tint = MColors.Leaf, modifier = Modifier.size(19.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Outlined.Park, null, tint = Color(0xD9FFFFFF), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(5.dp))
                        Text(profile?.rank ?: "", style = MaterialTheme.typography.bodySmall, color = Color(0xD9FFFFFF))
                    }
                }
                Box(
                    Modifier
                        .size(78.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .border(3.dp, Color(0xFF6CCB8A), CircleShape)
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
            }
            // ---------- quarterly report reminder ----------
            state.due?.let { d ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (d.overdue) Color(0xF2B23A2E) else Color(0xF2C98A1B))
                        .clickable(onClick = onReport)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(ReportReminder.title(d), style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(ReportReminder.message(profile?.fullName.orEmpty(), d), style = MaterialTheme.typography.labelSmall, color = Color(0xF2FFFFFF))
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
                }
            }
            // ---------- station ----------
            GlassCard(Modifier.fillMaxWidth().padding(top = 20.dp), onClick = onProfile) {
                Image(
                    painterResource(R.drawable.station_thumb), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(12.dp).size(width = 82.dp, height = 64.dp).clip(RoundedCornerShape(14.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text("پاسگاه محیط‌بانی ${profile?.station ?: ""}", style = MaterialTheme.typography.titleSmall, color = MColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "منطقه حفاظت‌شده ${profile?.protectedArea ?: ""}",
                        style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.LocationOn, null, tint = MColors.Green, modifier = Modifier.padding(horizontal = 14.dp))
            }
            // ---------- fuel (main action) ----------
            GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), onClick = onFuel) {
                Box(
                    Modifier
                        .padding(14.dp)
                        .size(68.dp)
                        .shadow(10.dp, CircleShape, spotColor = MColors.Green)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MColors.GreenBright, Color(0xFF1C6F3A)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.LocalGasStation, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    Text("هزینه سوخت", style = MaterialTheme.typography.titleLarge, color = MColors.Ink)
                    Text("ثبت و مشاهده هزینه‌های سوخت", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A5650), modifier = Modifier.padding(top = 4.dp))
                    Text("فرم رسمی، رسید و امضا", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A5650))
                }
                CircleIcon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, MColors.Green.copy(alpha = 0.12f), MColors.Green,
                    size = 32.dp, iconSize = 18.dp, modifier = Modifier.padding(end = 14.dp),
                )
            }
            // ---------- vehicle strip ----------
            state.vehicle?.let { v ->
                GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), dark = true, onClick = onVehicle) {
                    CircleIcon(
                        if (v.type == VehicleType.MOTORCYCLE) Icons.Outlined.TwoWheeler else Icons.Outlined.DirectionsCar,
                        Color(0x33FFFFFF), Color.White, size = 38.dp, iconSize = 20.dp, modifier = Modifier.padding(start = 12.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp, top = 12.dp, bottom = 12.dp)) {
                        Text(v.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "آخرین کیلومتر: ${v.odometer.rial()}",
                            style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    PlateView(v.plate, height = 34.dp, modifier = Modifier.padding(end = 14.dp))
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
            // ---------- season summary ----------
            val entries = state.seasonEntries
            if (entries.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), dark = true) {
                    Column(Modifier.padding(16.dp).weight(1f)) {
                        Text("جمع ${vm.season.title} ${vm.year.fa()}", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF))
                        Text(
                            "${entries.sumOf { it.total }.rial()} ریال",
                            style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(top = 2.dp),
                        )
                        val g = entries.filter { it.fuelType == ir.mohitban.app.domain.model.FuelType.GASOLINE }.sumOf { it.amount }
                        val c = entries.filter { it.fuelType == ir.mohitban.app.domain.model.FuelType.CNG }.sumOf { it.amount }
                        Text(
                            "بنزین ${g.amountText()} لیتر  |  گاز ${c.amountText()} کیلوگرم",
                            style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            // ---------- motto ----------
            Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "طراح و برنامه‌نویس: مهندس محمدرضا حیدری",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xE6FFFFFF),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(120.dp))
        }
        BottomBar(MainTab.HOME, onTab, Modifier.align(Alignment.BottomCenter))
    }
}

private fun initials(name: String?): String {
    if (name.isNullOrBlank()) return ""
    return name.trim().split(" ", "‌").filter { it.isNotBlank() }.take(2).joinToString(" ") { it.first().toString() }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.height(84.dp), dark = true, radius = 20.dp, onClick = onClick) {
        CircleIcon(icon, Color(0xEBFFFFFF), MColors.Green, size = 42.dp, modifier = Modifier.padding(start = 12.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xBFFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun Motto(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(icon, null, tint = MColors.Leaf, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xF2FFFFFF))
    }
}

/** Runs the reminder check right after the permission was granted. */
private fun scopeLaunchCheck(context: Context) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { ReportReminder.check(context.applicationContext) }
}
