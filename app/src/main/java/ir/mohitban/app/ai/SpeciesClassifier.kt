package ir.mohitban.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** The kinds of living things the app can recognise from a photo; each has its own model on the phone. */
enum class Category(val key: String, val title: String, val shortTitle: String, val hint: String) {
    BIRD("birds", "پرنده", "پرنده", "۹۶۴ گونه‌ی پرنده‌ی جهان"),
    PLANT("plants", "گیاه، درخت و بوته", "گیاه و درخت", "۲۱۰۱ گونه‌ی گیاهی (بیشتر اروپا و آمریکای شمالی)"),
    INSECT("insects", "حشره و پروانه", "حشره", "۱۰۲۱ گونه‌ی حشره"),
    ANIMAL("animals", "جانوران دیگر و قارچ", "جانوران", "پستانداران، خزندگان، دوزیستان، ماهی، عنکبوتیان، بی‌مهرگان دریایی و قارچ (در سطح گروه)");

    companion object { fun of(key: String): Category? = entries.firstOrNull { it.key == key } }
}

/** What the app knows about a species or group, in Persian (assets/ai/info). */
data class SpeciesInfo(
    val fa: String,
    /** درخت، درختچه، گیاه علفی، حشره، پستاندار… */
    val kind: String = "",
    val note: String = "",
    /** IUCN Red List category: LC, NT, VU, EN, CR. */
    val iucn: String = "",
    /** Presence in Iran: resident, summer / winter visitor… */
    val iran: String = "",
    val habitat: String = "",
    /** Only for the animal model: the group the answer stands for. */
    val group: String = "",
    /** The model has no class for this exact species; this is the nearest look-alike group. */
    val approx: Boolean = false,
    /** Protected in Iran (the environment organisation's list): a sighting must be reported. */
    val protected: Boolean = false,
) {
    val iucnTitle: String get() = when (iucn) {
        "LC" -> "کمترین نگرانی (LC)"
        "NT" -> "در آستانه‌ی تهدید (NT)"
        "VU" -> "آسیب‌پذیر (VU)"
        "EN" -> "در خطر انقراض (EN)"
        "CR" -> "در معرض خطر شدید انقراض (CR)"
        else -> iucn
    }
    val threatened: Boolean get() = iucn in setOf("NT", "VU", "EN", "CR")
}

/** One answer of a model. [info] is null when the app has nothing in Persian about it. */
data class SpeciesGuess(
    val category: Category,
    val scientific: String,
    val english: String,
    val probability: Float,
    val info: SpeciesInfo?,
    /** Persian name of the genus when the species itself is not in the app's list. */
    val genusFa: String? = null,
) {
    /** The best name to show. */
    val title: String
        get() = info?.fa ?: genusFa?.let { "جنس $it" } ?: english.ifBlank { scientific }
    /** True when only the genus (not the species) has a Persian name. */
    val genusOnly: Boolean get() = info == null && genusFa != null
}

/** The answers of one model, best first. [score] is what auto mode ranks the categories by. */
data class CategoryResult(val category: Category, val guesses: List<SpeciesGuess>, val score: Float)

sealed interface Recognition {
    /** None of the models is inside this APK. */
    data object NoModel : Recognition
    /** Nothing the models know seems to be in the photo. */
    data object NothingFound : Recognition
    data class Found(val results: List<CategoryResult>, val auto: Boolean) : Recognition
    data class Error(val message: String) : Recognition
}

/**
 * Offline recognition of living things from a photo with TensorFlow Lite; nothing leaves the phone.
 *
 * Four models (Apache 2.0): the iNaturalist-trained AIY MobileNets for birds, plants and insects (about 1,000 to
 * 2,100 species each), and an ImageNet MobileNet for everything else (mammals, reptiles, amphibians, fish, spiders,
 * marine invertebrates, fungi). The ImageNet one has no classes for Iran's own mammals, so its answers are mapped
 * onto the nearest group (for instance "ibex" stands for the wild goat) and are marked as approximate.
 *
 * Tricks that make the answers steadier: the photo and its mirror image are both judged and averaged; species that
 * the app knows (Persian information, so likely seen in Iran) get a prior; the ImageNet model is restricted to
 * the classes that are living things, and its confidence is scaled by how much of its probability fell on them.
 */
object SpeciesClassifier {
    private const val DIR = "ai"
    /** How much likelier a species (or genus) with Persian information is taken to be. */
    private const val SPECIES_PRIOR = 2.5f
    private const val GENUS_PRIOR = 1.5f
    /** Protected species of Iran are weighted above the rest: they matter most to the ranger. */
    private const val PROTECTED_PRIOR = 4f

    private class Pack(
        val category: Category,
        val tf: Interpreter,
        val scientific: Array<String>,
        val english: Array<String>,
        val background: Int,
        val species: Map<String, SpeciesInfo>,
        val genus: Map<String, String>,
        /** Animal model only: class index to what it stands for. */
        val animals: Map<Int, SpeciesInfo>,
    )

    private val packs = HashMap<Category, Pack?>()

    private fun hasAsset(context: Context, path: String) =
        runCatching { context.assets.open(path).use { true } }.getOrDefault(false)

    fun availableCategories(context: Context): List<Category> =
        Category.entries.filter { hasAsset(context, "$DIR/models/${it.key}.tflite") }

    private fun readText(context: Context, path: String) = context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun infoOf(o: JSONObject) = SpeciesInfo(
        fa = o.optString("fa"), kind = o.optString("kind"), note = o.optString("note"), iucn = o.optString("iucn"),
        iran = o.optString("iran"), habitat = o.optString("habitat"), group = o.optString("group"), approx = o.optBoolean("approx"),
        protected = o.optBoolean("protected"),
    )

    @Synchronized
    private fun pack(context: Context, c: Category): Pack? = packs.getOrPut(c) {
        runCatching {
            val lines = readText(context, "$DIR/labels/${c.key}.txt").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val sci = Array(lines.size) { "" }
            val en = Array(lines.size) { "" }
            lines.forEachIndexed { i, l ->
                if (c == Category.ANIMAL) {
                    en[i] = l.substringBefore(',').trim()
                } else {
                    val m = Regex("^(.*?)(?: \\((.*)\\))?$").matchEntire(l)
                    sci[i] = m?.groupValues?.get(1).orEmpty()
                    en[i] = m?.groupValues?.getOrNull(2).orEmpty()
                }
            }
            val bg = lines.indexOfFirst { it.equals("background", ignoreCase = true) }
            val info = JSONObject(readText(context, "$DIR/info/${c.key}.json"))
            var species = emptyMap<String, SpeciesInfo>()
            var genus = emptyMap<String, String>()
            var animals = emptyMap<Int, SpeciesInfo>()
            if (c == Category.ANIMAL) {
                animals = info.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to infoOf(info.getJSONObject(k)) } }.toMap()
            } else {
                info.optJSONObject("species")?.let { s -> species = s.keys().asSequence().associateWith { infoOf(s.getJSONObject(it)) } }
                info.optJSONObject("genus")?.let { g -> genus = g.keys().asSequence().associateWith { g.getString(it) } }
            }
            val bytes = context.assets.open("$DIR/models/${c.key}.tflite").use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
            Pack(c, Interpreter(buf, Interpreter.Options().setNumThreads(4)), sci, en, bg, species, genus, animals)
        }.getOrNull()
    }

    /** How many species (or groups, for animals) a category can name. */
    fun classCount(context: Context, c: Category): Int = pack(context, c)?.let { p ->
        if (c == Category.ANIMAL) p.animals.size else p.scientific.size - (if (p.background >= 0) 1 else 0)
    } ?: 0

    /** How many species have a Persian name of their own (the rest get their genus's name when it is known). */
    fun persianCount(context: Context, c: Category): Int = pack(context, c)?.let { if (c == Category.ANIMAL) it.animals.size else it.species.size } ?: 0

    /** Call off the main thread. [only] null means auto: try every model and rank the categories. */
    @Synchronized
    fun recognize(context: Context, photo: Bitmap, only: Category? = null, top: Int = 5): Recognition {
        val cats = (if (only != null) listOf(only) else Category.entries).filter { hasAsset(context, "$DIR/models/${it.key}.tflite") }
        if (cats.isEmpty()) return Recognition.NoModel
        return runCatching {
            val square = centerSquare(photo)
            val raw = HashMap<Category, FloatArray>()
            fun probs(c: Category): FloatArray? = raw[c] ?: pack(context, c)?.let { rawProbs(it, square) }?.also { raw[c] = it }

            // Auto mode: every model always names something, so the categories are told apart with the ImageNet model.
            // It says how much of the photo looks like a bird, an insect or another animal; a plant is what is left.
            val gate: Map<Category, Float> = if (only == null) gates(context, probs(Category.ANIMAL)) else emptyMap()

            val results = cats.mapNotNull { c ->
                val p = pack(context, c) ?: return@mapNotNull null
                val pr = probs(c) ?: return@mapNotNull null
                val r = if (c == Category.ANIMAL) animalResult(p, pr, top) else inatResult(p, pr, top)
                if (only == null && c != Category.ANIMAL) r.copy(score = r.score * (gate[c] ?: 1f)) else r
            }
            if (square !== photo) square.recycle()
            if (results.isEmpty()) return Recognition.Error("مدل باز نشد")
            val best = results.maxOf { it.score }
            // a category far behind the leading one is noise; in manual mode the chosen one is always shown
            val kept = results.filter { it.guesses.isNotEmpty() && (only != null || (it.score >= 0.08f && it.score >= 0.2f * best)) }
                .sortedByDescending { it.score }
            if (kept.isEmpty() || (only == null && kept.first().score < 0.05f)) Recognition.NothingFound else Recognition.Found(kept, auto = only == null)
        }.getOrElse { Recognition.Error(it.message ?: "خطای مدل") }
    }

    // ImageNet class numbers (the animal model's outputs) that stand for birds, insects and flowers
    private val IMAGENET_BIRDS = (8..25) + (81..101) + (128..147)
    private val IMAGENET_INSECTS = 301..327
    private val IMAGENET_FLORA = 985..991

    /** How much each category is "present" in the photo, 0..1, from the ImageNet answers (1 when they are not available). */
    private fun gates(context: Context, imagenet: FloatArray?): Map<Category, Float> {
        val animal = pack(context, Category.ANIMAL)
        if (imagenet == null || animal == null) return emptyMap()
        fun sum(idx: Iterable<Int>) = idx.sumOf { (imagenet.getOrNull(it) ?: 0f).toDouble() }.toFloat()
        val birds = sum(IMAGENET_BIRDS)
        val insects = sum(IMAGENET_INSECTS)
        val others = sum(animal.animals.keys)
        val flora = sum(IMAGENET_FLORA)
        val floor = 0.12f
        return mapOf(
            Category.BIRD to maxOf(floor, birds).coerceAtMost(1f),
            Category.INSECT to maxOf(floor, insects).coerceAtMost(1f),
            Category.PLANT to (maxOf(floor, 1f - (birds + insects + others)) + flora).coerceAtMost(1f),
            Category.ANIMAL to 1f,
        )
    }

    private fun centerSquare(src: Bitmap): Bitmap {
        val s = min(src.width, src.height)
        if (src.width == src.height) return src
        return Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
    }

    /** The model's probabilities for the photo and its mirror image, averaged. */
    private fun rawProbs(p: Pack, square: Bitmap): FloatArray {
        val shape = p.tf.getInputTensor(0).shape()
        val h = shape[1]; val w = shape[2]
        val a = run(p.tf, square, w, h, mirror = false)
        val b = run(p.tf, square, w, h, mirror = true)
        return FloatArray(a.size) { (a[it] + b[it]) / 2f }
    }

    private fun inatResult(p: Pack, probs: FloatArray, top: Int): CategoryResult {
        val weighted = FloatArray(probs.size) { i ->
            if (i == p.background) probs[i] else {
                val sci = p.scientific.getOrNull(i).orEmpty()
                probs[i] * when {
                    p.species[sci]?.protected == true -> PROTECTED_PRIOR
                    p.species.containsKey(sci) -> SPECIES_PRIOR
                    p.genus.containsKey(sci.substringBefore(' ')) -> GENUS_PRIOR
                    else -> 1f
                }
            }
        }
        val sum = weighted.sum().takeIf { it > 0f } ?: 1f
        val ranked = weighted.indices.filter { it != p.background }.sortedByDescending { weighted[it] }.take(top)
        val guesses = ranked.map { i ->
            val sci = p.scientific.getOrNull(i).orEmpty()
            SpeciesGuess(p.category, sci, p.english.getOrNull(i).orEmpty(), weighted[i] / sum, p.species[sci], p.genus[sci.substringBefore(' ')])
        }
        return CategoryResult(p.category, guesses, guesses.firstOrNull()?.probability ?: 0f)
    }

    private fun animalResult(p: Pack, probs: FloatArray, top: Int): CategoryResult {
        // only the classes that stand for living things; everything else (objects, scenes) is "not an animal"
        val mass = p.animals.keys.sumOf { (probs.getOrNull(it) ?: 0f).toDouble() }.toFloat()
        if (mass < 0.04f) return CategoryResult(p.category, emptyList(), 0f)
        val scale = min(1f, mass * 2.5f)
        val byName = LinkedHashMap<String, Pair<SpeciesInfo, Float>>()
        for ((idx, info) in p.animals) {
            val q = (probs.getOrNull(idx) ?: 0f) / mass
            val prev = byName[info.fa]
            byName[info.fa] = info to ((prev?.second ?: 0f) + q)
        }
        val guesses = byName.values.sortedByDescending { it.second }.take(top).map { (info, q) ->
            SpeciesGuess(p.category, "", info.fa, q * scale, info)
        }
        return CategoryResult(p.category, guesses, guesses.firstOrNull()?.probability ?: 0f)
    }

    /** One pass of a model; returns probabilities 0..1 for every class. */
    private fun run(tf: Interpreter, square: Bitmap, w: Int, h: Int, mirror: Boolean): FloatArray {
        val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(scaled).apply {
            if (mirror) { val m = Matrix(); m.setScale(-1f, 1f, w / 2f, h / 2f); concat(m) }
            drawBitmap(square, Rect(0, 0, square.width, square.height), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        scaled.recycle()

        val inT = tf.getInputTensor(0)
        val input: ByteBuffer = when (inT.dataType()) {
            DataType.FLOAT32 -> ByteBuffer.allocateDirect(w * h * 3 * 4).order(ByteOrder.nativeOrder()).apply {
                // MobileNet float models expect -1..1
                for (p in pixels) {
                    putFloat(((p shr 16) and 0xFF) / 127.5f - 1f); putFloat(((p shr 8) and 0xFF) / 127.5f - 1f); putFloat((p and 0xFF) / 127.5f - 1f)
                }
            }
            else -> ByteBuffer.allocateDirect(w * h * 3).order(ByteOrder.nativeOrder()).apply {
                for (p in pixels) {
                    put(((p shr 16) and 0xFF).toByte()); put(((p shr 8) and 0xFF).toByte()); put((p and 0xFF).toByte())
                }
            }
        }
        input.rewind()

        val outT = tf.getOutputTensor(0)
        val n = outT.shape().last()
        return when (outT.dataType()) {
            DataType.FLOAT32 -> {
                val out = Array(1) { FloatArray(n) }
                tf.run(input, out)
                out[0]
            }
            else -> {
                val out = Array(1) { ByteArray(n) }
                tf.run(input, out)
                val q = outT.quantizationParams()
                val scale = if (q.scale > 0f) q.scale else 1f / 255f
                FloatArray(n) { ((out[0][it].toInt() and 0xFF) - q.zeroPoint) * scale }
            }
        }
    }
}
