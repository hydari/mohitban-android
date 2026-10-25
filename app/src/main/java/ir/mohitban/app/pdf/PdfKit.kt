package ir.mohitban.app.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.domain.model.Plate
import ir.mohitban.app.ui.theme.AppFonts
import kotlin.math.max
import kotlin.math.min

/** Small drawing toolkit shared by the A6 fuel form and the A4 quarterly report (RTL aware). */
object PdfKit {
    val GREEN = Color.rgb(31, 107, 58)
    val GREEN_LIGHT = Color.rgb(227, 237, 220)
    val INK = Color.rgb(28, 32, 30)
    val MUTED = Color.rgb(110, 118, 112)
    val LINE = Color.rgb(154, 165, 150)
    val PLATE_RED = Color.rgb(198, 40, 40)
    val PLATE_BLUE = Color.rgb(29, 79, 163)

    enum class Align { RIGHT, CENTER, LEFT }

    fun text(size: Float, bold: Boolean = false, color: Int = INK): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (bold) AppFonts.androidBold else AppFonts.androidRegular
            textSize = size
            this.color = color
        }

    /** Single line; [x] is the right edge, centre or left edge depending on [align]; [y] is the baseline. */
    fun line(c: Canvas, s: String, x: Float, y: Float, p: TextPaint, align: Align = Align.RIGHT) {
        p.textAlign = when (align) {
            Align.RIGHT -> Paint.Align.RIGHT
            Align.CENTER -> Paint.Align.CENTER
            Align.LEFT -> Paint.Align.LEFT
        }
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** Text that shrinks to fit [maxWidth]. */
    fun fitLine(c: Canvas, s: String, x: Float, y: Float, p: TextPaint, maxWidth: Float, align: Align = Align.RIGHT) {
        val original = p.textSize
        while (p.measureText(s) > maxWidth && p.textSize > 5f) p.textSize -= 0.5f
        line(c, s, x, y, p, align)
        p.textSize = original
    }

    /** Multi-line paragraph inside a box of [width] whose right edge is [right]; returns the height used. */
    fun paragraph(c: Canvas, s: String, right: Float, top: Float, width: Float, p: TextPaint, align: Align = Align.RIGHT, spacing: Float = 1.25f): Float {
        val alignment = when (align) {
            Align.RIGHT -> Layout.Alignment.ALIGN_NORMAL
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.LEFT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = if (android.os.Build.VERSION.SDK_INT >= 23) {
            StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt())
                .setAlignment(alignment)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(s, p, width.toInt(), alignment, spacing, 0f, false)
        }
        c.save()
        c.translate(right - width, top)
        layout.draw(c)
        c.restore()
        return layout.height.toFloat()
    }

    fun dotted(width: Float = 0.9f, color: Int = LINE) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = width
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(0.1f, 2.4f), 0f)
    }

    fun dashed(width: Float = 0.9f, color: Int = LINE, on: Float = 4f, off: Float = 3f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = width
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(on, off), 0f)
    }

    fun stroke(width: Float = 0.8f, color: Int = LINE) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = width
        style = Paint.Style.STROKE
    }

    fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    /** Draws [bmp] fitted (keeping aspect ratio) and centred inside [box]. */
    fun bitmapFit(c: Canvas, bmp: Bitmap, box: RectF, alpha: Int = 255) {
        val s = min(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        c.drawBitmap(bmp, null, dst, p)
    }

    /**
     * Red government plate. [right] is the right edge, [cy] the vertical centre, [h] the height.
     * Returns the drawn width.
     */
    fun plate(c: Canvas, plate: Plate, right: Float, cy: Float, h: Float): Float = when (plate) {
        is Plate.Car -> carPlate(c, plate, right, cy, h)
        is Plate.Motorcycle -> motoPlate(c, plate, right, cy, h * 1.45f)
    }

    private fun flag(c: Canvas, left: Float, top: Float, w: Float, h: Float) {
        c.drawRect(left, top, left + w, top + h, fill(PLATE_BLUE))
        val sw = w * 0.56f
        val sh = h * 0.07f
        val sx = left + (w - sw) / 2
        var sy = top + h * 0.14f
        listOf(Color.rgb(35, 159, 64), Color.WHITE, Color.rgb(218, 0, 0)).forEach {
            c.drawRect(sx, sy, sx + sw, sy + sh, fill(it)); sy += sh
        }
        // "I.R. IRAN" under the flag, like the real plate
        val t = text(minOf(w * 0.3f, h * 0.15f), true, Color.WHITE)
        line(c, "I.R.", left + w / 2, top + h * 0.62f, t, Align.CENTER)
        line(c, "IRAN", left + w / 2, top + h * 0.82f, t, Align.CENTER)
    }

    private fun carPlate(c: Canvas, p: Plate.Car, right: Float, cy: Float, h: Float): Float {
        val big = text(h * 0.5f, true, Color.WHITE)
        val letter = text(h * 0.4f, true, Color.WHITE)
        val small = text(h * 0.17f, true, Color.WHITE)
        val code = text(h * 0.4f, true, Color.WHITE)
        val gap = h * 0.16f
        val pad = h * 0.2f
        val mainW = big.measureText(p.first.fa()) + letter.measureText(p.letter) + big.measureText(p.middle.fa()) + gap * 2 + pad * 2
        val cityW = max(code.measureText(p.iranCode.fa()), small.measureText("ایران")) + h * 0.28f
        val flagW = h * 0.34f
        val w = flagW + mainW + cityW
        val left = right - w
        val top = cy - h / 2
        val rect = RectF(left, top, right, top + h)
        val r = h * 0.12f
        c.drawRoundRect(rect, r, r, fill(PLATE_RED))
        c.save()
        c.clipRect(left, top, left + flagW, top + h)
        c.drawRoundRect(rect, r, r, fill(PLATE_BLUE))
        c.restore()
        flag(c, left, top, flagW, h)
        // main digits (always laid out left → right like the real plate)
        var x = left + flagW + pad
        val base = cy + big.textSize * 0.36f
        line(c, p.first.fa(), x, base, big, Align.LEFT); x += big.measureText(p.first.fa()) + gap
        line(c, p.letter, x, cy + letter.textSize * 0.36f, letter, Align.LEFT); x += letter.measureText(p.letter) + gap
        line(c, p.middle.fa(), x, base, big, Align.LEFT)
        val sepX = left + flagW + mainW
        c.drawRect(sepX, top, sepX + h * 0.045f, top + h, fill(Color.WHITE))
        val cx = sepX + cityW / 2 + h * 0.02f
        line(c, "ایران", cx, top + h * 0.33f, small, Align.CENTER)
        line(c, p.iranCode.fa(), cx, top + h * 0.82f, code, Align.CENTER)
        c.drawRoundRect(rect, r, r, stroke(h * 0.05f, Color.rgb(27, 27, 27)))
        return w
    }

    private fun motoPlate(c: Canvas, p: Plate.Motorcycle, right: Float, cy: Float, h: Float): Float {
        val t1 = text(h * 0.3f, true, Color.WHITE)
        val t2 = text(h * 0.33f, true, Color.WHITE)
        val flagW = h * 0.24f
        val bodyW = max(max(t1.measureText(p.top.fa()), t2.measureText(p.bottom.fa())) + h * 0.4f, h * 1.1f)
        val w = flagW + bodyW
        val left = right - w
        val top = cy - h / 2
        val rect = RectF(left, top, right, top + h)
        val r = h * 0.1f
        c.drawRoundRect(rect, r, r, fill(PLATE_RED))
        c.save(); c.clipRect(left, top, left + flagW, top + h); c.drawRoundRect(rect, r, r, fill(PLATE_BLUE)); c.restore()
        flag(c, left, top, flagW, h)
        val cx = left + flagW + bodyW / 2
        line(c, p.top.fa(), cx, top + h * 0.38f, t1, Align.CENTER)
        c.drawRect(left + flagW + h * 0.12f, top + h * 0.47f, right - h * 0.12f, top + h * 0.5f, fill(Color.WHITE))
        line(c, p.bottom.fa(), cx, top + h * 0.86f, t2, Align.CENTER)
        c.drawRoundRect(rect, r, r, stroke(h * 0.035f, Color.rgb(27, 27, 27)))
        return w
    }
}
