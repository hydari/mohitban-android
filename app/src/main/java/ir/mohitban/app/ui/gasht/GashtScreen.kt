package ir.mohitban.app.ui.gasht

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.GeomagneticField
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.AppContainer
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.data.local.TrackEntity
import ir.mohitban.app.data.local.WaypointEntity
import ir.mohitban.app.gasht.Compass
import ir.mohitban.app.gasht.CoordFormat
import ir.mohitban.app.gasht.Fix
import ir.mohitban.app.gasht.GashtSession
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.gasht.GpsFiles
import ir.mohitban.app.gasht.GpsLocation
import ir.mohitban.app.gasht.GuideTarget
import ir.mohitban.app.gasht.TrackSource
import ir.mohitban.app.gasht.geo
import ir.mohitban.app.ui.common.Share
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.theme.Corners
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ir.mohitban.app.ui.components.clay
import ir.mohitban.app.ui.components.clayInset
import androidx.compose.animation.core.animateFloat

// ============================================================================ view-model

@OptIn(ExperimentalCoroutinesApi::class)
class GashtViewModel(private val c: AppContainer) : ViewModel() {
    private val ctx = c.appContext
    private val repo = c.gasht

    private val permission = MutableStateFlow(GpsLocation.hasPermission(ctx))
    val hasPermission: StateFlow<Boolean> = permission
    fun refreshPermission() { permission.value = GpsLocation.hasPermission(ctx) }

    val fix: StateFlow<Fix?> = permission.flatMapLatest { if (it) GpsLocation.updates(ctx) else emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), null)
    val satellites: StateFlow<Pair<Int, Int>?> = permission.flatMapLatest { if (it) GpsLocation.satellites(ctx) else emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), null)
    val compass: StateFlow<Float?> = Compass.heading(ctx).map<Float, Float?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), null)

    val tracks: StateFlow<List<TrackEntity>> = repo.tracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val places: StateFlow<List<WaypointEntity>> = repo.waypoints.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val record = GashtSession.record
    val guide = GashtSession.guide
    val livePoints: StateFlow<List<GeoPoint>> = GashtSession.record.map { it.trackId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observePoints(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), emptyList())

    var tab by mutableStateOf(0)
    var format by mutableStateOf(0)
    var backwards by mutableStateOf(false)
    var shown by mutableStateOf(setOf<Long>())
    var fitKey by mutableLongStateOf(0L)

    // ---- offline maps ----
    var maps by mutableStateOf(ir.mohitban.app.gasht.MapStore.list(ctx))
    var activeMap by mutableStateOf<ir.mohitban.app.gasht.OfflineMap?>(null)
    var background by mutableStateOf<MapBackground>(GridBackground)
    var labels by mutableStateOf<List<ir.mohitban.app.gasht.MapLabel>>(emptyList())
    var mapBusy by mutableStateOf(false)

    // ---- protected-area boundaries (imported file, private to the phone) ----
    var zones by mutableStateOf<List<ir.mohitban.app.gasht.Zone>>(emptyList())
    var mapZones by mutableStateOf<List<MapZone>>(emptyList())
    var showZones by mutableStateOf(true)
    var fitPoints by mutableStateOf<List<GeoPoint>>(emptyList())

    private fun applyZones(z: List<ir.mohitban.app.gasht.Zone>) {
        zones = z
        mapZones = z.map { MapZone(it) }
    }

    init {
        viewModelScope.launch {
            applyZones(withContext(Dispatchers.IO) { ir.mohitban.app.gasht.ZoneStore.load(ctx) })
        }
    }

    fun importZones(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ir.mohitban.app.gasht.ZoneStore.import(ctx, uri) } }
            r.onSuccess { applyZones(it); showZones = true; onDone("${it.size.fa()} منطقه وارد شد") }
                .onFailure { onDone("در این فایل مرز منطقه‌ای پیدا نشد (فایل GeoJSON لازم است)") }
        }
    }

    fun deleteZones() {
        viewModelScope.launch {
            val z = withContext(Dispatchers.IO) { ir.mohitban.app.gasht.ZoneStore.delete(ctx); ir.mohitban.app.gasht.ZoneStore.load(ctx) }
            applyZones(z)
        }
    }

    fun showZone(z: ir.mohitban.app.gasht.Zone) {
        fitPoints = listOf(GeoPoint(z.south, z.west), GeoPoint(z.north, z.east))
        showZones = true
        fitKey++
        tab = 0
    }

    init { ir.mohitban.app.gasht.MapStore.active(ctx)?.let { openMap(it) } }

    private fun openMap(file: java.io.File?) {
        viewModelScope.launch {
            val old = activeMap
            val opened = withContext(Dispatchers.IO) {
                file?.let { f -> runCatching { ir.mohitban.app.gasht.OfflineMap(f) }.getOrNull()?.let { m -> m to m.labels() } }
            }
            activeMap = opened?.first
            labels = opened?.second.orEmpty()
            background = opened?.first?.let { TileBackground(it, viewModelScope) } ?: GridBackground
            ir.mohitban.app.gasht.MapStore.setActive(ctx, opened?.first?.file)
            fitKey++
            if (old != null && old !== opened?.first) withContext(Dispatchers.IO) { runCatching { old.close() } }
        }
    }

    fun selectMap(file: java.io.File?) = openMap(file)

    fun importMap(uri: Uri, onDone: (String) -> Unit) {
        mapBusy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                        val i = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cur.moveToFirst() && i >= 0) cur.getString(i) else null
                    } ?: "map.mbtiles"
                    ir.mohitban.app.gasht.MapStore.import(ctx, uri, name)
                }
            }
            mapBusy = false
            maps = ir.mohitban.app.gasht.MapStore.list(ctx)
            result.onSuccess { openMap(it); onDone("نقشه وارد شد و نمایش داده می‌شود") }
                .onFailure { onDone("این فایل نقشه‌ی معتبر MBTiles نیست") }
        }
    }

    fun deleteMap(file: java.io.File) {
        viewModelScope.launch {
            if (activeMap?.file?.path == file.path) openMap(null)
            withContext(Dispatchers.IO) { ir.mohitban.app.gasht.MapStore.delete(ctx, file) }
            maps = ir.mohitban.app.gasht.MapStore.list(ctx)
        }
    }

    override fun onCleared() {
        runCatching { activeMap?.close() }
    }
    val trackPoints = mutableStateMapOf<Long, List<GeoPoint>>()

    private suspend fun load(id: Long): List<GeoPoint> =
        trackPoints[id] ?: repo.points(id).also { trackPoints[id] = it }

    fun toggleShown(id: Long) {
        if (id in shown) { shown = shown - id; return }
        viewModelScope.launch { load(id); shown = shown + id; fitKey++ }
    }

    fun showOnly(id: Long) {
        viewModelScope.launch { load(id); shown = setOf(id); fitKey++; tab = 0 }
    }

    fun followTrack(t: TrackEntity) {
        viewModelScope.launch {
            val pts = load(t.id)
            if (pts.size < 2) return@launch
            GashtSession.follow(ctx, GuideTarget.Track(t.id, t.name, pts))
            shown = shown + t.id
            fitKey++
            tab = 0
        }
    }

    fun followPlace(w: WaypointEntity) {
        GashtSession.follow(ctx, GuideTarget.Place(w.name, w.geo()))
        tab = 0
    }

    fun stopFollowing() = GashtSession.stopFollowing(ctx)

    fun importFile(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                        val i = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cur.moveToFirst() && i >= 0) cur.getString(i) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("read")
                    val data = GpsFiles.parse(name, bytes)
                    if (data.isEmpty) null else repo.import(data)
                }.getOrNull()
            }
            onDone(
                when {
                    result == null -> "در این فایل مسیر یا نقطه‌ای پیدا نشد. قالب‌های قابل خواندن: GPX، KML، KMZ، PLT، WPT"
                    else -> "${result.first.fa()} مسیر و ${result.second.fa()} نقطه وارد شد"
                },
            )
        }
    }

    fun export(ids: List<Long>, places: Boolean, kml: Boolean, title: String, context: Context) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) { repo.export(ids, places, kml, title) }
            Share.file(context, f, if (kml) "application/vnd.google-earth.kml+xml" else "application/gpx+xml")
        }
    }

    fun rename(id: Long, name: String) = viewModelScope.launch { repo.rename(id, name.trim().ifEmpty { "مسیر" }) }

    fun delete(id: Long) = viewModelScope.launch {
        if (guide.value.target.let { it is GuideTarget.Track && it.trackId == id }) stopFollowing()
        repo.deleteTrack(id)
        shown = shown - id
        trackPoints.remove(id)
    }

    fun addPlace(w: WaypointEntity) = viewModelScope.launch { repo.addWaypoint(w) }
    fun deletePlace(id: Long) = viewModelScope.launch { repo.deleteWaypoint(id) }
}

// ============================================================================ helpers

/** Magnetic heading turned into true north using the Earth's field model at the ranger's position. */
fun trueHeading(magnetic: Float?, fix: Fix?): Float? {
    magnetic ?: return null
    val decl = fix?.let {
        GeomagneticField(it.point.lat.toFloat(), it.point.lon.toFloat(), (it.point.ele ?: 0.0).toFloat(), System.currentTimeMillis()).declination
    } ?: 0f
    return (magnetic + decl + 360f) % 360f
}

fun jalaliOf(millis: Long): JalaliDate {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return Jalali.fromGregorian(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

fun durationText(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h.fa()} ساعت و ${m.fa()} دقیقه" else "${m.fa()} دقیقه"
}

fun positionMessage(title: String, name: String, f: Fix): String {
    val p = f.point
    return buildString {
        append(title)
        if (name.isNotBlank()) append(" — $name")
        append("\n").append(CoordFormat.decimal(p))
        append("\nUTM: ").append(CoordFormat.utm(p))
        p.ele?.let { append("\nارتفاع: ${it.toInt().fa()} متر") }
        f.accuracy?.let { append("\nدقت: ±${it.toInt().fa()} متر") }
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = f.time }
        append("\nزمان: ${jalaliOf(f.time).format().fa()} ساعت ${String.format(java.util.Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)).fa()}")
        append("\nhttps://maps.google.com/?q=${String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon)}")
    }
}

fun shareText(context: Context, text: String) {
    runCatching {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(i, "ارسال موقعیت").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun copyText(context: Context, text: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("location", text))
        Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
    }
}

/** Latin text (coordinates) laid out left-to-right inside the RTL screen. */
@Composable
fun LtrText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium, color: Color = MColors.Ink) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(text, modifier, style = style, color = color, textAlign = TextAlign.Center)
    }
}

// ============================================================================ screen

@Composable
fun GashtScreen(onBack: () -> Unit, initialTab: Int = 0) {
    val vm = appViewModel(key = "gasht") { c, _ -> GashtViewModel(c) }
    val context = LocalContext.current
    val permitted by vm.hasPermission.collectAsStateWithLifecycle()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPermission() }
    fun requestPermission() {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        askPermission.launch(list.toTypedArray())
    }
    LaunchedEffect(Unit) { if (!permitted) requestPermission() }
    LaunchedEffect(initialTab) { if (initialTab > 0) vm.tab = initialTab }

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("گشت‌یار", onBack = onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("نقشه", "مسیرها", "نقاط", "دفتر گشت", "ابزارها").forEachIndexed { i, t ->
                val on = vm.tab == i
                Box(
                    Modifier.weight(1f).height(40.dp)
                        .clay(12.dp, color = if (on) MColors.Green2 else ir.mohitban.app.ui.components.Clay.Surface, depth = if (on) 6.dp else 3.dp)
                        .clickable { vm.tab = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (on) Color.White else MColors.Ink, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!permitted) {
            PermissionCard(onAllow = { requestPermission() }, onSettings = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            })
        }
        when (vm.tab) {
            0 -> MapTab(vm)
            1 -> TracksTab(vm)
            2 -> PlacesTab(vm)
            3 -> PatrolLogTab(vm)
            else -> ToolsTab(vm)
        }
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit, onSettings: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(Icons.Outlined.LocationOff, MColors.Error, Color.White, size = 40.dp, iconSize = 20.dp)
                Text(
                    "برای نمایش موقعیت و ضبط مسیر، اجازه‌ی دسترسی به موقعیت مکانی لازم است. موقعیت فقط از ماهواره‌ی GPS گرفته می‌شود و هیچ داده‌ای از گوشی خارج نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MColors.Ink, modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("اجازه دسترسی", onAllow, Modifier.weight(1f))
                ir.mohitban.app.ui.components.OutlinePill("تنظیمات برنامه", onSettings, Modifier.weight(1f))
            }
        }
    }
}

// ============================================================================ map tab

@Composable
private fun MapTab(vm: GashtViewModel) {
    val context = LocalContext.current
    val fix by vm.fix.collectAsStateWithLifecycle()
    val sats by vm.satellites.collectAsStateWithLifecycle()
    val compass by vm.compass.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val record by vm.record.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val live by vm.livePoints.collectAsStateWithLifecycle()
    val heading = trueHeading(compass, fix)
    val name = rememberRangerName()
    var addPlace by remember { mutableStateOf(false) }
    var mapsDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // GPS switched off: a red, pulsing warning that stays until it is turned on
        var gpsOn by remember { mutableStateOf(GpsLocation.isGpsOn(context)) }
        LaunchedEffect(Unit) { while (true) { gpsOn = GpsLocation.isGpsOn(context); delay(1500) } }
        if (!gpsOn) {
            val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "gps").animateFloat(
                0.85f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "gpsPulse",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clay(color = Color(0xFFD32F2F), depth = 10.dp)
                    .clickable { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.GpsOff, null, tint = Color.White, modifier = Modifier.size((30 * pulse).dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("موقعیت مکانی (GPS) گوشی خاموش است!", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = Color.White)
                    Text("بدون GPS موقعیت، ضبط مسیر و هشدارها کار نمی‌کنند.", style = MaterialTheme.typography.labelSmall, color = Color(0xF2FFFFFF))
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("روشن کن", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = Color(0xFFD32F2F)) }
            }
        }

        // ---- position ----
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Outlined.GpsFixed, Color(0xFF1E6FD9), Color.White, size = 30.dp, iconSize = 16.dp)
                    Text(" موقعیت من", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    Text(
                        listOf("درجه اعشاری", "درجه‌دقیقه‌ثانیه", "UTM")[vm.format] + "  ⇄",
                        style = MaterialTheme.typography.labelMedium, color = MColors.Green,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.format = (vm.format + 1) % 3 }.padding(6.dp),
                    )
                }
                val f = fix
                if (f == null) {
                    Text(
                        "در حال یافتن موقعیت… در فضای باز بمانید (اولین بار ممکن است چند دقیقه طول بکشد).",
                        style = MaterialTheme.typography.bodySmall, color = MColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LtrText(
                        when (vm.format) { 0 -> CoordFormat.decimal(f.point); 1 -> CoordFormat.dms(f.point); else -> CoordFormat.utm(f.point) },
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    )
                    val age = (System.currentTimeMillis() - f.time) / 1000
                    val info = listOfNotNull(
                        f.point.ele?.let { "ارتفاع ${it.toInt().fa()} متر" },
                        f.accuracy?.let { "دقت ±${it.toInt().fa()} متر" },
                        sats?.let { "ماهواره ${it.first.fa()} از ${it.second.fa()}" },
                    ).joinToString(" | ")
                    Text(info, style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp))
                    if (age > 30) Text("آخرین موقعیت ${durationText(age * 1000)} پیش", style = MaterialTheme.typography.labelSmall, color = MColors.Amber)
                    // province, county and nearest settlement, from the built-in OpenStreetMap data
                    val where by androidx.compose.runtime.produceState<ir.mohitban.app.gasht.Whereabouts.Result?>(null, (f.point.lat * 500).toInt(), (f.point.lon * 500).toInt()) {
                        value = withContext(Dispatchers.Default) { runCatching { ir.mohitban.app.gasht.Whereabouts.locate(context, f.point) }.getOrNull() }
                    }
                    where?.takeIf { !it.isEmpty }?.let { w ->
                        val parts = listOfNotNull(w.province?.let { "استان $it" }, w.county?.let { "شهرستان $it" })
                        if (parts.isNotEmpty()) {
                            Text(parts.joinToString(" | "), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
                        }
                        w.place?.let { pl ->
                            val d = w.placeDistance ?: 0.0
                            Text(
                                if (d < 700) "در ${pl.label}" else "${CoordFormat.meters(d).fa()} از ${pl.label}",
                                style = MaterialTheme.typography.labelMedium, color = MColors.Muted, textAlign = TextAlign.Center,
                            )
                        }
                    }
                    // which protected area the ranger is in
                    if (vm.zones.isNotEmpty()) {
                        val zs = remember(f.point, vm.zones) { ir.mohitban.app.gasht.ZoneStore.status(vm.zones, f.point) }
                        val inside = zs.inside
                        val text = when {
                            inside != null -> "داخل ${inside.title}" + (zs.distance?.let { " — ${CoordFormat.meters(it).fa()} تا مرز" } ?: "")
                            zs.nearest != null -> "خارج از مناطق — ${zs.nearest.title} در ${CoordFormat.meters(zs.distance ?: 0.0).fa()}"
                            else -> ""
                        }
                        if (text.isNotEmpty()) {
                            Text(
                                text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (inside != null) Color(ir.mohitban.app.gasht.ZoneTypes.color(inside.type)) else MColors.Muted,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Icons.Outlined.ContentCopy, "کپی", Modifier.weight(1f), enabled = f != null) {
                        f?.let { copyText(context, "${CoordFormat.decimal(it.point)}\nUTM ${CoordFormat.utm(it.point)}") }
                    }
                    SmallAction(Icons.Outlined.Share, "ارسال", Modifier.weight(1f), enabled = f != null) {
                        f?.let { shareText(context, positionMessage("موقعیت محیط‌بان", name, it)) }
                    }
                    SmallAction(Icons.Outlined.AddLocationAlt, "ثبت نقطه", Modifier.weight(1f), enabled = f != null) { addPlace = true }
                }
            }
        }

        if (vm.zones.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip(if (vm.showZones) "مرز مناطق: روشن" else "مرز مناطق: خاموش", vm.showZones, Modifier.weight(1f)) { vm.showZones = !vm.showZones }
            }
        }
        // ---- base map choice ----
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip("بدون نقشه", vm.activeMap == null, Modifier.weight(1f)) { vm.selectMap(null) }
            vm.maps.take(2).forEach { f ->
                val on = vm.activeMap?.file?.path == f.path
                ChoiceChip(if (on) vm.activeMap?.title?.substringBefore(" —")?.ifBlank { null } ?: f.nameWithoutExtension else f.nameWithoutExtension, on, Modifier.weight(1.3f)) { vm.selectMap(f) }
            }
            SmallAction(Icons.Outlined.Layers, "نقشه‌ها", Modifier.weight(0.9f)) { mapsDialog = true }
        }
        val am = vm.activeMap
        val fx = fix
        if (am != null && fx != null && !am.contains(fx.point)) {
            Text("شما خارج از محدوده‌ی این نقشه هستید.", style = MaterialTheme.typography.labelSmall, color = MColors.Amber, modifier = Modifier.padding(top = 4.dp))
        }

        // ---- canvas ----
        val target = guide.target
        val mapTracks = buildList {
            vm.shown.forEach { id ->
                val pts = vm.trackPoints[id] ?: return@forEach
                val isTarget = target is GuideTarget.Track && target.trackId == id
                val src = tracks.firstOrNull { it.id == id }?.source
                add(MapTrack(id, pts, if (isTarget) Color(0xFFE08A1E) else if (src == TrackSource.IMPORTED) Color(0xFF2F7FC1) else Color(0xFF239B56), if (isTarget) 9f else 6f, highlight = isTarget))
            }
            if (target is GuideTarget.Track && target.trackId !in vm.shown) add(MapTrack(target.trackId, target.points, Color(0xFFE08A1E), 9f, true))
            if (live.size > 1) add(MapTrack(-1, live, Color(0xFFC62828), 7f, true))
        }
        val mapPlaces = places.map { MapPlace(it.name, it.geo(), Color(0xFF7B4FB5), target is GuideTarget.Place && target.name == it.name) } +
            listOfNotNull((target as? GuideTarget.Place)?.let { t -> MapPlace(t.name, t.point, Color(0xFFE08A1E), true).takeIf { places.none { p -> p.name == t.name } } })
        TrackCanvas(
            mapTracks, mapPlaces, fix, heading,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(360.dp),
            fitKey = vm.fitKey,
            background = vm.background,
            labels = vm.labels,
            attribution = vm.activeMap?.attribution.orEmpty(),
            initialCenter = vm.activeMap?.center,
            zones = if (vm.showZones) vm.mapZones else emptyList(),
            fitPoints = vm.fitPoints,
        )
        if (vm.shown.isEmpty() && target == null && !record.active) {
            Text(
                "برای دیدن مسیرها از زبانه‌ی «مسیرها» گزینه‌ی نمایش را بزنید. با دو انگشت بزرگ‌نمایی کنید.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (target != null) GuidePanel(vm, fix, heading, Modifier.padding(top = 12.dp))
        RecordPanel(vm, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(30.dp))
    }

    if (mapsDialog) MapsDialog(vm, onDismiss = { mapsDialog = false })
    if (addPlace) {
        fix?.let { f -> AddPlaceDialog(vm, f.point, onDismiss = { addPlace = false }) } ?: run { addPlace = false }
    }
}

@Composable
fun SmallAction(icon: ImageVector, text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = MColors.Green, onClick: () -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .then(if (enabled) Modifier.clay(10.dp, depth = 3.dp) else Modifier.clayInset(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (enabled) color else MColors.Hint, modifier = Modifier.size(18.dp))
        Text(" $text", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (enabled) color else MColors.Hint)
    }
}

// ============================================================================ guidance

private data class GuideInfo(val aim: GeoPoint, val distance: Double, val offRoute: Double?, val remaining: Double?, val label: String)

private fun guideInfo(fix: Fix, target: GuideTarget, backwards: Boolean): GuideInfo {
    val here = fix.point
    return when (target) {
        is GuideTarget.Place -> GuideInfo(target.point, Geo.distance(here, target.point), null, null, "تا «${target.name}»")
        is GuideTarget.Track -> {
            val pts = if (backwards) target.points.asReversed() else target.points
            val n = Geo.nearestOnPath(here, pts) ?: return GuideInfo(pts.first(), Geo.distance(here, pts.first()), null, null, "")
            val total = Geo.pathLength(pts)
            if (n.distance > 40) {
                GuideInfo(n.point, n.distance, n.distance, total - n.along, "به سمت نزدیک‌ترین نقطه‌ی مسیر")
            } else {
                // look about 40 m ahead along the path in the chosen direction
                var need = 40.0
                var aim = pts.last()
                var prev = n.point
                for (i in n.segment + 1 until pts.size) {
                    val d = Geo.distance(prev, pts[i])
                    if (d >= need) { aim = pts[i]; break }
                    need -= d; prev = pts[i]
                }
                val left = total - n.along
                GuideInfo(aim, left, n.distance, left, if (backwards) "روی مسیر — بازگشت به نقطه‌ی شروع" else "روی مسیر — ادامه تا پایان")
            }
        }
    }
}

@Composable
private fun GuidePanel(vm: GashtViewModel, fix: Fix?, heading: Float?, modifier: Modifier) {
    val guide by vm.guide.collectAsStateWithLifecycle()
    val target = guide.target ?: return
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("راهنمای «${target.name}»", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { vm.stopFollowing() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "پایان راهنما", tint = MColors.Muted)
                }
            }
            if (fix == null) {
                Text("منتظر موقعیت GPS…", style = MaterialTheme.typography.bodySmall, color = MColors.Muted)
            } else {
                val info = guideInfo(fix, target, vm.backwards)
                val bearing = Geo.bearing(fix.point, info.aim).toFloat()
                val off = info.offRoute != null && info.offRoute > guide.thresholdMeters
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    GuideArrow(if (heading != null) bearing - heading else bearing, if (off) MColors.Error else Color(0xFFE08A1E), Modifier.size(104.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(info.label, style = MaterialTheme.typography.labelMedium, color = if (off) MColors.Error else MColors.Muted)
                        Text(CoordFormat.meters(info.distance).fa(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = MColors.Ink)
                        Text(
                            "جهت ${bearing.toInt().fa()}° ${Geo.direction(bearing.toDouble())}",
                            style = MaterialTheme.typography.labelMedium, color = MColors.Ink,
                        )
                        info.offRoute?.let {
                            Text(
                                "فاصله از مسیر: ${CoordFormat.meters(it).fa()}" + if (off) " — از مسیر خارج شده‌اید" else "",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (off) MColors.Error else MColors.Green,
                            )
                        }
                        if (heading == null) Text("قطب‌نما در دسترس نیست؛ فلش نسبت به شمال است.", style = MaterialTheme.typography.labelSmall, color = MColors.Amber)
                    }
                }
            }
            if (target is GuideTarget.Track) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("ادامه تا پایان مسیر", !vm.backwards, Modifier.weight(1f)) { vm.backwards = false }
                    ChoiceChip("بازگشت به شروع", vm.backwards, Modifier.weight(1f)) { vm.backwards = true }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("هشدار خروج از مسیر", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(
                        guide.alarm, { GashtSession.setAlarm(it, guide.thresholdMeters) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MColors.Green2),
                    )
                }
                if (guide.alarm) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 100, 200, 500).forEach { m ->
                            ChoiceChip("${m.fa()} متر", guide.thresholdMeters == m, Modifier.weight(1f)) { GashtSession.setAlarm(true, m) }
                        }
                    }
                    Text(
                        "اگر بیشتر از این فاصله از مسیر دور شوید، گوشی حتی با صفحه‌ی خاموش می‌لرزد و هشدار می‌دهد.",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(38.dp)
            .then(if (selected) Modifier.clayInset(10.dp, Color(0xFFDCEEE2)).border(1.5.dp, MColors.Green2, RoundedCornerShape(10.dp)) else Modifier.clay(10.dp, depth = 3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), color = if (selected) MColors.Green else MColors.Ink, maxLines = 1)
    }
}

// ============================================================================ recording

@Composable
private fun RecordPanel(vm: GashtViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val record by vm.record.collectAsStateWithLifecycle()
    val permitted by vm.hasPermission.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }
    if (!record.active) {
        // recording needs the phone's location (GPS) switched on first
        var gpsOn by remember { mutableStateOf(GpsLocation.isGpsOn(context)) }
        LaunchedEffect(Unit) { while (true) { gpsOn = GpsLocation.isGpsOn(context); delay(1500) } }
        PillButton(
            "شروع ضبط مسیر گشت", { if (GpsLocation.isGpsOn(context)) GashtSession.startRecording(context) else gpsOn = false }, modifier,
            icon = Icons.Outlined.FiberManualRecord, enabled = permitted && gpsOn,
        )
        if (permitted && !gpsOn) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(ir.mohitban.app.ui.theme.Corners.R))
                    .background(MColors.ErrorBg)
                    .clickable {
                        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.LocationOff, null, tint = MColors.Error, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        "برای ضبط مسیر، ابتدا موقعیت مکانی (GPS) گوشی را روشن کنید.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MColors.Error,
                    )
                    Text(
                        "برای باز کردن تنظیمات موقعیت مکانی اینجا را بزنید. بعد از روشن کردن، دکمه‌ی ضبط فعال می‌شود.",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Ink, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Text(
            "ضبط با خاموش شدن صفحه هم ادامه پیدا می‌کند. برای مصرف کمتر باتری، بعد از گشت آن را پایان دهید.",
            style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
        )
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(record.active) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ir.mohitban.app.ui.components.PulseDot(if (record.paused) MColors.Amber else MColors.Error, size = 12.dp, active = !record.paused)
                Text(
                    if (record.paused) "  ضبط متوقف موقت" else "  در حال ضبط مسیر",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Stat("مسافت", CoordFormat.meters(record.distance).fa(), Modifier.weight(1f))
                Stat("مدت", durationText(now - record.startedAt), Modifier.weight(1f))
                Stat("نقاط", record.points.fa(), Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.paused) SmallAction(Icons.Outlined.PlayArrow, "ادامه ضبط", Modifier.weight(1f)) { GashtSession.resumeRecording(context) }
                else SmallAction(Icons.Outlined.Pause, "توقف موقت", Modifier.weight(1f), color = MColors.Amber) { GashtSession.pauseRecording(context) }
                SmallAction(Icons.Outlined.Stop, "پایان و ذخیره", Modifier.weight(1f), color = MColors.Error) { confirmStop = true }
            }
        }
    }
    if (confirmStop) {
        ConfirmDialog(
            title = "پایان ضبط مسیر",
            text = "مسیر ذخیره می‌شود و در زبانه‌ی «مسیرها» قابل مشاهده و ارسال است. سپس «دفتر گشت» باز می‌شود تا این گشت را با یک لمس ثبت کنید.",
            confirm = "پایان و ذخیره",
            onConfirm = { GashtSession.stopRecording(context); confirmStop = false; vm.tab = 3 },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = MColors.Ink, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
    }
}

/** Ranger's name for messages (read once). */
@Composable
fun rememberRangerName(): String {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        name = withContext(Dispatchers.IO) { context.container.profileRepository.getProfile()?.fullName.orEmpty() }
    }
    return name
}

/** Offline map files: import an .mbtiles file, open the download page, delete. */
@Composable
private fun MapsDialog(vm: GashtViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<java.io.File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importMap(uri) { message = it }
    }
    val zonePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importZones(uri) { message = it }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ir.mohitban.app.ui.components.Clay.Surface,
        title = { Text("نقشه‌های آفلاین") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "نقشه‌ی توپوگرافی (پستی‌وبلندی، خطوط تراز، رودخانه، جنگل، جاده و روستاها) بدون اینترنت نمایش داده می‌شود. فایل نقشه (MBTiles) را اینجا وارد کنید.",
                    style = MaterialTheme.typography.bodySmall, color = MColors.Ink,
                )
                PillButton(
                    if (vm.mapBusy) "در حال وارد کردن…" else "وارد کردن فایل نقشه (MBTiles)",
                    { picker.launch(arrayOf("*/*")) }, Modifier.padding(top = 10.dp), loading = vm.mapBusy,
                )
                message?.let { Text(it, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Green, modifier = Modifier.padding(top = 6.dp)) }
                // ---- protected-area boundaries ----
                Text("مرز مناطق چهارگانه", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 16.dp))
                val imported = ir.mohitban.app.gasht.ZoneStore.isImported(context)
                Text(
                    when {
                        imported -> "${vm.zones.size.fa()} منطقه از فایلی که وارد کرده‌اید."
                        vm.zones.isNotEmpty() -> "مرز ${vm.zones.size.fa()} منطقه‌ی استان آذربایجان شرقی در برنامه موجود است. برای مرزهای جدیدتر، فایل GeoJSON اداره را وارد کنید."
                        else -> "فایل مرز مناطق (GeoJSON) را وارد کنید."
                    },
                    style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallAction(Icons.Outlined.Layers, if (imported) "جایگزینی فایل" else "وارد کردن فایل جدیدتر", Modifier.weight(1.4f)) { zonePicker.launch(arrayOf("*/*")) }
                    if (imported) SmallAction(Icons.Outlined.Close, "بازگشت به نسخه‌ی برنامه", Modifier.weight(1.4f), color = MColors.Error) { vm.deleteZones() }
                }
                vm.maps.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.nameWithoutExtension, style = MaterialTheme.typography.titleSmall)
                            Text("${(f.length() / (1024 * 1024)).fa()} مگابایت", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                        }
                        SmallAction(Icons.Outlined.Close, "حذف", color = MColors.Error) { deleting = f }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onDismiss) { Text("بستن") } },
    )
    deleting?.let { f ->
        ConfirmDialog(
            title = "حذف نقشه", text = "فایل نقشه‌ی «${f.nameWithoutExtension}» از گوشی حذف شود؟", confirm = "حذف", danger = true,
            onConfirm = { vm.deleteMap(f); deleting = null }, onDismiss = { deleting = null },
        )
    }
}
