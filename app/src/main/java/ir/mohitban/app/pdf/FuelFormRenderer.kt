package ir.mohitban.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import ir.mohitban.app.R
import ir.mohitban.app.core.text.amountText
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.FuelType
import ir.mohitban.app.pdf.PdfKit.Align
import java.io.File
import java.io.FileOutputStream

/**
 * The official "فرم هزینه سوخت" of the Department of Environment, redrawn with exactly the
 * fields and positions of the paper form, on half an A5 sheet (A6, 105 × 148 mm).
 *  - page 1: the form (driver / passenger signatures, empty box for the gas station stamp,
 *            box where the POS slip is stapled)
 *  - page 2: the photographed POS receipt (if any)
 */
class FuelFormRenderer(private val context: Context, private val files: FileStore) {

    companion object {
        const val W = 298f   // A6 width in PostScript points
        const val H = 420f   // A6 height
        private const val M = 16f
    }

    private val emblem: Bitmap? by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.doe_emblem)
    }

    fun createPdf(entry: FuelEntry): File {
        val doc = PdfDocument()
        val p1 = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
        draw(p1.canvas, entry)
        doc.finishPage(p1)
        val receipt = files.loadBitmap(entry.receiptPath, 1800)
        if (receipt != null) {
            val p2 = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 2).create())
            drawReceiptPage(p2.canvas, entry, receipt)
            doc.finishPage(p2)
        }
        val out = File(files.cacheDir, "fuel-form-${entry.date.format().replace('/', '-')}-${entry.id}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    /** The form as a 300 dpi PNG (1240 × 1754 px) for quick sending in messengers. */
    fun createPng(entry: FuelEntry): File {
        val scale = 1240f / W
        val bmp = Bitmap.createBitmap(1240, (H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale, scale)
        draw(c, entry)
        val out = File(files.cacheDir, "fuel-form-${entry.date.format().replace('/', '-')}-${entry.id}.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return out
    }

    // ------------------------------------------------------------------ drawing

    private sealed interface Seg {
        data class Label(val text: String) : Seg
        data class Value(val text: String, val weight: Float = 1f, val fixed: Float = 0f) : Seg
    }

    /** Draws one "label ..... value ..... label" row from right to left like the paper form. */
    private fun row(c: Canvas, y: Float, segs: List<Seg>, right: Float = W - M, left: Float = M) {
        val label = PdfKit.text(9.5f, true)
        val value = PdfKit.text(12f, true, PdfKit.INK)
        val gap = 3f
        val fixed = segs.sumOf {
            when (it) {
                is Seg.Label -> (label.measureText(it.text) + gap).toDouble()
                is Seg.Value -> (it.fixed + gap).toDouble()
            }
        }.toFloat()
        val weights = segs.filterIsInstance<Seg.Value>().filter { it.fixed == 0f }.sumOf { it.weight.toDouble() }.toFloat()
        val flexTotal = (right - left - fixed).coerceAtLeast(10f)
        var x = right
        val dots = PdfKit.dotted(1.1f, Color.rgb(95, 105, 95))
        for (s in segs) {
            when (s) {
                is Seg.Label -> {
                    PdfKit.line(c, s.text, x, y, label, Align.RIGHT)
                    x -= label.measureText(s.text) + gap
                }
                is Seg.Value -> {
                    val w = if (s.fixed > 0f) s.fixed else flexTotal * (s.weight / weights.coerceAtLeast(0.01f))
                    c.drawLine(x - w, y + 2f, x, y + 2f, dots)
                    val txt = s.text
                    if (txt.isNotEmpty()) {
                        val muted = txt == "—"
                        val p = if (muted) PdfKit.text(10f, false, PdfKit.MUTED) else value
                        PdfKit.fitLine(c, txt, x - w / 2, y - 1f, p, w - 4f, Align.CENTER)
                    }
                    x -= w + gap
                }
            }
        }
    }

    fun draw(c: Canvas, e: FuelEntry) {
        val s = e.snapshot
        // ---------- header ----------
        emblem?.let { PdfKit.bitmapFit(c, it, RectF(W / 2 - 18f, 10f, W / 2 + 18f, 46f)) }
        PdfKit.line(c, "ریاست جمهوری", W / 2, 59f, PdfKit.text(9.5f, true), Align.CENTER)
        PdfKit.fitLine(c, "اداره کل حفاظت محیط زیست ${s.province}", W / 2, 72f, PdfKit.text(9.2f, true), W - 2 * M, Align.CENTER)
        c.drawLine(62f, 80f, W - 62f, 80f, PdfKit.stroke(1.3f, PdfKit.GREEN))
        PdfKit.line(c, "فرم هزینه سوخت خودرو", W / 2, 91f, PdfKit.text(7.5f, false, PdfKit.GREEN), Align.CENTER)

        // ---------- city (centred, as on the paper form) ----------
        run {
            val lab = PdfKit.text(9.5f, true)
            val labW = lab.measureText("شهرستان")
            val valW = 92f
            val total = labW + 4f + valW
            val right = W / 2 + total / 2
            row(c, 110f, listOf(Seg.Label("شهرستان"), Seg.Value(s.city, fixed = valW)), right = right, left = right - total)
        }

        // ---------- vehicle number with the red plate ----------
        run {
            val y = 141f
            val lab = PdfKit.text(9.5f, true)
            PdfKit.line(c, "شماره خودرو :", W - M, y, lab, Align.RIGHT)
            val labW = lab.measureText("شماره خودرو :") + 6f
            val areaRight = W - M - labW
            val areaLeft = M
            c.drawLine(areaLeft, y + 2f, areaRight, y + 2f, PdfKit.dotted(1.1f, Color.rgb(95, 105, 95)))
            // centre the plate over the dotted line
            val tmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val probe = Canvas(tmp)
            val plateH = if (s.plate is ir.mohitban.app.domain.model.Plate.Car) 27f else 20f
            val pw = PdfKit.plate(probe, s.plate, 400f, 0f, plateH)
            tmp.recycle()
            val cx = (areaLeft + areaRight) / 2
            val cy = if (s.plate is ir.mohitban.app.domain.model.Plate.Car) y - 9f else y - 10f
            c.drawRect(cx - pw / 2 - 3f, cy - plateH, cx + pw / 2 + 3f, y + 4f, PdfKit.fill(Color.WHITE))
            PdfKit.plate(c, s.plate, cx + pw / 2, cy, plateH)
        }

        // ---------- odometer ----------
        row(c, 168f, listOf(Seg.Label("کیلومتر فعلی"), Seg.Value(e.odometer.rial())))

        // ---------- gasoline / gas lines ----------
        val isGas = e.fuelType == FuelType.CNG
        row(
            c, 194f,
            listOf(
                Seg.Label("مقدار بنزین"),
                Seg.Value(if (!isGas) e.amount.amountText() else "—", 1f),
                Seg.Label("لیتر"),
                Seg.Label("فی:"),
                Seg.Value(if (!isGas) e.unitPrice.rial() else "—", 1.15f),
                Seg.Label("ریال"),
            ),
        )
        row(
            c, 220f,
            listOf(
                Seg.Label("مقدار گاز :"),
                Seg.Value(if (isGas) e.amount.amountText() else "—", 1f),
                Seg.Label("کیلو"),
                Seg.Label("فی"),
                Seg.Value(if (isGas) e.unitPrice.rial() else "—", 1.15f),
                Seg.Label("ریال"),
            ),
        )

        // ---------- date & total ----------
        row(
            c, 248f,
            listOf(
                Seg.Label("تاریخ :"),
                Seg.Value(e.date.format().fa(), 1f),
                Seg.Label("جمع:"),
                Seg.Value(e.total.rial(), 1.25f),
                Seg.Label("ریال"),
            ),
        )

        // ---------- signatures ----------
        val sigLabel = PdfKit.text(9.5f, true)
        val half = (W - 2 * M) / 2
        val rightCenter = W - M - half / 2
        val leftCenter = M + half / 2
        PdfKit.line(c, "امضاء راننده", rightCenter, 272f, sigLabel, Align.CENTER)
        PdfKit.line(c, "امضاء سرنشین", leftCenter, 272f, sigLabel, Align.CENTER)
        files.loadBitmap(e.driverSignaturePath, 700)?.let {
            PdfKit.bitmapFit(c, it, RectF(rightCenter - 52f, 276f, rightCenter + 52f, 312f))
        }
        PdfKit.line(c, e.snapshot.driverName, rightCenter, 320f, PdfKit.text(7f, false, PdfKit.MUTED), Align.CENTER)
        val passenger = files.loadBitmap(e.passengerSignaturePath, 700)
        if (passenger != null) {
            PdfKit.bitmapFit(c, passenger, RectF(leftCenter - 52f, 276f, leftCenter + 52f, 312f))
        } else {
            PdfKit.line(c, "—", leftCenter, 298f, PdfKit.text(10f, false, PdfKit.MUTED), Align.CENTER)
        }

        // ---------- stamp box & POS slip box ----------
        val top = 330f
        val bottom = 400f
        val boxGap = 8f
        val boxW = (W - 2 * M - boxGap) / 2
        val stampBox = RectF(W - M - boxW, top, W - M, bottom)
        val slipBox = RectF(M, top, M + boxW, bottom)
        val dash = PdfKit.dashed(0.9f, Color.rgb(120, 135, 118), 3.5f, 2.5f)
        c.drawRoundRect(stampBox, 6f, 6f, dash)
        c.drawRoundRect(slipBox, 6f, 6f, dash)
        PdfKit.paragraph(c, "مهر و امضاء فروشنده شرکت نفت", stampBox.right - 4f, top + 5f, boxW - 8f, PdfKit.text(8.2f, true), Align.CENTER)
        PdfKit.line(c, "محل الصاق رسید پوز", slipBox.centerX(), top + 14f, PdfKit.text(8f, false, PdfKit.MUTED), Align.CENTER)
        // staple mark
        val sx = slipBox.centerX()
        val st = PdfKit.stroke(1.4f, PdfKit.MUTED)
        c.drawLine(sx - 11f, top + 33f, sx + 11f, top + 33f, st)
        c.drawLine(sx - 11f, top + 33f, sx - 11f, top + 37f, st)
        c.drawLine(sx + 11f, top + 33f, sx + 11f, top + 37f, st)
        PdfKit.line(c, "منگنه", sx, top + 48f, PdfKit.text(7f, false, PdfKit.MUTED), Align.CENTER)
        if (e.receiptPath != null) {
            PdfKit.line(c, "تصویر رسید در صفحه ۲", sx, top + 60f, PdfKit.text(6.5f, false, PdfKit.GREEN), Align.CENTER)
        }

        // ---------- footer ----------
        val foot = if (e.id > 0) "شماره ثبت ${e.id.fa()} | تهیه‌شده با اپلیکیشن محیط‌یار" else "تهیه‌شده با اپلیکیشن محیط‌یار"
        PdfKit.line(c, foot, W / 2, 413f, PdfKit.text(5.8f, false, Color.rgb(140, 150, 142)), Align.CENTER)
    }

    private fun drawReceiptPage(c: Canvas, e: FuelEntry, receipt: Bitmap) {
        PdfKit.line(c, "پیوست فرم هزینه سوخت | رسید کارتخوان", W / 2, 24f, PdfKit.text(9f, true), Align.CENTER)
        PdfKit.line(
            c, "${e.date.formatLong().fa()} | ${e.fuelType.short} | ${e.total.rial()} ریال",
            W / 2, 37f, PdfKit.text(7.5f, false, PdfKit.MUTED), Align.CENTER,
        )
        val box = RectF(M, 46f, W - M, H - M)
        PdfKit.bitmapFit(c, receipt, box)
        c.drawRect(box, PdfKit.stroke(0.5f, Color.rgb(210, 214, 210)))
    }
}
