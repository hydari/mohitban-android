package ir.mohitban.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import ir.mohitban.app.R
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.PersianWords
import ir.mohitban.app.core.text.amountText
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.domain.model.FuelType
import ir.mohitban.app.domain.model.QuarterSummary
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.pdf.PdfKit.Align
import java.io.File
import java.io.FileOutputStream

/** Official quarterly (Jalali season) fuel report on A4, ready to print or send to the department head. */
class QuarterReportRenderer(private val context: Context, private val files: FileStore) {

    companion object {
        private const val W = 595f
        private const val H = 842f
        private const val M = 36f
        private const val ROW_H = 19f
        private const val HEAD_H = 22f
        private const val CONT_TOP = 70f
        private const val CONTENT_BOTTOM = H - 52f
        private const val FINAL_BLOCK = 200f
    }

    private val emblem: Bitmap? by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.doe_emblem) }

    // right-to-left columns: title, width
    private val columns = listOf(
        "ردیف" to 24f, "تاریخ" to 54f, "کیلومتر" to 52f, "نوع سوخت" to 38f,
        "مقدار" to 52f, "فی (ریال)" to 52f, "مبلغ (ریال)" to 64f, "وسیله / پلاک" to 0f, "راننده" to 82f,
    )

    private fun widths(): List<Float> {
        val fixed = columns.sumOf { it.second.toDouble() }.toFloat()
        return columns.map { if (it.second == 0f) (W - 2 * M) - fixed else it.second }
    }

    fun createPdf(s: QuarterSummary, profile: RangerProfile, signature: Bitmap?, signaturePath: String?): File {
        val sig = signature ?: files.loadBitmap(signaturePath, 700)
        val firstTableTop = 300f
        // ---- paginate rows ----
        val pages = mutableListOf<IntRange>()
        var index = 0
        var top = firstTableTop
        while (index < s.entries.size || pages.isEmpty()) {
            val capacity = (((CONTENT_BOTTOM - top - HEAD_H) / ROW_H).toInt()).coerceAtLeast(1)
            val end = minOf(s.entries.size, index + capacity)
            pages += index until end
            index = end
            top = CONT_TOP
            if (s.entries.isEmpty()) break
        }
        // does the totals row + final block fit after the last row?
        val lastTop = if (pages.size == 1) firstTableTop else CONT_TOP
        val lastBottom = lastTop + HEAD_H + pages.last().count() * ROW_H + ROW_H
        val extraPage = lastBottom + FINAL_BLOCK > CONTENT_BOTTOM
        // appendix: photos of the POS receipts (فیش‌های پرداختی), twelve per A4 page (4 × 3)
        val receipts = s.entries.mapIndexedNotNull { i, e -> if (e.receiptPath != null) (i + 1) to e else null }
        val receiptPages = receipts.chunked(12)
        val totalPages = pages.size + (if (extraPage) 1 else 0) + receiptPages.size

        val doc = PdfDocument()
        pages.forEachIndexed { pi, range ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pi + 1).create())
            val c = page.canvas
            watermark(c)
            val tableTop = if (pi == 0) {
                header(c, s, profile, receiptPages.size)
                firstTableTop
            } else {
                PdfKit.line(c, "ادامه گزارش فصلی هزینه سوخت | ${s.season.title} ${s.year.fa()}", W - M, 48f, PdfKit.text(10f, true, PdfKit.GREEN))
                CONT_TOP
            }
            var y = tableHeader(c, tableTop)
            for (i in range) {
                y = tableRow(c, y, i, s)
            }
            if (pi == pages.lastIndex) {
                y = totalsRow(c, y, s)
                if (!extraPage) finalBlock(c, y + 14f, s, profile, sig)
            }
            footer(c, pi + 1, totalPages)
            doc.finishPage(page)
        }
        if (extraPage) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), totalPages).create())
            watermark(page.canvas)
            finalBlock(page.canvas, CONT_TOP, s, profile, sig)
            footer(page.canvas, totalPages, totalPages)
            doc.finishPage(page)
        }
        receiptPages.forEachIndexed { ri, chunk ->
            val number = pages.size + (if (extraPage) 1 else 0) + ri + 1
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), number).create())
            receiptsPage(page.canvas, s, chunk)
            footer(page.canvas, number, totalPages)
            doc.finishPage(page)
        }
        val out = File(files.cacheDir, "quarter-report-${s.year}-${s.season.index}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    /** Renders every page to a bitmap for the in-app preview. */
    fun renderPages(file: File, width: Int = 1240): List<Bitmap> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val result = mutableListOf<Bitmap>()
        try {
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val h = (width.toFloat() * page.height / page.width).toInt()
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result += bmp
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return result
    }

    // ------------------------------------------------------------------ parts

    private fun watermark(c: Canvas) {
        emblem?.let { PdfKit.bitmapFit(c, it, RectF(W / 2 - 150f, H / 2 - 150f, W / 2 + 150f, H / 2 + 150f), alpha = 14) }
    }

    private fun header(c: Canvas, s: QuarterSummary, p: RangerProfile, attachmentPages: Int) {
        emblem?.let { PdfKit.bitmapFit(c, it, RectF(W - M - 54f, 30f, W - M, 84f)) }
        PdfKit.line(c, "ریاست جمهوری", W / 2, 46f, PdfKit.text(11f, true), Align.CENTER)
        PdfKit.line(c, "اداره کل حفاظت محیط زیست استان ${p.province.trim().removePrefix("استان").trim()}", W / 2, 63f, PdfKit.text(11f, true), Align.CENTER)
        PdfKit.line(c, "گزارش فصلی هزینه سوخت | ${s.season.title} ${s.year.fa()} (${s.season.months})", W / 2, 84f, PdfKit.text(13f, true, PdfKit.GREEN), Align.CENTER)
        val small = PdfKit.text(8.5f, false, PdfKit.MUTED)
        PdfKit.line(c, "شماره: ................", M, 44f, small, Align.LEFT)
        PdfKit.line(c, "تاریخ تهیه: ${JalaliDate.today().format().fa()}", M, 58f, small, Align.LEFT)
        PdfKit.line(c, if (attachmentPages > 0) "پیوست: دارد" else "پیوست: ندارد", M, 72f, small, Align.LEFT)
        c.drawLine(M, 96f, W - M, 96f, PdfKit.stroke(1.4f, PdfKit.GREEN))

        // ---- info box ----
        val box = RectF(M, 106f, W - M, 196f)
        c.drawRoundRect(box, 6f, 6f, PdfKit.fill(Color.rgb(250, 251, 248)))
        c.drawRoundRect(box, 6f, 6f, PdfKit.stroke(0.7f, Color.rgb(200, 209, 195)))
        val lab = PdfKit.text(9f, false, PdfKit.MUTED)
        val v = PdfKit.text(9.5f, true)
        val colR = W - M - 10f
        val colL = W / 2 - 6f
        fun pair(x: Float, y: Float, l: String, value: String) {
            PdfKit.line(c, "$l:", x, y, lab, Align.RIGHT)
            PdfKit.fitLine(c, value, x - lab.measureText("$l:") - 5f, y, v, (W / 2 - M - 30f) - lab.measureText("$l:"), Align.RIGHT)
        }
        pair(colR, 124f, "نام و نام خانوادگی", p.fullName)
        pair(colL, 124f, "کد ملی", p.nationalId.fa())
        pair(colR, 142f, "نام پدر", p.fatherName)
        pair(colL, 142f, "درجه / سمت", p.rank)
        pair(colR, 160f, "شهرستان", p.city)
        pair(colL, 160f, "منطقه حفاظت‌شده", p.protectedArea)
        pair(colR, 178f, "پاسگاه محیط‌بانی", p.station)
        val vehiclesText = s.entries.map { "${it.snapshot.vehicleModel} ${it.snapshot.plate.displayText().fa()}" }.distinct().joinToString(" ، ")
        if (vehiclesText.isNotBlank()) pair(colL, 178f, "وسیله", vehiclesText)

        // ---- stat boxes ----
        val stats = listOf(
            "تعداد ثبت" to s.count.fa(),
            "مجموع بنزین (لیتر)" to s.gasolineLiters.amountText(),
            "مجموع گاز (کیلوگرم)" to s.cngKg.amountText(),
            "جمع کل (ریال)" to s.total.rial(),
        )
        val gap = 8f
        val bw = (W - 2 * M - gap * 3) / 4
        var x = W - M
        stats.forEachIndexed { i, (t, value) ->
            val r = RectF(x - bw, 208f, x, 270f)
            val last = i == stats.lastIndex
            c.drawRoundRect(r, 6f, 6f, PdfKit.fill(if (last) PdfKit.GREEN else Color.rgb(243, 247, 243)))
            PdfKit.line(c, t, r.centerX(), r.top + 20f, PdfKit.text(8.5f, false, if (last) Color.rgb(207, 227, 213) else PdfKit.MUTED), Align.CENTER)
            PdfKit.fitLine(c, value, r.centerX(), r.top + 45f, PdfKit.text(14f, true, if (last) Color.WHITE else PdfKit.INK), bw - 10f, Align.CENTER)
            x -= bw + gap
        }
        PdfKit.line(c, "ریز هزینه‌های سوخت", W - M, 292f, PdfKit.text(10.5f, true, PdfKit.GREEN))
    }

    private fun tableHeader(c: Canvas, top: Float): Float {
        val w = widths()
        c.drawRect(M, top, W - M, top + HEAD_H, PdfKit.fill(PdfKit.GREEN_LIGHT))
        val p = PdfKit.text(8.5f, true, PdfKit.GREEN)
        var x = W - M
        columns.forEachIndexed { i, (title, _) ->
            PdfKit.fitLine(c, title, x - w[i] / 2, top + 14.5f, p, w[i] - 4f, Align.CENTER)
            x -= w[i]
        }
        grid(c, top, top + HEAD_H)
        return top + HEAD_H
    }

    private fun grid(c: Canvas, top: Float, bottom: Float) {
        val w = widths()
        val line = PdfKit.stroke(0.6f, Color.rgb(170, 180, 166))
        c.drawRect(M, top, W - M, bottom, line)
        var x = W - M
        for (i in 0 until w.size - 1) {
            x -= w[i]
            c.drawLine(x, top, x, bottom, line)
        }
    }

    private fun tableRow(c: Canvas, top: Float, i: Int, s: QuarterSummary): Float {
        val e = s.entries[i]
        val w = widths()
        if (i % 2 == 1) c.drawRect(M, top, W - M, top + ROW_H, PdfKit.fill(Color.rgb(248, 250, 247)))
        val cells = listOf(
            (i + 1).fa(),
            e.date.format().fa(),
            e.odometer.rial(),
            if (e.fuelType == FuelType.CNG) "گاز" else "بنزین",
            "${e.amount.amountText()} ${e.fuelType.unitShort}",
            e.unitPrice.rial(),
            e.total.rial(),
            listOf(e.snapshot.vehicleModel.trim(), e.snapshot.plate.displayText().fa()).filter { it.isNotEmpty() }.joinToString(" | "),
            e.snapshot.driverName,
        )
        val p = PdfKit.text(8.5f)
        val small = PdfKit.text(7.5f)
        var x = W - M
        cells.forEachIndexed { k, t ->
            PdfKit.fitLine(c, t, x - w[k] / 2, top + 13f, if (k == 7) small else p, w[k] - 5f, Align.CENTER)
            x -= w[k]
        }
        grid(c, top, top + ROW_H)
        return top + ROW_H
    }

    private fun totalsRow(c: Canvas, top: Float, s: QuarterSummary): Float {
        val w = widths()
        c.drawRect(M, top, W - M, top + ROW_H + 2f, PdfKit.fill(PdfKit.GREEN_LIGHT))
        val p = PdfKit.text(9f, true, PdfKit.GREEN)
        val firstFour = w.take(4).sum()
        PdfKit.line(c, "جمع کل", W - M - firstFour / 2, top + 14f, p, Align.CENTER)
        var x = W - M - firstFour
        PdfKit.fitLine(c, "${s.gasolineLiters.amountText()} لیتر / ${s.cngKg.amountText()} کیلو", x - w[4] / 2, top + 14f, PdfKit.text(7.5f, true, PdfKit.GREEN), w[4] - 4f, Align.CENTER)
        x -= w[4]
        PdfKit.line(c, "—", x - w[5] / 2, top + 14f, p, Align.CENTER)
        x -= w[5]
        PdfKit.fitLine(c, s.total.rial(), x - w[6] / 2, top + 14f, p, w[6] - 4f, Align.CENTER)
        c.drawRect(M, top, W - M, top + ROW_H + 2f, PdfKit.stroke(0.8f, PdfKit.GREEN))
        return top + ROW_H + 2f
    }

    private fun finalBlock(c: Canvas, top: Float, s: QuarterSummary, p: RangerProfile, sig: Bitmap?) {
        val body = PdfKit.text(9.5f)
        val bold = PdfKit.text(9.5f, true)
        var y = top
        PdfKit.line(c, "جمع هزینه بنزین: ${s.gasolineTotal.rial()} ریال   |   جمع هزینه گاز: ${s.cngTotal.rial()} ریال", W - M, y + 10f, body)
        y += 20f
        val words = "جمع کل به حروف: ${PersianWords.of(s.total)} ریال"
        y += PdfKit.paragraph(c, words, W - M, y, W - 2 * M, bold) + 16f

        val gap = 12f
        val bw = (W - 2 * M - gap * 2) / 3
        val bh = 118f
        val titles = listOf("امضای محیط‌بان", "تأیید رئیس پاسگاه", "مهر و امضای رئیس اداره")
        var x = W - M
        titles.forEachIndexed { i, t ->
            val r = RectF(x - bw, y, x, y + bh)
            if (i == 2) c.drawRoundRect(r, 6f, 6f, PdfKit.dashed(0.9f, Color.rgb(140, 155, 138)))
            else c.drawRoundRect(r, 6f, 6f, PdfKit.stroke(0.7f, Color.rgb(200, 209, 195)))
            PdfKit.line(c, t, r.centerX(), r.top + 16f, PdfKit.text(9f, true), Align.CENTER)
            if (i == 0) {
                sig?.let { PdfKit.bitmapFit(c, it, RectF(r.left + 14f, r.top + 24f, r.right - 14f, r.bottom - 26f)) }
                PdfKit.line(c, p.fullName, r.centerX(), r.bottom - 10f, PdfKit.text(8.5f, false, PdfKit.MUTED), Align.CENTER)
            }
            x -= bw + gap
        }
    }

    private fun receiptsPage(c: Canvas, s: QuarterSummary, items: List<Pair<Int, ir.mohitban.app.domain.model.FuelEntry>>) {
        PdfKit.line(c, "پیوست: فیش‌های پرداختی (رسید کارتخوان) | ${s.season.title} ${s.year.fa()}", W - M, 40f, PdfKit.text(10.5f, true, PdfKit.GREEN))
        c.drawLine(M, 48f, W - M, 48f, PdfKit.stroke(1f, PdfKit.GREEN))
        val cols = 3
        val rows = 4
        val gap = 7f
        val top0 = 56f
        val cellW = (W - 2 * M - gap * (cols - 1)) / cols
        val cellH = (H - 44f - top0 - gap * (rows - 1)) / rows
        val cap1 = PdfKit.text(7.6f, true)
        val cap2 = PdfKit.text(7.2f, true, PdfKit.GREEN)
        items.forEachIndexed { k, (row, e) ->
            val col = k % cols
            val line = k / cols
            val right = W - M - col * (cellW + gap)
            val top = top0 + line * (cellH + gap)
            val box = RectF(right - cellW, top, right, top + cellH)
            c.drawRoundRect(box, 5f, 5f, PdfKit.stroke(0.6f, Color.rgb(200, 209, 195)))
            PdfKit.fitLine(c, "ردیف ${row.fa()} | ${e.date.format().fa()}", box.centerX(), box.top + 11f, cap1, cellW - 8f, Align.CENTER)
            PdfKit.fitLine(c, "${e.fuelType.short} | ${e.total.rial()} ریال", box.centerX(), box.top + 21f, cap2, cellW - 8f, Align.CENTER)
            val img = files.loadBitmap(e.receiptPath, 1000)
            val imgBox = RectF(box.left + 3f, box.top + 25f, box.right - 3f, box.bottom - 3f)
            if (img != null) {
                PdfKit.bitmapFit(c, img, imgBox)
                img.recycle()
            } else {
                PdfKit.line(c, "تصویر در دسترس نیست", imgBox.centerX(), imgBox.centerY(), PdfKit.text(7.5f, false, PdfKit.MUTED), Align.CENTER)
            }
        }
    }

    private fun footer(c: Canvas, page: Int, total: Int) {
        c.drawLine(M, H - 34f, W - M, H - 34f, PdfKit.stroke(0.5f, Color.rgb(213, 220, 209)))
        PdfKit.line(c, "صفحه ${page.fa()} از ${total.fa()}  |  تهیه‌شده با اپلیکیشن محیط‌یار نسخه ${ir.mohitban.app.BuildConfigCompat.versionName(context).fa()}", W / 2, H - 22f, PdfKit.text(7.5f, false, Color.rgb(122, 134, 118)), Align.CENTER)
    }
}
