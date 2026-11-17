package ir.mohitban.app.ui.ai

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.History
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
import ir.mohitban.app.ai.Category
import ir.mohitban.app.ai.CategoryResult
import ir.mohitban.app.ai.Observation
import ir.mohitban.app.ai.ObservationStore
import ir.mohitban.app.ai.PendingWildlife
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
 * "What is this?": a photo of a bird, plant or tree, insect, mammal, reptile, fish, fungus… from the camera or the
 * gallery, optionally cropped onto the subject, recognised on the phone without internet. The result shows the likeliest
 * names with what the app knows in Persian, and can be kept in the observation history or sent to the daily report.
 */
@Composable
fun IdentifyScreen(onBack: () -> Unit, onHistory: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<List<Category>?>(null) }
    var counts by remember { mutableStateOf<Map<Category, Int>>(emptyMap()) }
    var mode by remember { mutableStateOf<Category?>(null) } // null = auto
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var cropping by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Recognition?>(null) }
    var savedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val av = SpeciesClassifier.availableCategories(context)
            counts = av.associateWith { SpeciesClassifier.classCount(context, it) }
            available = av
        }
    }
    val ready = !available.isNullOrEmpty()

    fun recognize(b: Bitmap, m: Category? = mode) {
        working = true
        result = null
        scope.launch {
            result = withContext(Dispatchers.Default) { SpeciesClassifier.recognize(context.applicationContext, b, m) }
            working = false
        }
    }

    val picker = rememberImagePicker(prefix = "identify", onImported = {}, rawCameraFile = { f ->
        scope.launch {
            val b = withContext(Dispatchers.IO) { context.container.files.decodeFile(f, 1600) }
            runCatching { f.delete() }
            if (b != null) { photo = b; result = null; savedIds = emptySet(); cropping = true }
        }
    })

    fun keep(g: SpeciesGuess) {
        val b = photo
        scope.launch {
            val id = "${g.category.key}|${g.scientific}|${g.title}"
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val path = b?.let { context.container.files.saveJpeg(it, "obs", 86) }
                    val pt = ObservationStore.lastKnown(context)
                    ObservationStore.save(
                        context,
                        Observation(
                            createdAt = System.currentTimeMillis(), category = g.category.key, scientific = g.scientific, fa = g.title,
                            english = g.english, probability = g.probability, photoPath = path, lat = pt?.lat, lon = pt?.lon,
                            approx = g.info?.approx == true,
                        ),
                    )
                }.isSuccess
            }
            if (ok) { savedIds = savedIds + id; Toast.makeText(context, "در تاریخچه‌ی مشاهدات ذخیره شد", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(context, "ذخیره انجام نشد", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("شناسایی با هوش مصنوعی", onBack = onBack, action = {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onHistory), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.History, "تاریخچه‌ی مشاهدات", tint = MColors.Green)
            }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Outlined.ImageSearch, MColors.GreenLight, MColors.Green, size = 42.dp)
                    Text(
                        "پرنده، درخت و گیاه، حشره، پستاندار، خزنده، ماهی و قارچ را از روی عکس بشناسید. تشخیص روی خود گوشی و بدون اینترنت انجام می‌شود؛ " +
                            "عکس نزدیک، واضح و با موضوع در وسط کادر بهترین نتیجه را می‌دهد.",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }

            if (available != null && !ready) {
                Text("مدل‌های هوش مصنوعی در این نسخه نیستند.", style = MaterialTheme.typography.bodyMedium, color = MColors.Error, modifier = Modifier.padding(top = 14.dp))
            }

            // ---------------- what is in the photo
            SectionTitle("موضوع عکس")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip("خودکار", Icons.Outlined.AutoAwesome, mode == null, Modifier.weight(1f)) { mode = null; photo?.let { b -> if (result != null) recognize(b, null) } }
                    ModeChip("پرنده", null, mode == Category.BIRD, Modifier.weight(1f), available?.contains(Category.BIRD) != false) { mode = Category.BIRD; photo?.let { b -> if (result != null) recognize(b, Category.BIRD) } }
                    ModeChip("گیاه و درخت", null, mode == Category.PLANT, Modifier.weight(1.4f), available?.contains(Category.PLANT) != false) { mode = Category.PLANT; photo?.let { b -> if (result != null) recognize(b, Category.PLANT) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip("حشره", null, mode == Category.INSECT, Modifier.weight(1f), available?.contains(Category.INSECT) != false) { mode = Category.INSECT; photo?.let { b -> if (result != null) recognize(b, Category.INSECT) } }
                    ModeChip("جانوران و قارچ", null, mode == Category.ANIMAL, Modifier.weight(1.6f), available?.contains(Category.ANIMAL) != false) { mode = Category.ANIMAL; photo?.let { b -> if (result != null) recognize(b, Category.ANIMAL) } }
                }
            }
            Text(
                mode?.let { "${it.title}: ${it.hint}" } ?: "حالت خودکار همه‌ی مدل‌ها را امتحان می‌کند و محتمل‌ترین دسته را بالا می‌آورد؛ اگر دسته اشتباه بود، موضوع را خودتان انتخاب کنید.",
                style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 6.dp),
            )

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("گرفتن عکس", { picker.takePhoto() }, Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera, enabled = ready && !working)
                OutlinePill("از گالری", { picker.pickGallery() }, Modifier.weight(1f), icon = Icons.Outlined.PhotoLibrary)
            }

            photo?.let { b ->
                Image(
                    b.asImageBitmap(), "عکس", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill("برش روی موضوع", { cropping = true }, Modifier.weight(1f), icon = Icons.Outlined.Crop)
                    PillButton("تشخیص", { recognize(b) }, Modifier.weight(1f), icon = Icons.Outlined.ImageSearch, enabled = ready && !working)
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
                    "موجود زنده‌ای که مدل‌ها بشناسند در عکس پیدا نشد. عکس نزدیک‌تر و واضح‌تری بگیرید، روی موضوع برش بزنید یا دسته را دستی انتخاب کنید.",
                    style = MaterialTheme.typography.bodyMedium, color = MColors.Amber, modifier = Modifier.padding(top = 14.dp),
                )
                is Recognition.Error -> Text("تشخیص انجام نشد: ${r.message}", color = MColors.Error, modifier = Modifier.padding(top = 14.dp))
                is Recognition.Found -> {
                    r.results.forEachIndexed { i, cr ->
                        CategorySection(cr, leading = i == 0 && r.auto && r.results.size > 1, savedIds = savedIds, onKeep = ::keep, onDaily = { g ->
                            PendingWildlife.add(context, g.title)
                            Toast.makeText(context, "«${g.title}» به گزارش روزانه اضافه شد", Toast.LENGTH_SHORT).show()
                        })
                    }
                    Text(
                        "این تشخیص ماشینی و راهنماست و جایگزین تأیید کارشناس نیست؛ به‌ویژه برای گونه‌های حمایت‌شده، گیاهان و قارچ‌های خوراکی یا دارویی و جانوران خطرناک. " +
                            "مدل‌های گیاه و حشره بیشتر با گونه‌های اروپا و آمریکای شمالی آموزش دیده‌اند و برای جانوران ایران نتیجه در سطح گروه است.",
                        style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            available?.let { av ->
                if (av.isNotEmpty() && photo == null) {
                    SectionTitle("آنچه برنامه می‌شناسد")
                    av.forEach { c ->
                        Text("• ${c.title}: ${counts[c]?.fa() ?: "—"} ${if (c == Category.ANIMAL) "گروه" else "گونه"}", style = MaterialTheme.typography.labelMedium, color = MColors.Ink, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
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
private fun ModeChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.height(42.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFE3F1E8) else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) MColors.Green else MColors.Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) MColors.Green else MColors.Muted, modifier = Modifier.size(17.dp).padding(end = 2.dp))
        Text(
            text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (!enabled) MColors.Hint else if (selected) MColors.Green else MColors.Ink, maxLines = 1,
        )
    }
}

@Composable
private fun CategorySection(cr: CategoryResult, leading: Boolean, savedIds: Set<String>, onKeep: (SpeciesGuess) -> Unit, onDaily: (SpeciesGuess) -> Unit) {
    val best = cr.guesses.first()
    SectionTitle(if (leading) "محتمل‌ترین دسته: ${cr.category.title}" else cr.category.title)
    BestCard(best, saved = "${best.category.key}|${best.scientific}|${best.title}" in savedIds, onKeep = { onKeep(best) }, onDaily = { onDaily(best) })
    if (best.probability < 0.4f) {
        Text(
            "اطمینان مدل پایین است؛ گزینه‌های دیگر را هم ببینید یا عکس بهتری بگیرید.",
            style = MaterialTheme.typography.labelSmall, color = MColors.Amber, modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (cr.guesses.size > 1) {
        Text("گزینه‌های دیگر", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MColors.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp, start = 4.dp))
        cr.guesses.drop(1).forEach { g ->
            AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Ink)
                            val sub = listOf(g.scientific, g.english.takeIf { it.isNotBlank() && it != g.title }).filterNotNull().filter { it.isNotBlank() }.joinToString(" — ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), color = MColors.Muted)
                        }
                        Text(percent(g.probability), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
                    }
                    LinearProgressIndicator(progress = { g.probability.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = MColors.Green)
                }
            }
        }
    }
}

@Composable
private fun BestCard(g: SpeciesGuess, saved: Boolean, onKeep: () -> Unit, onDaily: () -> Unit) {
    val i = g.info
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = MColors.Ink)
                    val sub = listOf(g.scientific, g.english.takeIf { it.isNotBlank() && it != g.title }).filterNotNull().filter { it.isNotBlank() }.joinToString(" — ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic), color = MColors.Muted)
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(MColors.Green).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(percent(g.probability), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = Color.White)
                }
            }
            if (i?.protected == true) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFDECEC)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = MColors.Error, modifier = Modifier.size(20.dp))
                    Text(
                        if (i.approx) "  اگر این جانور همان گونه باشد، در ایران حفاظت‌شده است؛ پیش از هر اقدام تأیید کنید و مشاهده را گزارش دهید."
                        else "  گونه‌ی حفاظت‌شده در ایران: مشاهده‌ی آن را فوراً به اداره‌ی حفاظت محیط زیست گزارش کنید و هرگونه آزار یا شکار تخلف است.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Error,
                    )
                }
            }
            when {
                i != null -> {
                    if (i.approx) Note("این مدل گونه‌ی دقیق را ندارد؛ نزدیک‌ترین گروه نشان داده شده و احتمال اشتباه بیشتر است.", MColors.Amber)
                    InfoLine("نوع", listOf(i.kind, i.group).filter { it.isNotBlank() }.distinct().joinToString(" | "), MColors.Ink)
                    InfoLine("وضعیت جهانی (IUCN)", i.iucnTitle, if (i.threatened) MColors.Error else MColors.Green)
                    InfoLine("حضور در ایران", i.iran, MColors.Ink)
                    InfoLine("زیستگاه", i.habitat, MColors.Ink)
                    InfoLine("توضیح", i.note, MColors.Ink)
                    if (i.threatened) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFDECEC)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.WarningAmber, null, tint = MColors.Error, modifier = Modifier.size(20.dp))
                            Text(
                                "  گونه‌ی تهدیدشده: هرگونه شکار، زنده‌گیری، آزار یا تخریب زیستگاه آن تخلف است و باید گزارش شود.",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Error,
                            )
                        }
                    }
                }
                g.genusOnly -> Note("نام فارسی گونه در برنامه نیست؛ جنس آن «${g.genusFa}» است. نام علمی بالا را برای ثبت رسمی به کار ببرید.", MColors.Amber)
                else -> Note("اطلاعات فارسی این گونه در برنامه نیست؛ احتمال اشتباه بیشتر است.", MColors.Amber)
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill(if (saved) "ذخیره شد" else "ذخیره در تاریخچه", onKeep, Modifier.weight(1f), icon = Icons.Outlined.Bookmark)
                OutlinePill("افزودن به گزارش روزانه", onDaily, Modifier.weight(1.3f), icon = Icons.AutoMirrored.Outlined.PlaylistAdd)
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun InfoLine(label: String, value: String, color: Color) {
    if (value.isBlank()) return
    Row(Modifier.padding(top = 8.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelMedium, color = MColors.Muted)
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color, modifier = Modifier.weight(1f, fill = false))
    }
}

private fun percent(p: Float): String = "${(p * 100).toInt().coerceIn(0, 100).fa()}٪"
