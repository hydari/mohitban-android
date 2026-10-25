package ir.mohitban.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.mohitban.app.BuildConfigCompat
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.ui.theme.MColors

/** Contact details of the app's designer and developer. */
object Developer {
    const val NAME = "مهندس محمدرضا حیدری"
    const val PHONE = "09149044270"
    const val EMAIL = "dev.hydari@gmail.com"

    fun call(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$PHONE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun mail(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
fun DeveloperDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircleIcon(Icons.Outlined.Code, MColors.Green, Color.White, size = 64.dp, iconSize = 32.dp)
            Text("طراح و برنامه‌نویس", style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.padding(top = 12.dp))
            Text(
                Developer.NAME, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MColors.Green, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
            )
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactRow(Icons.Outlined.Call, Color(0xFF239B56), "تلفن تماس", Developer.PHONE.fa()) { Developer.call(context) }
                ContactRow(Icons.Outlined.Email, Color(0xFF2F7FC1), "ایمیل", Developer.EMAIL) { Developer.mail(context) }
                ContactRow(Icons.Outlined.Info, Color(0xFF5F6F7A), "نسخه اپلیکیشن", "محیط‌یار ${BuildConfigCompat.versionName(context).fa()}", null)
            }
            GreenButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(48.dp))
        }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, color: Color, label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MColors.Bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, color, Color.White, size = 36.dp, iconSize = 19.dp)
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MColors.Muted)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
        }
    }
}
