package ir.mohitban.app.ui.ai

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.ai.Recognition
import ir.mohitban.app.ai.SpeciesClassifier
import ir.mohitban.app.ai.SpeciesGuess
import ir.mohitban.app.container
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.OutlinePill
import ir.mohitban.app.ui.components.PhotoCropDialog
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.components.SectionTitle
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.components.rememberImagePicker
import ir.mohitban.app.ui.components.safeBottomPadding
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Which bird is this?": a photo from the camera or the gallery, optionally cropped onto the bird, recognised on the
 * phone without internet; the likeliest species with what the app knows about it in Persian.
 */
@Composable
fun IdentifyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<Boolean?>(null) }
    var iranCount by remember { mutableStateOf(0) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var cropping by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Recognition?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            available = SpeciesClassifier.available(context)
            iranCount = SpeciesClassifier.iranSpeciesCount(context)
        }
    }

    fun recognize(b: Bitmap) {
        working = true
        result = null
        scope.launch {
            result = withContext(Dispatchers.Default) { SpeciesClassifier.recognize(context.applicationContext, b) }
            working = false
        }
    }

    val picker = rememberImagePicker(prefix = "identify", onImported = {}, rawCameraFile = { f ->
        scope.launch {
            val b = withContext(Dispatchers.IO) { context.container.files.decodeFile(f, 1600) }
            runCatching { f.delete() }
            if (b != null) { photo = b; result = null; cropping = true }
        }
    })

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("شناسایی پرنده با هوش مصنوعی", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Outlined.ImageSearch, MColors.GreenLight, MColors.Green, size = 42.dp)
                    Text(
                        "از پرنده عکس بگیرید یا از گالری انتخاب کنید؛ تشخیص روی خود گوشی و بدون اینترنت انجام می‌شود. " +
                            "بهترین نتیجه وقتی است که پرنده بزرگ و واضح وسط کادر باشد." +
                            (if (iranCount > 0) " اطلاعات فارسی ${iranCount.fa()} گونه از پرندگان ایران در برنامه هست." else ""),
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }
            if (available == false) {
                Text(
                    "مدل هوش مصنوعی در این نسخه‌ی برنامه نیست (هنگام ساخت برنامه دانلود نشد). در نسخه‌ی بعدی اضافه می‌شود.",
                    style = MaterialTheme.typography.bodyMedium, color = MColors.Error, modifier = Modifier.padding(top = 14.dp),
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("گرفتن عکس", { picker.takePhoto() }, Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera, enabled = available == true && !working)
                OutlinePill("از گالری", { picker.pickGallery() }, Modifier.weight(1f), icon = Icons.Outlined.PhotoLibrary)
            }

            photo?.let { b ->
                Image(
                    b.asImageBitmap(), "عکس", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill("برش روی پرنده", { cropping = true }, Modifier.weight(1f), icon = Icons.Outlined.Crop)
                    PillButton("تشخیص", { recognize(b) }, Modifier.weight(1f), icon = Icons.Outlined.ImageSearch, enabled = available == true && !working)
                }
            }

            if (working) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MColors.Green, strokeWidth = 3.dp)
                    Text("  در حال تشخیص…", style = MaterialTheme.typography.bodyMedium, color = MColors.Ink)
                }
            }
            when (val r = result) {
                null -> {}
                Recognition.NoModel -> Text("مدل هوش مصنوعی در این نسخه نیست.", color = MColors.Error, modifier = Modifier.padding(top = 14.dp))
                Recognition.NothingFound -> Text(
                    "پرنده‌ای که مدل بشناسد در عکس پیدا نشد. عکس نزدیک‌تر و واضح‌تری بگیرید یا روی پرنده برش بزنید.",
                    style = MaterialTheme.typography.bodyMedium, color = MColors.Amber, modifier = Modifier.padding(top = 14.dp),
                )
                is Recognition.Error -> Text("تشخیص انجام نشد: ${r.message}", color = MColors.Error, modifier = Modifier.padding(top = 14.dp))
                is Recognition.Found -> Results(r.guesses)
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.safeBottomPadding())
        }
    }

    val src = photo
    if (cropping && src != null) {
        PhotoCropDialog(
            source = src,
            onDismiss = { cropping = false },
            onCropped = { c -> cropping = false; photo = c; recognize(c) },
            outSize = 448,
        )
    }
}

@Composable
private fun Results(guesses: List<SpeciesGuess>) {
    val best = guesses.first()
    SectionTitle("نتیجه")
    BestCard(best)
    if (best.probability < 0.4f) {
        Text(
            "اطمینان مدل پایین است؛ گزینه‌های دیگر را هم ببینید یا عکس بهتری بگیرید.",
            style = MaterialTheme.typography.labelSmall, color = MColors.Amber, modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (guesses.size > 1) {
        SectionTitle("گزینه‌های دیگر")
        guesses.drop(1).forEach { g ->
            AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.info?.fa ?: g.scientific, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                            if (g.info != null) Text(g.scientific, style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), color = MColors.Muted)
                        }
                        Text(percent(g.probability), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
                    }
                    LinearProgressIndicator(progress = { g.probability.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = MColors.Green)
                }
            }
        }
    }
    Text(
        "این تشخیص ماشینی و راهنماست؛ برای ثبت رسمی، به‌ویژه درباره‌ی گونه‌های حمایت‌شده، تأیید کارشناس لازم است. " +
            "مدل فعلی پرندگان را می‌شناسد و برخی گونه‌های کمیاب ایران (مانند هوبره، هما و بالابان) هنوز در آن نیست.",
        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun BestCard(g: SpeciesGuess) {
    val i = g.info
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(i?.fa ?: g.scientific, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = MColors.Ink)
                    Text(
                        listOfNotNull(g.scientific.takeIf { i != null }, i?.en).joinToString(" — "),
                        style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic), color = MColors.Muted,
                    )
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(MColors.Green).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(percent(g.probability), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = Color.White)
                }
            }
            if (i == null) {
                Text(
                    "این گونه در فهرست پرندگان ایرانِ برنامه نیست؛ احتمال اشتباه بیشتر است و اطلاعات فارسی آن موجود نیست.",
                    style = MaterialTheme.typography.bodySmall, color = MColors.Amber, modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                InfoLine("وضعیت جهانی (IUCN)", i.iucnTitle, if (i.threatened) MColors.Error else MColors.Green)
                InfoLine("حضور در ایران", i.iran, MColors.Ink)
                InfoLine("زیستگاه", i.habitat, MColors.Ink)
                if (i.threatened) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFDECEC)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = MColors.Error, modifier = Modifier.size(20.dp))
                        Text(
                            "  گونه‌ی تهدیدشده: هرگونه شکار، زنده‌گیری، آزار یا تخریب آشیانه‌ی آن تخلف است و باید گزارش شود.",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, color: Color) {
    if (value.isBlank()) return
    Row(Modifier.padding(top = 8.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelMedium, color = MColors.Muted)
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

private fun percent(p: Float): String = "${(p * 100).toInt().coerceIn(0, 100).fa()}٪"
