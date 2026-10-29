package ir.mohitban.app.ui.gasht

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.OfflineMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Draws an offline MBTiles map under the tracks. Tiles are decoded off the main thread and kept
 * in a memory cache; while a tile loads, a scaled-up lower-zoom tile (or the grid) shows instead.
 * Beyond the file's deepest zoom the deepest tiles are enlarged.
 */
class TileBackground(private val map: OfflineMap, private val scope: CoroutineScope) : MapBackground {
    private val version = mutableIntStateOf(0)
    private val cache = object : LruCache<Long, ImageBitmap>(96) {}
    private val loading = HashSet<Long>()
    private val missing = HashSet<Long>()

    private fun key(z: Int, x: Int, y: Int): Long = (z.toLong() shl 50) or (x.toLong() shl 25) or y.toLong()

    private fun request(z: Int, x: Int, y: Int) {
        val k = key(z, x, y)
        synchronized(loading) { if (k in loading || k in missing) return; loading += k }
        scope.launch(Dispatchers.IO) {
            val bmp = map.tile(z, x, y)?.let { bytes -> runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
            withContext(Dispatchers.Main) {
                synchronized(loading) { loading -= k; if (bmp == null) missing += k }
                if (bmp != null) cache.put(k, bmp)
                version.intValue++
            }
        }
    }

    override fun draw(scope: DrawScope, v: Viewport) {
        @Suppress("UNUSED_VARIABLE") val tick = version.intValue // redraw when tiles arrive
        GridBackground.draw(scope, v)
        val ideal = ln(156_543.033_928 / v.metersPerPx) / ln(2.0)
        val z = floor(ideal + 0.35).toInt().coerceIn(map.minZoom, map.maxZoom)
        val n = 1 shl z
        val tileM = 40_075_016.685_578 / n
        val half = 20_037_508.342_789
        val left = v.cx - v.size.width / 2 * v.metersPerPx
        val right = v.cx + v.size.width / 2 * v.metersPerPx
        val top = v.cy + v.size.height / 2 * v.metersPerPx
        val bottom = v.cy - v.size.height / 2 * v.metersPerPx
        val x0 = floor((left + half) / tileM).toInt().coerceAtLeast(0)
        val x1 = floor((right + half) / tileM).toInt().coerceAtMost(n - 1)
        val y0 = floor((half - top) / tileM).toInt().coerceAtLeast(0)
        val y1 = floor((half - bottom) / tileM).toInt().coerceAtMost(n - 1)
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > 220) return
        for (x in x0..x1) for (y in y0..y1) {
            val tl = v.toScreen(-half + x * tileM, half - y * tileM)
            val br = v.toScreen(-half + (x + 1) * tileM, half - (y + 1) * tileM)
            val dst = IntOffset(tl.x.roundToInt(), tl.y.roundToInt())
            val size = IntSize((br.x - tl.x).roundToInt() + 1, (br.y - tl.y).roundToInt() + 1)
            val k = key(z, x, y)
            val img = cache.get(k)
            if (img != null) {
                scope.drawImage(img, dstOffset = dst, dstSize = size, filterQuality = FilterQuality.Low)
                continue
            }
            if (k !in missing) request(z, x, y)
            // meanwhile: the matching part of a lower-zoom tile already in memory
            for (up in 1..4) {
                val pz = z - up
                if (pz < map.minZoom) break
                val px = x shr up; val py = y shr up
                val parent = cache.get(key(pz, px, py)) ?: continue
                val part = parent.width shr up
                val sx = (x - (px shl up)) * part
                val sy = (y - (py shl up)) * part
                scope.drawImage(parent, IntOffset(sx, sy), IntSize(part, part), dst, size, filterQuality = FilterQuality.Low)
                break
            }
        }
    }
}

/** Colours and minimum zoom (web-map level) for each kind of named place. */
internal fun labelStyle(kind: String): Pair<Color, Double>? = when (kind) {
    "town" -> Color(0xFF3B2A1E) to 8.5
    "village" -> Color(0xFF4A3A2E) to 11.0
    "hamlet" -> Color(0xFF5C4C40) to 13.0
    "peak" -> Color(0xFF6B3E1E) to 11.5
    "spring" -> Color(0xFF1E5FA8) to 12.0
    else -> null
}

internal fun zoomOf(metersPerPx: Double): Double = ln(156_543.033_928 / metersPerPx) / ln(2.0)
