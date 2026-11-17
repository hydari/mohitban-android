package ir.mohitban.app.ui.ai

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.ai.Observation
import ir.mohitban.app.ai.ObservationStore
import ir.mohitban.app.ai.PendingWildlife
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.gasht.CoordFormat
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.ConfirmDialog
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.components.rememberStoredImage
import ir.mohitban.app.ui.components.safeBottomPadding
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** The recognised species the ranger kept, newest first, with photo, time and place. */
@Composable
fun ObservationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ver by ObservationStore.version.collectAsState()
    var list by remember { mutableStateOf<List<Observation>?>(null) }
    LaunchedEffect(ver) { list = withContext(Dispatchers.IO) { ObservationStore.all(context) } }
    var askDelete by remember { mutableStateOf<Observation?>(null) }

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("تاریخچه‌ی مشاهدات", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val items = list
            when {
                items == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MColors.Green) }
                items.isEmpty() -> Text(
                    "هنوز مشاهده‌ای ذخیره نشده است. بعد از شناسایی، «ذخیره در تاریخچه» را بزنید تا عکس، نام گونه، زمان و مکان نگه داشته شود.",
                    style = MaterialTheme.typography.bodyMedium, color = MColors.Muted, modifier = Modifier.padding(top = 20.dp),
                )
                else -> {
                    Text("${items.size.fa()} مشاهده", style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    items.forEach { o -> ObservationRow(o, onDelete = { askDelete = o }) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.safeBottomPadding())
        }
    }
    askDelete?.let { o ->
        ConfirmDialog(
            title = "حذف مشاهده", text = "«${o.fa}» از تاریخچه حذف شود؟", confirm = "حذف", danger = true,
            onDismiss = { askDelete = null },
            onConfirm = {
                askDelete = null
                ObservationStore.delete(context, o.id)?.let { gone -> context.container.files.delete(gone.photoPath) }
            },
        )
    }
}

@Composable
private fun ObservationRow(o: Observation, onDelete: () -> Unit) {
    val context = LocalContext.current
    val photo by rememberStoredImage(o.photoPath, 300)
    AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(MColors.GreenLight), contentAlignment = Alignment.Center) {
                val img = photo
                if (img != null) Image(img, o.fa, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Outlined.ImageSearch, null, tint = MColors.Green)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(o.fa, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                if (o.scientific.isNotBlank()) Text(o.scientific, style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), color = MColors.Muted)
                Text(
                    "${o.categoryTitle} | ${(o.probability * 100).toInt().coerceIn(0, 100).fa()}٪" + if (o.approx) " | گروه تقریبی" else "",
                    style = MaterialTheme.typography.labelSmall, color = MColors.Green,
                )
                Text(timeText(o.createdAt), style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                val lat = o.lat; val lon = o.lon
                if (lat != null && lon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = MColors.Error, modifier = Modifier.size(13.dp))
                        Text(" ${CoordFormat.decimal(GeoPoint(lat, lon))}", style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).clickable {
                        PendingWildlife.add(context, o.fa)
                        Toast.makeText(context, "«${o.fa}» به گزارش روزانه اضافه شد", Toast.LENGTH_SHORT).show()
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, "افزودن به گزارش روزانه", tint = MColors.Green, modifier = Modifier.size(22.dp)) }
                Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DeleteOutline, "حذف", tint = MColors.Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun timeText(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val d = Jalali.fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    val hm = String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    return "${d.formatLong(withWeekday = false)} ساعت $hm".fa()
}
