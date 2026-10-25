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
 * One page: the form with driver / passenger signatures, an empty box for the gas station
 * stamp, and the photographed POS receipt placed in the "محل الصاق رسید پوز" box.
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

    /**
     * Draws one row from right to left exactly like the paper form, without dotted lines:
     * bold labels, bold values centred in their slots, and a ruled line through the slot
     * when the field does not apply (e.g. the gas line of a gasoline entry).
     */
    private fun row(c: Canvas, y: Float, segs: List<Seg>, right: Float = W - M, left: Float = M) {
        val label = PdfKit.text(9.8f, true)
        val value = PdfKit.text(11f, true, Color.rgb(12, 16, 14))
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
        for (s in segs) {
            when (s) {
                is Seg.Label -> {
                    PdfKit.line(c, s.text, x, y, label, Align.RIGHT)
                    x -= label.measureText(s.text) + gap
                }
                is Seg.Value -> {
                    val w = if (s.fixed > 0f) s.fixed else flexTotal * (s.weight / weights.coerceAtLeast(0.01f))
                    if (s.text == "—" || s.text.isEmpty()) {
                        // not applicable: rule a line through the empty slot
                        c.drawLine(x - w * 0.88f, y - 3.5f, x - w * 0.12f, y - 3.5f, PdfKit.stroke(1.1f, Color.rgb(40, 44, 42)))
                    } else {
                        PdfKit.fitLine(c, s.text, x - w / 2, y, value, w - 4f, Align.CENTER)
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
        PdfKit.line(c, "ریاست جمهوری", W / 2, 59f, PdfKit.text(9.8f, true), Align.CENTER)
        PdfKit.fitLine(c, "اداره کل حفاظت محیط زیست ${s.province}", W / 2, 72f, PdfKit.text(9.5f, true), W - 2 * M, Align.CENTER)
        c.drawLine(62f, 80f, W - 62f, 80f, PdfKit.stroke(1.3f, PdfKit.GREEN))
        PdfKit.line(c, "فرم هزینه سوخت خودرو", W / 2, 91f, PdfKit.text(7.5f, true, PdfKit.GREEN), Align.CENTER)

        // ---------- city: centred on the page ----------
        run {
            val lab = PdfKit.text(9.8f, true)
            val v = PdfKit.text(11f, true, Color.rgb(12, 16, 14))
            val labText = "شهرستان :"
            val gap = 6f
            val total = lab.measureText(labText) + gap + v.measureText(s.city)
            val right = W / 2 + total / 2
            PdfKit.line(c, labText, right, 113f, lab, Align.RIGHT)
            PdfKit.line(c, s.city, right - lab.measureText(labText) - gap, 113f, v, Align.RIGHT)
        }

        // ---------- vehicle number with a larger red plate ----------
        run {
            val y = 145f
            val lab = PdfKit.text(9.8f, true)
            val labText = if (s.vehicleType == ir.mohitban.app.domain.model.VehicleType.MOTORCYCLE) "شماره موتورسیکلت :" else "شماره خودرو :"
            PdfKit.line(c, labText, W - M, y, lab, Align.RIGHT)
            val areaRight = W - M - lab.measureText(labText) - 6f
            val isCar = s.plate is ir.mohitban.app.domain.model.Plate.Car
            val plateH = if (isCar) 27f else 21f
            val tmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val pw = PdfKit.plate(Canvas(tmp), s.plate, 400f, 0f, plateH)
            tmp.recycle()
            val cx = (M + areaRight) / 2
            val cy = if (isCar) y - 9f else y - 12f
            PdfKit.plate(c, s.plate, cx + pw / 2, cy, plateH)
        }

        // ---------- odometer ----------
        row(c, 176f, listOf(Seg.Label("کیلومتر فعلی :"), Seg.Value(e.odometer.rial())))

        // ---------- gasoline / gas lines (the line that does not apply is ruled through) ----------
        val isGas = e.fuelType == FuelType.CNG
        row(
            c, 202f,
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
            c, 228f,
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
            c, 255f,
            listOf(
                Seg.Label("تاریخ :"),
                Seg.Value(e.date.format().fa(), 1f),
                Seg.Label("جمع:"),
                Seg.Value(e.total.rial(), 1.25f),
                Seg.Label("ریال"),
            ),
        )

        // ---------- signatures ----------
        val sigLabel = PdfKit.text(9.8f, true)
        val half = (W - 2 * M) / 2
        val rightCenter = W - M - half / 2
        val leftCenter = M + half / 2
        PdfKit.line(c, "امضاء راننده", rightCenter, 276f, sigLabel, Align.CENTER)
        PdfKit.line(c, "امضاء سرنشین", leftCenter, 276f, sigLabel, Align.CENTER)
        files.loadBitmap(e.driverSignaturePath, 700)?.let {
            PdfKit.bitmapFit(c, it, RectF(rightCenter - 52f, 280f, rightCenter + 52f, 314f))
        }
        PdfKit.line(c, e.snapshot.driverName, rightCenter, 322f, PdfKit.text(7f, true, PdfKit.MUTED), Align.CENTER)
        val passenger = files.loadBitmap(e.passengerSignaturePath, 700)
        if (passenger != null) {
            PdfKit.bitmapFit(c, passenger, RectF(leftCenter - 52f, 280f, leftCenter + 52f, 314f))
        } else {
            c.drawLine(leftCenter - 24f, 298f, leftCenter + 24f, 298f, PdfKit.stroke(1.1f, Color.rgb(40, 44, 42)))
        }

        // ---------- stamp box & POS slip box (the receipt photo is placed in its box) ----------
        val top = 330f
        val bottom = 404f
        val boxGap = 8f
        val boxW = (W - 2 * M - boxGap) / 2
        val stampBox = RectF(W - M - boxW, top, W - M, bottom)
        val slipBox = RectF(M, top, M + boxW, bottom)
        val dash = PdfKit.dashed(0.9f, Color.rgb(120, 135, 118), 3.5f, 2.5f)
        c.drawRoundRect(stampBox, 6f, 6f, dash)
        PdfKit.paragraph(c, "مهر و امضاء فروشنده شرکت نفت", stampBox.right - 4f, top + 5f, boxW - 8f, PdfKit.text(8.4f, true), Align.CENTER)
        val receipt = files.loadBitmap(e.receiptPath, 1400)
        if (receipt != null) {
            c.drawRoundRect(slipBox, 6f, 6f, PdfKit.stroke(0.8f, Color.rgb(170, 180, 166)))
            PdfKit.bitmapFit(c, receipt, RectF(slipBox.left + 3f, slipBox.top + 3f, slipBox.right - 3f, slipBox.bottom - 3f))
            receipt.recycle()
        } else {
            c.drawRoundRect(slipBox, 6f, 6f, dash)
            PdfKit.line(c, "محل الصاق رسید پوز", slipBox.centerX(), top + 16f, PdfKit.text(8.2f, true, PdfKit.MUTED), Align.CENTER)
            val sx = slipBox.centerX()
            val st = PdfKit.stroke(1.4f, PdfKit.MUTED)
            c.drawLine(sx - 11f, top + 35f, sx + 11f, top + 35f, st)
            c.drawLine(sx - 11f, top + 35f, sx - 11f, top + 39f, st)
            c.drawLine(sx + 11f, top + 35f, sx + 11f, top + 39f, st)
            PdfKit.line(c, "منگنه", sx, top + 50f, PdfKit.text(7f, true, PdfKit.MUTED), Align.CENTER)
        }

        // ---------- footer ----------
        val foot = if (e.id > 0) "شماره ثبت ${e.id.fa()} | تهیه‌شده با اپلیکیشن محیط‌یار" else "تهیه‌شده با اپلیکیشن محیط‌یار"
        PdfKit.line(c, foot, W / 2, 415f, PdfKit.text(5.8f, false, Color.rgb(140, 150, 142)), Align.CENTER)
    }
}
