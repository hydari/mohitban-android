package ir.mohitban.app.ui.gasht

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.mohitban.app.gasht.SosReadiness
import ir.mohitban.app.ui.theme.MColors

/**
 * Checklist so that a colleague's emergency SMS opens the app even when it is closed: every missing permission
 * with a button to allow it. Rechecked whenever the ranger comes back from the phone's settings.
 */
@Composable
fun SosReadinessList(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(SosReadiness.missing(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) missing = SosReadiness.missing(context) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { missing = SosReadiness.missing(context) }
    val autoStart = remember { SosReadiness.autoStartIntent(context) }

    Column(modifier.fillMaxWidth()) {
        if (missing.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF1B7F3B), modifier = Modifier.size(18.dp))
                Text(
                    "  همه‌ی اجازه‌ها داده شده؛ پیامک اضطراری حتی وقتی برنامه بسته است آن را باز می‌کند.",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B7F3B),
                )
            }
        } else {
            Text(
                "برای اینکه پیامک اضطراری حتی با برنامه‌ی بسته آن را خودکار باز کند، این موارد را روشن کنید:",
                style = MaterialTheme.typography.labelSmall, color = MColors.Amber, modifier = Modifier.padding(top = 6.dp),
            )
            missing.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF4E5))
                        .clickable {
                            when (item) {
                                SosReadiness.Item.SMS, SosReadiness.Item.NOTIFY -> ask.launch(
                                    buildList {
                                        add(android.Manifest.permission.RECEIVE_SMS)
                                        if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }.toTypedArray(),
                                )
                                else -> {
                                    val i = SosReadiness.settingsIntent(context, item) ?: SosReadiness.appDetails(context)
                                    runCatching { context.startActivity(i) }.onFailure { runCatching { context.startActivity(SosReadiness.appDetails(context)) } }
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MColors.Amber, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                        Text(item.why, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
                    }
                    Text("روشن کردن", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
                }
            }
        }
        if (autoStart != null) {
            Text(
                "در این گوشی «اجرای خودکار» (Autostart) را هم برای محیط‌یار روشن کنید؛ بدون آن، برنامه‌ی بسته‌شده پیامک را دریافت نمی‌کند.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "باز کردن تنظیمات اجرای خودکار", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Green,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { runCatching { context.startActivity(autoStart) }.onFailure { runCatching { context.startActivity(SosReadiness.appDetails(context)) } } }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
