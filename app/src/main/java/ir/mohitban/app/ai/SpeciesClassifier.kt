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

/** What the app knows about a bird of Iran (assets/ai/birds_fa.json). */
data class SpeciesInfo(
    val fa: String,
    val en: String,
    /** IUCN Red List category: LC, NT, VU, EN, CR. */
    val iucn: String,
    /** Presence in Iran: resident, summer / winter visitor… */
    val iran: String,
    val habitat: String,
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

/** One answer of the recogniser. [info] is null for a species that is not in the app's list of Iran's birds. */
data class SpeciesGuess(val scientific: String, val probability: Float, val info: SpeciesInfo?)

sealed interface Recognition {
    /** The model is not inside this APK (it is downloaded when the APK is built). */
    data object NoModel : Recognition
    /** The model answered "background": no bird it knows. */
    data object NothingFound : Recognition
    data class Found(val guesses: List<SpeciesGuess>) : Recognition
    data class Error(val message: String) : Recognition
}

/**
 * Offline bird recognition from a photo with TensorFlow Lite: Google's AIY "birds_V1" model (MobileNet, 965 species
 * of the world, Apache 2.0). Nothing leaves the phone.
 *
 * The model knows birds of the whole world, many of them from the Americas. A photo taken in Iran is far more likely
 * to show a bird of Iran, so the species in the app's Iran list get a higher weight (a prior) before the answers are
 * ranked. The photo and its mirror image are both judged and averaged, which makes the answer a little steadier.
 */
object SpeciesClassifier {
    private const val MODEL = "ai/birds_v1.tflite"
    private const val LABELS = "ai/birds_labels.csv"
    private const val INFO = "ai/birds_fa.json"
    private const val BACKGROUND = "background"
    /** How much more likely a bird of Iran is taken to be than any other bird of the world. */
    private const val IRAN_PRIOR = 4f

    private var interpreter: Interpreter? = null
    private var labels: Array<String> = emptyArray()
    private var info: Map<String, SpeciesInfo> = emptyMap()
    private var loaded = false

    fun available(context: Context): Boolean =
        runCatching { context.assets.open(MODEL).use { it.read() >= 0 } }.getOrDefault(false)

    /** How many species of Iran the app has Persian information for. */
    fun iranSpeciesCount(context: Context): Int { load(context); return info.size }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        info = runCatching {
            val o = JSONObject(context.assets.open(INFO).use { it.readBytes().toString(Charsets.UTF_8) })
            o.keys().asSequence().associateWith { k ->
                val x = o.getJSONObject(k)
                SpeciesInfo(x.optString("fa"), x.optString("en"), x.optString("iucn"), x.optString("iran"), x.optString("habitat"))
            }
        }.getOrDefault(emptyMap())
        val rows = runCatching {
            context.assets.open(LABELS).use { it.readBytes().toString(Charsets.UTF_8) }.lineSequence().drop(1)
                .mapNotNull { line -> line.split(',', limit = 2).takeIf { it.size == 2 }?.let { (id, name) -> id.trim().toIntOrNull()?.let { it to name.trim() } } }
                .toList()
        }.getOrDefault(emptyList())
        val size = (rows.maxOfOrNull { it.first } ?: -1) + 1
        labels = Array(size) { "" }.also { arr -> rows.forEach { (i, n) -> arr[i] = n } }
        interpreter = runCatching {
            // read into a direct buffer: works whether or not the asset is stored compressed
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
            Interpreter(buf, Interpreter.Options().setNumThreads(4))
        }.getOrNull()
    }

    fun infoOf(context: Context, scientific: String): SpeciesInfo? { load(context); return info[scientific] }

    /** Call off the main thread. [photo] is best a crop with the bird in the middle. */
    @Synchronized
    fun recognize(context: Context, photo: Bitmap, top: Int = 5): Recognition {
        load(context)
        val tf = interpreter ?: return if (available(context)) Recognition.Error("مدل باز نشد") else Recognition.NoModel
        return runCatching {
            val input = tf.getInputTensor(0)
            val shape = input.shape() // [1, h, w, 3]
            val h = shape[1]; val w = shape[2]
            val square = centerSquare(photo)
            val a = run(tf, square, w, h, mirror = false)
            val b = run(tf, square, w, h, mirror = true)
            if (square !== photo) square.recycle()
            val scores = FloatArray(a.size) { (a[it] + b[it]) / 2f }

            val bgIndex = labels.indexOf(BACKGROUND)
            // the prior: birds of Iran first
            val weighted = FloatArray(scores.size) { i ->
                val name = labels.getOrNull(i).orEmpty()
                if (i == bgIndex) scores[i] else scores[i] * (if (info.containsKey(name)) IRAN_PRIOR else 1f)
            }
            val sum = weighted.sum().takeIf { it > 0f } ?: 1f
            val ranked = weighted.indices.sortedByDescending { weighted[it] }
            if (ranked.firstOrNull() == bgIndex && weighted[bgIndex] / sum > 0.5f) return Recognition.NothingFound
            val guesses = ranked.asSequence().filter { it != bgIndex }.take(top).map { i ->
                val name = labels.getOrNull(i).orEmpty()
                SpeciesGuess(name, weighted[i] / sum, info[name])
            }.filter { it.scientific.isNotBlank() }.toList()
            if (guesses.isEmpty() || guesses.first().probability < 0.03f) Recognition.NothingFound else Recognition.Found(guesses)
        }.getOrElse { Recognition.Error(it.message ?: "خطای مدل") }
    }

    private fun centerSquare(src: Bitmap): Bitmap {
        val s = min(src.width, src.height)
        if (src.width == src.height) return src
        return Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
    }

    /** One pass of the model; returns probabilities 0..1 for every class. */
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
                for (p in pixels) {
                    putFloat(((p shr 16) and 0xFF) / 255f); putFloat(((p shr 8) and 0xFF) / 255f); putFloat((p and 0xFF) / 255f)
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
