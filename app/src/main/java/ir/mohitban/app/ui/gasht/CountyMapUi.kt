package ir.mohitban.app.ui.gasht

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.mohitban.app.container
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.gasht.CountyMap
import ir.mohitban.app.gasht.CountyMaps
import ir.mohitban.app.gasht.Download
import ir.mohitban.app.gasht.Fix
import ir.mohitban.app.gasht.MapDownloader
import ir.mohitban.app.gasht.Whereabouts
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.clay
import ir.mohitban.app.ui.theme.Corners
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun mbText(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_048_576.0).removeSuffix(".0").fa() + " مگابایت"

/** The county the ranger registered with (for when there is no GPS position yet). */
@Composable
private fun rememberRegisteredCounty(): String? {
    val context = LocalContext.current
    var city by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { city = withContext(Dispatchers.IO) { context.container.profileRepository.getProfile()?.city } }
    return city
}

/**
 * On the map tab: when the ranger stands in a county whose map is not on the phone, offer it
 * (size shown, one tap); while it downloads, show the progress; when it is on the phone but
 * another map is shown, offer to show it.
 */
@Composable
fun CountyMapSuggestion(vm: GashtViewModel, fix: Fix?) {
    val context = LocalContext.current
    val downloads by MapDownloader.state.collectAsStateWithLifecycle()
    var gpsCounty by remember { mutableStateOf<String?>(null) }
    val key = fix?.let { (it.point.lat * 50).toInt() to (it.point.lon * 50).toInt() } // about 2 km
    LaunchedEffect(key) {
        val p = fix?.point ?: return@LaunchedEffect
        gpsCounty = withContext(Dispatchers.IO) { runCatching { Whereabouts.locate(context, p).county }.getOrNull() }
    }
    val registered = rememberRegisteredCounty()
    val atGps = CountyMaps.byCounty(context, gpsCounty)
    val candidate = atGps ?: if (fix == null) CountyMaps.byCounty(context, registered) else null
    val have = candidate != null && vm.maps.any { it.name == candidate.file }

    Column(Modifier.fillMaxWidth()) {
        // every download in progress (or failed) with its own bar
        downloads.values.filter { it.state != Download.State.FINISHED }.forEach { d -> DownloadCard(d) }

        val offer = candidate != null && !have && candidate.file !in downloads && candidate.file !in vm.dismissedMaps
        AnimatedVisibility(offer, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val m = candidate ?: return@AnimatedVisibility
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Corners.R))
                    .background(Color(0xFFE6F0FA)).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Outlined.Map, Color(0xFF2F7FC1), Color.White, size = 36.dp, iconSize = 19.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            if (atGps != null) "شما در ${m.title} هستید" else "نقشه‌ی ${m.title} (محل خدمت شما)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1D4F80),
                        )
                        Text(
                            "نقشه‌ی این شهرستان (${mbText(m.size)}) هنوز روی گوشی نیست. با یک بار دانلود، بعد از آن بدون اینترنت نمایش داده می‌شود.",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Ink, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Icons.Outlined.CloudDownload, "دانلود نقشه", Modifier.weight(1.4f), color = Color(0xFF2F7FC1)) { MapDownloader.start(context, m) }
                    SmallAction(Icons.Outlined.Close, "بعداً", Modifier.weight(1f), color = MColors.Muted) { vm.dismissedMaps = vm.dismissedMaps + m.file }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(d: Download) {
    val context = LocalContext.current
    val failed = d.state == Download.State.FAILED
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clay(depth = 4.dp).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (failed) "دانلود نقشه‌ی ${d.map.title} متوقف شد" else "در حال دانلود نقشه‌ی ${d.map.title}…",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = if (failed) MColors.Error else MColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Text("${(d.fraction * 100).toInt().fa()}٪", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
        }
        LinearProgressIndicator(
            progress = { d.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (failed) MColors.Amber else MColors.Green2, trackColor = MColors.Line,
        )
        Text(
            d.error ?: "${mbText(d.done)} از ${mbText(d.total)} — می‌توانید از این صفحه بیرون بروید؛ دانلود ادامه دارد.",
            style = MaterialTheme.typography.labelSmall, color = if (failed) MColors.Error else MColors.Muted, modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failed) SmallAction(Icons.Outlined.Refresh, "ادامه‌ی دانلود", Modifier.weight(1f)) { MapDownloader.start(context, d.map) }
            SmallAction(Icons.Outlined.Close, if (failed) "بستن" else "لغو", Modifier.weight(1f), color = MColors.Muted) {
                if (failed) MapDownloader.clear(d.map) else MapDownloader.cancel(d.map)
            }
        }
    }
}

/** In «نقشه‌ها»: every county map that can be downloaded, the ranger's own county first. */
@Composable
fun CountyMapList(vm: GashtViewModel) {
    val context = LocalContext.current
    val downloads by MapDownloader.state.collectAsStateWithLifecycle()
    val registered = rememberRegisteredCounty()
    val all = remember { CountyMaps.all(context) }
    if (all.isEmpty()) return
    val mine = CountyMaps.byCounty(context, registered)
    val ordered = listOfNotNull(mine) + all.filter { it != mine }
    Text(
        "دانلود نقشه‌ی شهرستان‌ها",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        "فعلاً شهرستان‌های ${all.map { it.province }.distinct().joinToString("، ")}. اینترنت فقط برای همین دانلود به کار می‌رود.",
        style = MaterialTheme.typography.labelSmall, color = MColors.Muted,
    )
    ordered.forEach { m -> CountyRow(vm, m, m == mine, downloads[m.file]) }
}

@Composable
private fun CountyRow(vm: GashtViewModel, m: CountyMap, mine: Boolean, d: Download?) {
    val context = LocalContext.current
    val have = vm.maps.any { it.name == m.file }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                m.title + if (mine) " (محل خدمت شما)" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal), color = MColors.Ink,
            )
            Text(
                when {
                    d != null && d.state == Download.State.RUNNING -> "در حال دانلود ${(d.fraction * 100).toInt().fa()}٪"
                    have -> "روی گوشی | ${mbText(m.size)}"
                    else -> mbText(m.size)
                },
                style = MaterialTheme.typography.labelSmall, color = if (have) MColors.Green else MColors.Muted,
            )
        }
        when {
            d != null && d.state == Download.State.RUNNING -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(progress = { d.fraction }, modifier = Modifier.size(26.dp), color = MColors.Green2, strokeWidth = 3.dp)
            }
            have -> SmallAction(Icons.Outlined.Map, "نمایش") { vm.selectMap(CountyMaps.local(context, m)) }
            else -> SmallAction(Icons.Outlined.CloudDownload, "دانلود", color = Color(0xFF2F7FC1)) { MapDownloader.start(context, m) }
        }
    }
}
