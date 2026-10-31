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
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.gasht.Finding
import ir.mohitban.app.gasht.PatrolStats
import ir.mohitban.app.gasht.clockText
import ir.mohitban.app.gasht.minutes
import ir.mohitban.app.gasht.minutesText
import ir.mohitban.app.pdf.PdfKit.Align
import java.io.File
import java.io.FileOutputStream

/**
 * Monthly or yearly patrol report (گزارش گشت و کنترل مناطق) on A4: summary, coverage of every
 * protected area (most and least patrolled, areas with no patrol), findings and the full list.
 */
class PatrolReportRenderer(private val context: Context, private val files: FileStore) {
    companion object {
        private const val W = 595f
        private const val H = 842f
        private const val M = 36f
        private const val BOTTOM = H - 50f
        private const val ROW = 18f
    }

    private val emblem: Bitmap? by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.doe_emblem) }

    private inner class Writer(val doc: PdfDocument, val title: String) {
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        val c: Canvas get() = page.canvas
        var y = 0f

        fun newPage(first: Boolean) {
            if (pageNo > 0) finish()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            emblem?.let { PdfKit.bitmapFit(c, it, RectF(W / 2 - 150f, H / 2 - 150f, W / 2 + 150f, H / 2 + 150f), alpha = 12) }
            y = if (first) 0f else {
                PdfKit.line(c, "ادامه $title", W - M, 48f, PdfKit.text(10f, true, PdfKit.GREEN))
                66f
            }
        }

        fun need(h: Float) { if (y + h > BOTTOM) newPage(false) }

        fun finish() {
            c.drawLine(M, H - 34f, W - M, H - 34f, PdfKit.stroke(0.5f, Color.rgb(213, 220, 209)))
            PdfKit.line(
                c, "صفحه ${pageNo.fa()}  |  تهیه‌شده با اپلیکیشن محیط‌یار نسخه ${ir.mohitban.app.BuildConfigCompat.versionName(context).fa()}",
                W / 2, H - 22f, PdfKit.text(7.5f, false, Color.rgb(122, 134, 118)), Align.CENTER,
            )
            doc.finishPage(page)
        }

        fun section(t: String) {
            need(40f)
            y += 14f
            PdfKit.line(c, t, W - M, y + 10f, PdfKit.text(10.5f, true, PdfKit.GREEN))
            y += 18f
        }

        /** A table row; [cols] = (text, width) right to left; width 0 = the remaining space. */
        fun row(cols: List<Pair<String, Float>>, head: Boolean = false, shade: Boolean = false, color: Int = PdfKit.INK) {
            need(ROW)
            val fixed = cols.sumOf { it.second.toDouble() }.toFloat()
            val ws = cols.map { if (it.second == 0f) (W - 2 * M) - fixed else it.second }
            val r = RectF(M, y, W - M, y + ROW)
            if (head) c.drawRect(r, PdfKit.fill(PdfKit.GREEN_LIGHT)) else if (shade) c.drawRect(r, PdfKit.fill(Color.rgb(248, 250, 247)))
            var x = W - M
            val p = PdfKit.text(8.3f, head, if (head) PdfKit.GREEN else color)
            cols.forEachIndexed { i, (t, _) ->
                PdfKit.fitLine(c, t, x - ws[i] / 2, y + 12.5f, p, ws[i] - 6f, Align.CENTER)
                x -= ws[i]
                if (i < cols.lastIndex) c.drawLine(x, y, x, y + ROW, PdfKit.stroke(0.4f, Color.rgb(214, 222, 210)))
            }
            c.drawLine(M, y + ROW, W - M, y + ROW, PdfKit.stroke(0.4f, Color.rgb(214, 222, 210)))
            y += ROW
        }
    }

    fun createPdf(s: PatrolStats, profile: RangerProfile?): File {
        val yearly = s.period.month == null
        val title = if (yearly) "گزارش سالانه گشت و کنترل مناطق | ${s.period.title.fa()}" else "گزارش ماهانه گشت و کنترل مناطق | ${s.period.title.fa()}"
        val doc = PdfDocument()
        val w = Writer(doc, title)
        w.newPage(true)
        val c = w.c

        // ---- letterhead ----
        emblem?.let { PdfKit.bitmapFit(c, it, RectF(W - M - 54f, 30f, W - M, 84f)) }
        PdfKit.line(c, "ریاست جمهوری", W / 2, 46f, PdfKit.text(11f, true), Align.CENTER)
        val province = profile?.province?.trim()?.removePrefix("استان")?.trim().orEmpty()
        PdfKit.line(c, "اداره کل حفاظت محیط زیست${if (province.isNotEmpty()) " استان $province" else ""}", W / 2, 63f, PdfKit.text(11f, true), Align.CENTER)
        PdfKit.line(c, title, W / 2, 84f, PdfKit.text(13f, true, PdfKit.GREEN), Align.CENTER)
        val small = PdfKit.text(8.5f, false, PdfKit.MUTED)
        PdfKit.line(c, "شماره: ................", M, 44f, small, Align.LEFT)
        PdfKit.line(c, "تاریخ تهیه: ${JalaliDate.today().format().fa()}", M, 58f, small, Align.LEFT)
        c.drawLine(M, 96f, W - M, 96f, PdfKit.stroke(1.4f, PdfKit.GREEN))

        // ---- who ----
        val box = RectF(M, 106f, W - M, 160f)
        c.drawRoundRect(box, 6f, 6f, PdfKit.fill(Color.rgb(250, 251, 248)))
        c.drawRoundRect(box, 6f, 6f, PdfKit.stroke(0.7f, Color.rgb(200, 209, 195)))
        val lab = PdfKit.text(9f, false, PdfKit.MUTED)
        val v = PdfKit.text(9.5f, true)
        fun pair(x: Float, y: Float, l: String, value: String) {
            PdfKit.line(c, "$l:", x, y, lab, Align.RIGHT)
            PdfKit.fitLine(c, value, x - lab.measureText("$l:") - 5f, y, v, (W / 2 - M - 30f) - lab.measureText("$l:"), Align.RIGHT)
        }
        pair(W - M - 10f, 126f, "محیط‌بان", profile?.fullName.orEmpty())
        pair(W / 2 - 6f, 126f, "درجه / سمت", profile?.rank.orEmpty())
        pair(W - M - 10f, 146f, "پاسگاه محیط‌بانی", profile?.station.orEmpty())
        pair(W / 2 - 6f, 146f, "شهرستان", profile?.city.orEmpty())

        // ---- stat boxes ----
        val stats = listOf(
            "تعداد گشت" to s.count.fa(),
            "مدت کل" to minutesText(s.minutes).fa(),
            "مسافت (کیلومتر)" to String.format(java.util.Locale.US, "%.1f", s.km).removeSuffix(".0").fa(),
            "روزهای گشت" to "${s.activeDays.fa()} از ${s.period.days.fa()}",
            "تخلف / متخلف" to s.violations.fa(),
        )
        val gap = 6f
        val bw = (W - 2 * M - gap * (stats.size - 1)) / stats.size
        var x = W - M
        stats.forEach { (t, value) ->
            val r = RectF(x - bw, 172f, x, 226f)
            c.drawRoundRect(r, 6f, 6f, PdfKit.fill(Color.rgb(243, 247, 243)))
            PdfKit.line(c, t, r.centerX(), r.top + 18f, PdfKit.text(8f, false, PdfKit.MUTED), Align.CENTER)
            PdfKit.fitLine(c, value, r.centerX(), r.top + 40f, PdfKit.text(12f, true), bw - 8f, Align.CENTER)
            x -= bw + gap
        }
        w.y = 236f

        // ---- coverage by protected area ----
        w.section("پوشش گشت در مناطق")
        if (s.zones.isEmpty()) {
            w.need(20f); PdfKit.line(w.c, "در این دوره گشتی ثبت نشده است.", W - M, w.y + 12f, PdfKit.text(9f)); w.y += 20f
        } else {
            val max = s.zones.maxOf { it.count }.coerceAtLeast(1)
            w.row(listOf("ردیف" to 28f, "منطقه" to 0f, "تعداد گشت" to 52f, "سهم" to 120f, "مدت" to 86f, "کیلومتر" to 46f, "موارد" to 38f, "آخرین گشت" to 58f), head = true)
            s.zones.forEachIndexed { i, z ->
                val share = if (s.count > 0) z.count * 100 / s.count else 0
                w.row(
                    listOf(
                        (i + 1).fa() to 28f, z.zone to 0f, z.count.fa() to 52f, "" to 120f, minutesText(z.minutes).fa() to 86f,
                        String.format(java.util.Locale.US, "%.1f", z.km).removeSuffix(".0").fa() to 46f, z.problems.fa() to 38f,
                        (z.lastJdn?.let { JalaliDate.fromJdn(it).format() } ?: "—").fa() to 58f,
                    ),
                    shade = i % 2 == 1,
                )
                // share bar in its cell (cell = 4th column from the right)
                val right = W - M - 28f - ((W - 2 * M) - (28f + 52f + 120f + 86f + 46f + 38f + 58f)) - 52f
                val barW = 120f - 30f
                val top = w.y - ROW + 5f
                w.c.drawRoundRect(RectF(right - 6f - barW, top, right - 6f, top + 8f), 3f, 3f, PdfKit.fill(Color.rgb(230, 236, 228)))
                w.c.drawRoundRect(RectF(right - 6f - barW * z.count / max, top, right - 6f, top + 8f), 3f, 3f, PdfKit.fill(PdfKit.GREEN))
                PdfKit.line(w.c, "${share.fa()}٪", right - barW - 10f, top + 8f, PdfKit.text(7.5f, false, PdfKit.MUTED), Align.RIGHT)
            }
            w.need(34f)
            val most = s.zones.first()
            val least = s.zones.last()
            PdfKit.line(w.c, "بیشترین گشت: ${most.zone} (${most.count.fa()} بار)", W - M, w.y + 14f, PdfKit.text(9f, true, PdfKit.GREEN))
            if (s.zones.size > 1) PdfKit.line(w.c, "کمترین گشت: ${least.zone} (${least.count.fa()} بار)", W - M, w.y + 28f, PdfKit.text(9f, true, Color.rgb(192, 120, 20)))
            w.y += 34f
        }
        if (s.unvisited.isNotEmpty()) {
            w.section("مناطقی که در این دوره گشت نشده‌اند (${s.unvisited.size.fa()})")
            w.need(40f)
            val used = PdfKit.paragraph(w.c, s.unvisited.joinToString("  •  "), W - M, w.y, W - 2 * M, PdfKit.text(9f, false, Color.rgb(176, 46, 38)))
            w.y += used + 4f
        }

        // ---- findings ----
        if (s.findings.isNotEmpty()) {
            w.section("موارد مشاهده‌شده")
            w.row(listOf("مورد" to 0f, "تعداد گشت‌ها" to 90f), head = true)
            s.findings.forEachIndexed { i, (f, n) -> w.row(listOf(f.title to 0f, n.fa() to 90f), shade = i % 2 == 1, color = if (f.problem) Color.rgb(160, 40, 34) else PdfKit.INK) }
        }
        val species = ir.mohitban.app.gasht.Sightings.totals(s.logs)
        if (species.isNotEmpty()) {
            w.section("حیات‌وحش مشاهده‌شده (${species.size.fa()} گونه)")
            w.row(listOf("گونه" to 0f, "تعداد فرد" to 90f, "دفعات مشاهده" to 90f), head = true)
            species.forEachIndexed { i, (sp, n, times) -> w.row(listOf(sp to 0f, n.fa() to 90f, times.fa() to 90f), shade = i % 2 == 1) }
        }
        if (s.modes.isNotEmpty()) {
            w.section("نوع گشت")
            w.row(listOf("نوع" to 0f, "تعداد" to 90f), head = true)
            s.modes.forEachIndexed { i, (m, n) -> w.row(listOf(m to 0f, n.fa() to 90f), shade = i % 2 == 1) }
            if (s.nights > 0) w.row(listOf("گشت شبانه (شروع بعد از ۱۹ یا پیش از ۵ صبح)" to 0f, s.nights.fa() to 90f))
        }

        // ---- every patrol ----
        if (s.logs.isNotEmpty()) {
            w.section("ریز گشت‌ها")
            w.row(listOf("ردیف" to 26f, "تاریخ" to 54f, "ساعت" to 62f, "منطقه / محل" to 0f, "نوع" to 56f, "کیلومتر" to 40f, "موارد مشاهده‌شده" to 140f), head = true)
            s.logs.forEachIndexed { i, p ->
                val place = if (p.spot.isBlank()) p.zone else "${p.zone} — ${p.spot}"
                val f = Finding.parse(p.findings).joinToString("، ") { it.title.substringBefore(" /") } +
                    if (p.violations > 0) " (تخلف: ${p.violations})" else ""
                w.row(
                    listOf(
                        (i + 1).fa() to 26f, JalaliDate.fromJdn(p.jdn).format().fa() to 54f,
                        "${clockText(p.startMin)}–${clockText(p.endMin)}".fa() to 62f, place to 0f, p.mode to 56f,
                        (p.distanceKm?.let { String.format(java.util.Locale.US, "%.1f", it).removeSuffix(".0") } ?: "—").fa() to 40f,
                        f.ifBlank { "—" }.fa() to 140f,
                    ),
                    shade = i % 2 == 1,
                )
            }
        }

        // ---- signatures ----
        w.need(90f)
        w.y += 30f
        PdfKit.line(w.c, "نام و امضای محیط‌بان", W - M - 40f, w.y, PdfKit.text(9.5f, true), Align.RIGHT)
        PdfKit.line(w.c, "نام و امضای مسئول منطقه / رئیس اداره", M + 40f, w.y, PdfKit.text(9.5f, true), Align.LEFT)
        PdfKit.line(w.c, profile?.fullName.orEmpty(), W - M - 40f, w.y + 18f, PdfKit.text(9f), Align.RIGHT)
        w.finish()

        val name = if (yearly) "patrol-report-${s.period.year}.pdf" else "patrol-report-${s.period.year}-${s.period.month}.pdf"
        val out = File(files.cacheDir, name)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    @Suppress("unused") private val months = Jalali.MONTH_NAMES
}
