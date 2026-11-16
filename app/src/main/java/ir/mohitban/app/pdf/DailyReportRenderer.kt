package ir.mohitban.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import ir.mohitban.app.R
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.daily.DailyReport
import ir.mohitban.app.daily.Transport
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.domain.model.Plate
import ir.mohitban.app.pdf.PdfKit.Align
import java.io.File
import java.io.FileOutputStream

/**
 * The official A4 daily patrol report: the organisation's emblem and names at the top centre, the date and weekday on
 * the left, the ranger on the right; then vehicle, monitored areas, wildlife seen, violations, description, the day's
 * events and the signatures. Long texts continue on further pages.
 */
class DailyReportRenderer(private val context: Context, private val files: FileStore) {

    companion object {
        private const val W = 595f
        private const val H = 842f
        private const val M = 36f
        private const val BOTTOM = H - 48f
        private const val CONT_TOP = 64f
        private const val ROW_H = 18f
    }

    private val emblem: Bitmap? by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.doe_emblem) }

    /** Draws onto pages of one document and keeps track of where the next block goes. */
    private inner class Writer(val doc: PdfDocument, val r: DailyReport, val total: Int) {
        var number = 0
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = 0f

        fun newPage() {
            finish()
            number++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), number).create())
            page = p
            c = p.canvas
            watermark(c)
            if (number == 1) {
                y = header(c, r)
            } else {
                PdfKit.line(c, "ادامه‌ی گزارش روزانه گشت | ${JalaliDate.fromJdn(r.jdn).format().fa()} | ${r.name}", W - M, 44f, PdfKit.text(9.5f, true, PdfKit.GREEN))
                c.drawLine(M, 52f, W - M, 52f, PdfKit.stroke(0.8f, PdfKit.GREEN))
                y = CONT_TOP
            }
        }

        /** Makes room for [h] points, starting a page when the rest of this one is too short. */
        fun need(h: Float) { if (page == null || y + h > BOTTOM) newPage() }

        fun finish() {
            val p = page ?: return
            footer(p.canvas, number, total)
            doc.finishPage(p)
            page = null
        }
    }

    /** Builds the PDF in the cache folder and returns it. */
    fun createPdf(r: DailyReport): File {
        val sig = files.loadBitmap(r.signaturePath, 900)
        // first pass only counts the pages, so the footer can say "page x of y"
        val probe = PdfDocument()
        val pages = runCatching { layout(Writer(probe, r, 0), sig) }.getOrDefault(1)
        probe.close()
        val doc = PdfDocument()
        layout(Writer(doc, r, pages), sig)
        val day = JalaliDate.fromJdn(r.jdn).format().replace('/', '-')
        val out = File(files.cacheDir, "daily-report-$day-${r.id}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        sig?.recycle()
        return out
    }

    private fun layout(w: Writer, sig: Bitmap?): Int {
        val r = w.r
        w.newPage()
        vehicle(w, r)
        textBlock(w, "مناطق مورد پایش", r.areas.ifBlank { "—" })
        wildlife(w, r)
        violations(w, r)
        textBlock(w, "شرح و توضیحات گشت", r.description.ifBlank { "—" })
        textBlock(w, "اتفاقات و رویدادهای روز جاری", r.events.ifBlank { "موردی ثبت نشد." })
        signatures(w, r, sig)
        w.finish()
        return w.number
    }

    // ------------------------------------------------------------------ parts

    private fun watermark(c: Canvas) {
        emblem?.let { PdfKit.bitmapFit(c, it, RectF(W / 2 - 150f, H / 2 - 150f, W / 2 + 150f, H / 2 + 150f), alpha = 12) }
    }

    /** Letterhead; returns where the body starts. */
    private fun header(c: Canvas, r: DailyReport): Float {
        // centre: emblem, organisation, provincial office
        emblem?.let { PdfKit.bitmapFit(c, it, RectF(W / 2 - 28f, 24f, W / 2 + 28f, 80f)) }
        PdfKit.line(c, "سازمان حفاظت محیط زیست", W / 2, 96f, PdfKit.text(11.5f, true), Align.CENTER)
        val province = r.province.trim().removePrefix("استان").trim()
        PdfKit.fitLine(c, "اداره کل حفاظت محیط زیست استان $province", W / 2, 112f, PdfKit.text(10f, true, PdfKit.GREEN), 230f, Align.CENTER)

        // left: date and weekday (filled in by the app)
        val date = JalaliDate.fromJdn(r.jdn)
        val lab = PdfKit.text(8.5f, false, PdfKit.MUTED)
        val v = PdfKit.text(9.5f, true)
        // the label sits to the right of the value in RTL reading order
        fun leftRtl(y: Float, l: String, value: String) {
            val lw = lab.measureText("$l: ")
            PdfKit.line(c, "$l:", M + 150f, y, lab, Align.RIGHT)
            PdfKit.fitLine(c, value, M + 150f - lw, y, v, 150f - lw, Align.RIGHT)
        }
        leftRtl(44f, "تاریخ", date.format().fa())
        leftRtl(60f, "روز", Jalali.WEEKDAY_NAMES[date.dayOfWeek])
        leftRtl(76f, "شماره", "....................")
        leftRtl(92f, "پیوست", "ندارد")

        // right: the ranger
        val colR = W - M
        val maxW = 170f
        fun right(y: Float, l: String, value: String) {
            val lw = lab.measureText("$l: ")
            PdfKit.line(c, "$l:", colR, y, lab, Align.RIGHT)
            PdfKit.fitLine(c, value.ifBlank { "—" }, colR - lw, y, v, maxW - lw, Align.RIGHT)
        }
        right(40f, "نام و نام خانوادگی", r.name)
        right(55f, "درجه / سمت", listOf(r.grade, r.rank).filter { it.isNotBlank() }.distinct().joinToString(" | "))
        right(70f, "کد ملی", r.nationalId.fa())
        right(85f, "پاسگاه", r.station)
        right(100f, "منطقه", ir.mohitban.app.domain.model.AreaTypes.title(r.area))
        right(115f, "شهرستان", r.city)

        c.drawLine(M, 126f, W - M, 126f, PdfKit.stroke(1.4f, PdfKit.GREEN))
        c.drawLine(M, 129f, W - M, 129f, PdfKit.stroke(0.5f, PdfKit.GREEN))
        PdfKit.line(c, "گزارش روزانه گشت و پایش محیط‌بانی", W / 2, 152f, PdfKit.text(14f, true, PdfKit.GREEN), Align.CENTER)
        PdfKit.line(c, "تاریخ گشت: ${date.formatLong().fa()}", W / 2, 168f, PdfKit.text(9f, false, PdfKit.MUTED), Align.CENTER)
        return 182f
    }

    private fun sectionTitle(w: Writer, title: String) {
        val c = w.c
        c.drawRoundRect(RectF(M, w.y, W - M, w.y + 18f), 4f, 4f, PdfKit.fill(PdfKit.GREEN_LIGHT))
        c.drawRect(W - M - 4f, w.y, W - M, w.y + 18f, PdfKit.fill(PdfKit.GREEN))
        PdfKit.line(c, title, W - M - 10f, w.y + 12.8f, PdfKit.text(9.5f, true, PdfKit.GREEN))
        w.y += 22f
    }

    private fun box(c: Canvas, top: Float, bottom: Float) {
        c.drawRoundRect(RectF(M, top, W - M, bottom), 5f, 5f, PdfKit.stroke(0.7f, Color.rgb(200, 209, 195)))
    }

    private fun vehicle(w: Writer, r: DailyReport) {
        val h = 22f + 40f
        w.need(h + 8f)
        sectionTitle(w, "نحوه‌ی گشت و وسیله‌ی نقلیه")
        val c = w.c
        val top = w.y
        box(c, top, top + 40f)
        val lab = PdfKit.text(9f, false, PdfKit.MUTED)
        val v = PdfKit.text(10f, true)
        val cy = top + 20f
        if (r.transport == Transport.FOOT) {
            PdfKit.line(c, "نوع گشت:", W - M - 10f, cy + 4f, lab)
            PdfKit.line(c, "پیاده", W - M - 14f - lab.measureText("نوع گشت:"), cy + 4f, v)
        } else {
            val text = listOf(r.vehicleType, r.vehicleTitle).filter { it.isNotBlank() }.distinct().joinToString(" — ")
            PdfKit.line(c, "وسیله‌ی نقلیه:", W - M - 10f, cy + 4f, lab)
            val x = W - M - 14f - lab.measureText("وسیله‌ی نقلیه:")
            PdfKit.fitLine(c, text, x, cy + 4f, v, 200f)
            val plate = Plate.decode(r.plate)
            if (plate != null) {
                PdfKit.line(c, "پلاک:", W / 2 - 20f, cy + 4f, lab)
                PdfKit.plate(c, plate, W / 2 - 26f - lab.measureText("پلاک:"), cy, 26f)
            }
        }
        w.y = top + 48f
    }

    private fun textBlock(w: Writer, title: String, text: String) {
        val p = PdfKit.text(9.5f)
        val width = W - 2 * M - 20f
        val th = PdfKit.measure(text, width, p)
        val h = 22f + th + 16f
        // a long text starts on a new page rather than right at the bottom of this one
        w.need(minOf(h, 22f + 80f))
        sectionTitle(w, title)
        val top = w.y
        val used = PdfKit.paragraph(w.c, text, W - M - 10f, top + 8f, width, p)
        box(w.c, top, top + used + 16f)
        w.y = top + used + 24f
    }

    private fun wildlife(w: Writer, r: DailyReport) {
        val items = r.wildlife.filter { it.species.isNotBlank() && it.count > 0 }
        if (items.isEmpty()) { textBlock(w, "گونه‌های حیات وحش مشاهده‌شده", "گونه‌ای ثبت نشد."); return }
        // two lists side by side: ردیف | نام گونه | تعداد
        val rows = (items.size + 1) / 2
        w.need(22f + ROW_H * 3)
        sectionTitle(w, "گونه‌های حیات وحش مشاهده‌شده")
        val half = (W - 2 * M - 10f) / 2
        val cols = listOf(28f, half - 28f - 50f, 50f)
        fun header(top: Float) {
            for (side in 0..1) {
                val right = W - M - side * (half + 10f)
                w.c.drawRect(right - half, top, right, top + ROW_H, PdfKit.fill(PdfKit.GREEN_LIGHT))
                var x = right
                listOf("ردیف", "نام گونه", "تعداد").forEachIndexed { i, t ->
                    PdfKit.line(w.c, t, x - cols[i] / 2, top + 12.5f, PdfKit.text(8.5f, true, PdfKit.GREEN), Align.CENTER)
                    x -= cols[i]
                }
                grid(w.c, right, half, top, cols)
            }
        }
        header(w.y)
        w.y += ROW_H
        for (row in 0 until rows) {
            if (w.y + ROW_H > BOTTOM) { w.newPage(); header(w.y); w.y += ROW_H }
            for (side in 0..1) {
                val k = row + side * rows
                val right = W - M - side * (half + 10f)
                if (row % 2 == 1) w.c.drawRect(right - half, w.y, right, w.y + ROW_H, PdfKit.fill(Color.rgb(248, 250, 247)))
                val item = items.getOrNull(k)
                if (item != null) {
                    var x = right
                    val cells = listOf((k + 1).fa(), item.species, item.count.fa())
                    cells.forEachIndexed { i, t ->
                        PdfKit.fitLine(w.c, t, x - cols[i] / 2, w.y + 12.5f, PdfKit.text(9f, i == 2), cols[i] - 6f, Align.CENTER)
                        x -= cols[i]
                    }
                }
                grid(w.c, right, half, w.y, cols)
            }
            w.y += ROW_H
        }
        if (w.y + ROW_H > BOTTOM) w.newPage()
        val total = r.wildlifeTotal
        w.c.drawRect(M, w.y, W - M, w.y + ROW_H, PdfKit.fill(PdfKit.GREEN_LIGHT))
        PdfKit.line(w.c, "جمع: ${items.size.fa()} گونه، ${total.fa()} فرد", W / 2, w.y + 12.5f, PdfKit.text(9f, true, PdfKit.GREEN), Align.CENTER)
        w.c.drawRect(M, w.y, W - M, w.y + ROW_H, PdfKit.stroke(0.6f, PdfKit.GREEN))
        w.y += ROW_H + 10f
    }

    private fun grid(c: Canvas, right: Float, width: Float, top: Float, cols: List<Float>) {
        val line = PdfKit.stroke(0.6f, Color.rgb(170, 180, 166))
        c.drawRect(right - width, top, right, top + ROW_H, line)
        var x = right
        for (i in 0 until cols.size - 1) { x -= cols[i]; c.drawLine(x, top, x, top + ROW_H, line) }
    }

    private fun violations(w: Writer, r: DailyReport) {
        val p = PdfKit.text(9.5f)
        val width = W - 2 * M - 20f
        val detail = r.violationText.trim()
        val th = if (detail.isEmpty()) 0f else PdfKit.measure(detail, width, p) + 8f
        w.need(22f + 30f + minOf(th, 60f))
        sectionTitle(w, "تخلفات مشاهده‌شده (تخریب / شکار)")
        val c = w.c
        val top = w.y
        // checkboxes, right to left
        var x = W - M - 10f
        val t = PdfKit.text(9.5f, true)
        fun check(label: String, on: Boolean) {
            val s = 10f
            val box = RectF(x - s, top + 9f, x, top + 9f + s)
            c.drawRect(box, PdfKit.stroke(0.9f, PdfKit.INK))
            if (on) {
                val k = PdfKit.stroke(1.6f, Color.rgb(198, 40, 40))
                c.drawLine(box.left + 2f, box.centerY(), box.left + 4.5f, box.bottom - 2f, k)
                c.drawLine(box.left + 4.5f, box.bottom - 2f, box.right - 1.5f, box.top + 1.5f, k)
            }
            PdfKit.line(c, label, x - s - 5f, top + 18f, t)
            x -= s + 5f + t.measureText(label) + 22f
        }
        check("تخریب زیستگاه / منابع طبیعی", r.destruction)
        check("شکار و صید غیرمجاز", r.poaching)
        check("تخلفی مشاهده نشد", !r.hasViolation)
        var bottom = top + 28f
        if (detail.isNotEmpty()) {
            bottom += PdfKit.paragraph(c, "شرح تخلف: $detail", W - M - 10f, top + 30f, width, p) + 6f
        }
        box(c, top, bottom)
        w.y = bottom + 8f
    }

    private fun signatures(w: Writer, r: DailyReport, sig: Bitmap?) {
        val bh = 104f
        w.need(bh + 18f)
        w.y += 6f
        val gap = 14f
        val bw = (W - 2 * M - gap) / 2
        val titles = listOf("امضای محیط‌بان", "نظر و امضای رئیس پاسگاه / اداره")
        var x = W - M
        titles.forEachIndexed { i, t ->
            val rr = RectF(x - bw, w.y, x, w.y + bh)
            if (i == 0) w.c.drawRoundRect(rr, 6f, 6f, PdfKit.stroke(0.7f, Color.rgb(200, 209, 195)))
            else w.c.drawRoundRect(rr, 6f, 6f, PdfKit.dashed(0.9f, Color.rgb(140, 155, 138)))
            PdfKit.fitLine(w.c, t, rr.centerX(), rr.top + 16f, PdfKit.text(9f, true), bw - 10f, Align.CENTER)
            if (i == 0) {
                sig?.let { PdfKit.bitmapFit(w.c, it, RectF(rr.left + 24f, rr.top + 22f, rr.right - 24f, rr.bottom - 24f)) }
                PdfKit.fitLine(w.c, r.name, rr.centerX(), rr.bottom - 10f, PdfKit.text(8.5f, false, PdfKit.MUTED), bw - 10f, Align.CENTER)
            }
            x -= bw + gap
        }
        w.y += bh + 8f
    }

    private fun footer(c: Canvas, page: Int, total: Int) {
        c.drawLine(M, H - 34f, W - M, H - 34f, PdfKit.stroke(0.5f, Color.rgb(213, 220, 209)))
        val of = if (total > 0) " از ${total.fa()}" else ""
        PdfKit.line(
            c, "صفحه ${page.fa()}$of  |  تهیه‌شده با اپلیکیشن محیط‌یار نسخه ${ir.mohitban.app.BuildConfigCompat.versionName(context).fa()}",
            W / 2, H - 22f, PdfKit.text(7.5f, false, Color.rgb(122, 134, 118)), Align.CENTER,
        )
    }
}
