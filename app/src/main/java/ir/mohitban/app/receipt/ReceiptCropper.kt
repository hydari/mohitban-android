package ir.mohitban.app.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offline receipt boundary detection and perspective correction (no OpenCV / Play Services).
 *
 * Detection: the picture is reduced to ~320 px, an Otsu threshold separates the bright paper
 * from the background, the largest bright region is kept and its four extreme corners are used.
 * The user can then move each corner before [warp] straightens the receipt.
 */
object ReceiptCropper {

    /** Corners in bitmap coordinates: top-left, top-right, bottom-right, bottom-left. */
    fun detect(src: Bitmap): List<PointF> {
        val fallback = defaultCorners(src.width, src.height)
        return runCatching { detectInternal(src) }.getOrNull() ?: fallback
    }

    fun defaultCorners(w: Int, h: Int, inset: Float = 0.08f): List<PointF> {
        val dx = w * inset
        val dy = h * inset
        return listOf(PointF(dx, dy), PointF(w - dx, dy), PointF(w - dx, h - dy), PointF(dx, h - dy))
    }

    private fun detectInternal(src: Bitmap): List<PointF>? {
        val scale = 320f / max(src.width, src.height)
        val w = max(16, (src.width * scale).roundToInt())
        val h = max(16, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        // luminance + histogram
        val lum = IntArray(w * h)
        val hist = IntArray(256)
        for (i in px.indices) {
            val c = px[i]
            val l = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            lum[i] = l
            hist[l]++
        }
        val threshold = otsu(hist, w * h)
        val mask = BooleanArray(w * h) { lum[it] > threshold }

        // largest 4-connected bright component
        val label = IntArray(w * h)
        var best = -1
        var bestSize = 0
        var current = 0
        val queue = IntArray(w * h)
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            current++
            var head = 0
            var tail = 0
            queue[tail++] = start
            label[start] = current
            var size = 0
            while (head < tail) {
                val p = queue[head++]
                size++
                val x = p % w
                val y = p / w
                if (x > 0) { val q = p - 1; if (mask[q] && label[q] == 0) { label[q] = current; queue[tail++] = q } }
                if (x < w - 1) { val q = p + 1; if (mask[q] && label[q] == 0) { label[q] = current; queue[tail++] = q } }
                if (y > 0) { val q = p - w; if (mask[q] && label[q] == 0) { label[q] = current; queue[tail++] = q } }
                if (y < h - 1) { val q = p + w; if (mask[q] && label[q] == 0) { label[q] = current; queue[tail++] = q } }
            }
            if (size > bestSize) { bestSize = size; best = current }
        }
        val area = w * h
        // a receipt should cover a meaningful part of the photo but not the entire frame
        if (best < 0 || bestSize < area * 0.06f || bestSize > area * 0.92f) return null

        var tl = PointF(); var tr = PointF(); var br = PointF(); var bl = PointF()
        var minSum = Float.MAX_VALUE; var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE; var maxDiff = -Float.MAX_VALUE
        for (i in label.indices) {
            if (label[i] != best) continue
            val x = (i % w).toFloat()
            val y = (i / w).toFloat()
            val s = x + y
            val d = x - y
            if (s < minSum) { minSum = s; tl = PointF(x, y) }
            if (s > maxSum) { maxSum = s; br = PointF(x, y) }
            if (d > maxDiff) { maxDiff = d; tr = PointF(x, y) }
            if (d < minDiff) { minDiff = d; bl = PointF(x, y) }
        }
        val inv = 1f / scale
        val corners = listOf(tl, tr, br, bl).map { PointF(it.x * inv, it.y * inv) }
        // reject degenerate shapes
        val wTop = dist(corners[0], corners[1])
        val hLeft = dist(corners[0], corners[3])
        if (wTop < src.width * 0.15f || hLeft < src.height * 0.15f) return null
        return corners
    }

    private fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0
        var maxVar = 0.0
        var threshold = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; threshold = t }
        }
        return threshold
    }

    private fun dist(a: PointF, b: PointF) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    /** Straightens the quadrilateral into a rectangle and slightly boosts contrast (scanner look). */
    fun warp(src: Bitmap, corners: List<PointF>, enhance: Boolean = true, maxSide: Int = 1800): Bitmap {
        require(corners.size == 4)
        val (tl, tr, br, bl) = corners
        var outW = max(dist(tl, tr), dist(bl, br))
        var outH = max(dist(tl, bl), dist(tr, br))
        val s = min(1f, maxSide / max(outW, outH))
        outW = max(40f, outW * s)
        outH = max(40f, outH * s)
        val srcPts = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dstPts = floatArrayOf(0f, 0f, outW, 0f, outW, outH, 0f, outH)
        val m = Matrix()
        m.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        val out = Bitmap.createBitmap(outW.roundToInt(), outH.roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (enhance) {
            val contrast = 1.25f
            val translate = (-0.5f * contrast + 0.5f) * 255f + 10f
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        c.drawBitmap(src, m, paint)
        return out
    }

    fun rotate90(src: Bitmap): Bitmap {
        val m = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
