package ir.mohitban.app.ui.gasht

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.data.local.TrackEntity
import ir.mohitban.app.data.local.WaypointEntity
import ir.mohitban.app.gasht.CoordFormat
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.gasht.PlaceKind
import ir.mohitban.app.gasht.TrackSource
import ir.mohitban.app.gasht.geo
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.EmptyState
import ir.mohitban.app.ui.components.MField
import ir.mohitban.app.ui.components.OutlinePill
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.rememberImagePicker
import ir.mohitban.app.ui.components.rememberStoredImage
import ir.mohitban.app.ui.theme.MColors

// ============================================================================ tracks

@Composable
fun TracksTab(vm: GashtViewModel) {
    val context = LocalContext.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<TrackEntity?>(null) }
    var deleting by remember { mutableStateOf<TrackEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri) { message = it }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PillButton("وارد کردن فایل دستگاه GPS", { picker.launch(arrayOf("*/*")) }, icon = Icons.Outlined.FileOpen)
            Text(
                "فایل‌های GPX (گارمین و بیشتر دستگاه‌ها)، KML و KMZ (گوگل ارث)، PLT و WPT (اوزی‌اکسپلورر) خوانده می‌شوند. فایل را با کابل یا پیام‌رسان به گوشی بیاورید.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
            )
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MColors.Green, modifier = Modifier.padding(top = 6.dp))
            }
            if (tracks.isNotEmpty()) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Icons.Outlined.FileUpload, "همه به GPX", Modifier.weight(1f)) {
                        vm.export(tracks.map { it.id }, true, false, "gasht-all", context)
                    }
                    SmallAction(Icons.Outlined.FileUpload, "همه به KML", Modifier.weight(1f)) {
                        vm.export(tracks.map { it.id }, true, true, "gasht-all", context)
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyState(Icons.Outlined.Route, "مسیری ثبت نشده", "مسیر گشت را از زبانه‌ی «نقشه» ضبط کنید یا فایل دستگاه GPS را وارد کنید.") }
        }
        items(tracks, key = { it.id }) { t ->
            TrackCard(
                t, shown = t.id in vm.shown,
                onShow = { vm.toggleShown(t.id) },
                onOpen = { vm.showOnly(t.id) },
                onFollow = { vm.followTrack(t) },
                onGpx = { vm.export(listOf(t.id), false, false, t.name, context) },
                onKml = { vm.export(listOf(t.id), false, true, t.name, context) },
                onRename = { renaming = t },
                onDelete = { deleting = t },
            )
        }
    }

    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = Color.White,
            title = { Text("نام مسیر") },
            text = { MField(name, { name = it }, label = "نام", maxLength = 80) },
            confirmButton = { TextButton({ vm.rename(t.id, name); renaming = null }) { Text("ذخیره") } },
            dismissButton = { TextButton({ renaming = null }) { Text("انصراف") } },
        )
    }
    deleting?.let { t ->
        ConfirmDialog(
            title = "حذف مسیر", text = "مسیر «${t.name}» حذف شود؟", confirm = "حذف", danger = true,
            onConfirm = { vm.delete(t.id); deleting = null }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TrackCard(
    t: TrackEntity, shown: Boolean,
    onShow: () -> Unit, onOpen: () -> Unit, onFollow: () -> Unit,
    onGpx: () -> Unit, onKml: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    val recorded = t.source == TrackSource.RECORDED
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(Icons.Outlined.Route, if (recorded) Color(0xFF239B56) else Color(0xFF2F7FC1), Color.White, size = 40.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(
                        listOfNotNull(
                            if (recorded) "ضبط‌شده" else "واردشده",
                            jalaliOf(t.createdAt).format().fa(),
                            CoordFormat.meters(t.distance).fa(),
                            t.duration.takeIf { it > 0 }?.let { durationText(it) },
                            "${t.pointCount.fa()} نقطه",
                        ).joinToString(" | "),
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
                    )
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (shown) "پنهان" else "نمایش", Modifier.weight(1f), onClick = onShow)
                SmallAction(Icons.Outlined.Navigation, "راهنما", Modifier.weight(1f), color = Color(0xFFE08A1E), onClick = onFollow)
                SmallAction(Icons.Outlined.Share, "GPX", Modifier.weight(1f), color = Color(0xFF2F7FC1), onClick = onGpx)
                SmallAction(Icons.Outlined.Share, "KML", Modifier.weight(1f), color = Color(0xFF2F7FC1), onClick = onKml)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(Icons.Outlined.DriveFileRenameOutline, "تغییر نام", Modifier.weight(1f), color = MColors.Muted, onClick = onRename)
                SmallAction(Icons.Outlined.Delete, "حذف", Modifier.weight(1f), color = MColors.Error, onClick = onDelete)
            }
        }
    }
}

// ============================================================================ places

@Composable
fun PlacesTab(vm: GashtViewModel) {
    val context = LocalContext.current
    val places by vm.places.collectAsStateWithLifecycle()
    val fix by vm.fix.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf<GeoPoint?>(null) }
    var manual by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WaypointEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("ثبت در موقعیت فعلی", { fix?.let { add = it.point } }, Modifier.weight(1f), icon = Icons.Outlined.AddLocationAlt, enabled = fix != null)
                OutlinePill("با مختصات", { manual = true }, Modifier.weight(1f).height(56.dp), Icons.Outlined.EditLocationAlt)
            }
            if (places.isNotEmpty()) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Icons.Outlined.FileUpload, "نقاط به GPX", Modifier.weight(1f)) { vm.export(emptyList(), true, false, "places", context) }
                    SmallAction(Icons.Outlined.FileUpload, "نقاط به KML", Modifier.weight(1f)) { vm.export(emptyList(), true, true, "places", context) }
                }
            }
        }
        if (places.isEmpty()) {
            item { EmptyState(Icons.Outlined.Place, "نقطه‌ای ثبت نشده", "چشمه، محل کمین، لانه یا محل کشف را با موقعیت فعلی یا مختصات ثبت کنید.") }
        }
        items(places, key = { it.id }) { w ->
            PlaceCard(
                w, from = fix?.point,
                onGo = { vm.followPlace(w) },
                onShare = {
                    val p = w.geo()
                    shareText(context, "${w.name} (${PlaceKind.of(w.kind).title})\n${CoordFormat.decimal(p)}\nUTM: ${CoordFormat.utm(p)}" +
                        (if (w.note.isNotBlank()) "\n${w.note}" else "") +
                        "\nhttps://maps.google.com/?q=${String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon)}")
                },
                onDelete = { deleting = w },
            )
        }
    }

    add?.let { p -> AddPlaceDialog(vm, p, onDismiss = { add = null }) }
    if (manual) ManualPointDialog(onDismiss = { manual = false }, onPoint = { manual = false; add = it })
    deleting?.let { w ->
        ConfirmDialog(
            title = "حذف نقطه", text = "نقطه‌ی «${w.name}» حذف شود؟", confirm = "حذف", danger = true,
            onConfirm = { vm.deletePlace(w.id); deleting = null }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PlaceCard(w: WaypointEntity, from: GeoPoint?, onGo: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val photo by rememberStoredImage(w.photoPath, 300)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ph = photo
                if (ph != null) {
                    Image(ph, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                } else {
                    CircleIcon(Icons.Outlined.Place, Color(0xFF7B4FB5), Color.White, size = 44.dp, iconSize = 22.dp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(w.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(PlaceKind.of(w.kind).title, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                    LtrText(CoordFormat.decimal(w.geo()), style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                }
                from?.let { f ->
                    val d = Geo.distance(f, w.geo())
                    val b = Geo.bearing(f, w.geo())
                    Column(horizontalAlignment = Alignment.End) {
                        Text(CoordFormat.meters(d).fa(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                        Text(Geo.direction(b), style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                    }
                }
            }
            if (w.note.isNotBlank()) Text(w.note, style = MaterialTheme.typography.bodySmall, color = MColors.Ink, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(Icons.Outlined.Navigation, "مسیریابی", Modifier.weight(1f), color = Color(0xFFE08A1E), onClick = onGo)
                SmallAction(Icons.Outlined.Share, "ارسال", Modifier.weight(1f), color = Color(0xFF2F7FC1), onClick = onShare)
                SmallAction(Icons.Outlined.Delete, "حذف", Modifier.weight(1f), color = MColors.Error, onClick = onDelete)
            }
        }
    }
}

@Composable
fun AddPlaceDialog(vm: GashtViewModel, point: GeoPoint, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PlaceKind.SPRING) }
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    val picker = rememberImagePicker("place", onImported = { photo = it })
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("ثبت نقطه") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LtrText(CoordFormat.decimal(point), style = MaterialTheme.typography.labelLarge, color = MColors.Green, modifier = Modifier.fillMaxWidth())
                MField(name, { name = it }, label = "نام نقطه", placeholder = kind.title, maxLength = 60)
                Text("نوع", style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                PlaceKind.entries.chunked(3).forEach { row ->
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k -> ChoiceChip(k.title, k == kind, Modifier.weight(1f)) { kind = k } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                MField(note, { note = it }, label = "توضیح (اختیاری)", maxLength = 300)
                SmallAction(Icons.Outlined.AddAPhoto, if (photo == null) "افزودن عکس" else "عکس اضافه شد ✓", Modifier.fillMaxWidth().padding(top = 8.dp)) { picker.openSheet() }
            }
        },
        confirmButton = {
            TextButton({
                vm.addPlace(
                    WaypointEntity(
                        name = name.trim().ifEmpty { kind.title }, kind = kind.name, lat = point.lat, lon = point.lon, ele = point.ele,
                        note = note.trim(), photoPath = photo, createdAt = System.currentTimeMillis(),
                    ),
                )
                onDismiss()
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

/** Enter a position by hand: decimal / DMS, or UTM zone + easting + northing (as printed on maps). */
@Composable
fun ManualPointDialog(onDismiss: () -> Unit, onPoint: (GeoPoint) -> Unit) {
    var utm by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("39") }
    var east by remember { mutableStateOf("") }
    var north by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("وارد کردن مختصات") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("درجه", !utm, Modifier.weight(1f)) { utm = false }
                    ChoiceChip("UTM", utm, Modifier.weight(1f)) { utm = true }
                }
                Spacer(Modifier.height(8.dp))
                if (!utm) {
                    MField(text, { text = it }, label = "عرض و طول جغرافیایی", placeholder = "35.689200, 51.389000", maxLength = 60)
                    Text("درجه اعشاری یا درجه‌دقیقه‌ثانیه، اول عرض (N) بعد طول (E).", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                } else {
                    MField(zone, { zone = it }, label = "زون (ایران ۳۸ تا ۴۱)", numeric = ir.mohitban.app.ui.components.NumericMode.Digits, maxLength = 2)
                    MField(east, { east = it }, label = "Easting (شرقی)", numeric = ir.mohitban.app.ui.components.NumericMode.Digits, maxLength = 7)
                    MField(north, { north = it }, label = "Northing (شمالی)", numeric = ir.mohitban.app.ui.components.NumericMode.Digits, maxLength = 8)
                }
                error?.let { Text(it, color = MColors.Error, style = MaterialTheme.typography.labelSmall) }
            }
        },
        confirmButton = {
            TextButton({
                val p = if (!utm) CoordFormat.parse(text) else {
                    val z = zone.toIntOrNull(); val e = east.toDoubleOrNull(); val n = north.toDoubleOrNull()
                    if (z == null || e == null || n == null || z !in 1..60) null
                    else ir.mohitban.app.gasht.Utm.toLatLon(ir.mohitban.app.gasht.Utm.Coord(z, true, e, n))
                }
                if (p == null) error = "مختصات معتبر نیست" else onPoint(p)
            }) { Text("ادامه") }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}
