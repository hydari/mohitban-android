package ir.mohitban.app.gasht

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One county's ready-made topographic map, as listed in the app (assets/geo/maps.json). */
data class CountyMap(val name: String, val province: String, val file: String, val size: Long, val bounds: DoubleArray) {
    val title: String get() = "شهرستان $name"
    fun contains(p: GeoPoint) = p.lon in bounds[0]..bounds[2] && p.lat in bounds[1]..bounds[3]
}

/** The county maps the app can download, and which of them are already on the phone. */
object CountyMaps {
    @Volatile private var cache: List<CountyMap>? = null

    fun all(context: Context): List<CountyMap> {
        cache?.let { return it }
        val list = runCatching {
            val arr = JSONObject(assetText(context, "geo/maps.json") ?: "{}").optJSONArray("maps")
            (0 until (arr?.length() ?: 0)).map { i ->
                val o = arr!!.getJSONObject(i)
                val b = o.getJSONArray("bounds")
                CountyMap(o.getString("name"), o.optString("province"), o.getString("file"), o.optLong("size"), DoubleArray(4) { b.getDouble(it) })
            }
        }.getOrDefault(emptyList())
        cache = list
        return list
    }

    private fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").removePrefix("شهرستان").trim()

    fun byCounty(context: Context, county: String?): CountyMap? {
        val n = norm(county ?: return null)
        if (n.isEmpty()) return null
        return all(context).firstOrNull { norm(it.name) == n || norm(it.name).removePrefix("ویژه") == n }
    }

    fun byFile(context: Context, file: File): CountyMap? = all(context).firstOrNull { it.file == file.name }

    fun local(context: Context, m: CountyMap): File = File(MapStore.dir(context), m.file)

    fun isDownloaded(context: Context, m: CountyMap) = local(context, m).exists()

    /** Name to show for a map file on the phone. */
    fun label(context: Context, f: File): String = byFile(context, f)?.title ?: f.nameWithoutExtension
}

/** Progress of one map download. */
data class Download(val map: CountyMap, val done: Long, val total: Long, val state: State, val error: String? = null) {
    enum class State { RUNNING, FINISHED, FAILED }
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Downloads county maps. The only internet use of the whole app: it connects to the map files
 * published with the app and nowhere else, sends nothing about the phone, and resumes an
 * interrupted download from where it stopped.
 */
object MapDownloader {
    /** The map files live only on the department's own site (served to this app alone). */
    val SOURCES = listOf("https://muhitban.ir/app/maps/")

    // the host answers only requests that carry this app's access token, and the files are
    // encrypted: a copied file is useless outside the app
    private val K1 = byteArrayOf(102, 22, 21, 28, -98, -56, 57, 56, -57, 104, -122, 111, -83, 35, -109, 32, 34, 1, -40, -28, 58, 58, 37, 127, -106, 30, 23, -88, -16, 83, -36, -37)
    private val K2 = byteArrayOf(-13, -27, -74, 44, -36, -117, 99, -19, -124, -54, -42, -13, -5, -80, 72, -98, 114, -50, 62, 54, -47, -79, 26, 100, 11, -58, -102, -2, 99, -30, 22, 108)
    private val T1 = byteArrayOf(-54, -69, 90, 107, -92, -66, 47, -63, -119, 52, 26, -72, 44, 47, -15, -117, -25, -78, 26, -36, 41, -8, -61, 41, 99, 117, 33, -73, -13, 36, -79, 93, -89, 117, 15, -77)
    private val T2 = byteArrayOf(-7, -113, 109, 93, -105, -122, 24, -12, -65, 82, 46, -120, 77, 23, -58, -66, -41, -118, 42, -67, 17, -102, -6, 77, 82, 20, 18, -43, -62, 69, -43, 106, -59, 23, 108, -126)

    private fun join(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }
    private val token: String get() = String(join(T1, T2), Charsets.US_ASCII)

    /** File layout: "MHYM1" + 16-byte IV + AES-256-CTR of the MBTiles file. */
    private fun decrypt(src: File, dst: File) {
        src.inputStream().buffered().use { input ->
            val magic = ByteArray(5); require(input.read(magic) == 5 && String(magic, Charsets.US_ASCII) == "MHYM1") { "invalid" }
            val iv = ByteArray(16); require(input.read(iv) == 16) { "invalid" }
            val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(join(K1, K2), "AES"), javax.crypto.spec.IvParameterSpec(iv))
            javax.crypto.CipherInputStream(input, cipher).use { c -> dst.outputStream().use { c.copyTo(it, 1 shl 16) } }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()
    private val _state = MutableStateFlow<Map<String, Download>>(emptyMap())
    val state: StateFlow<Map<String, Download>> = _state.asStateFlow()

    /** Each finished map file, once, so the open screen can show it straight away. */
    private val _finished = kotlinx.coroutines.flow.MutableSharedFlow<File>(extraBufferCapacity = 4)
    val finished: kotlinx.coroutines.flow.SharedFlow<File> = _finished

    fun start(context: Context, m: CountyMap) {
        val app = context.applicationContext
        synchronized(jobs) {
            if (jobs[m.file]?.isActive == true) return
            _state.update { it + (m.file to Download(m, 0, m.size, Download.State.RUNNING)) }
            jobs[m.file] = scope.launch { run(app, m) }
        }
    }

    fun cancel(m: CountyMap) {
        synchronized(jobs) { jobs.remove(m.file)?.cancel() }
        _state.update { it - m.file }
    }

    fun clear(m: CountyMap) = _state.update { it - m.file }

    private fun set(m: CountyMap, d: Download) = _state.update { it + (m.file to d) }

    /** Parallel connections: several ranged requests together fill a slow or throttled link much better than one. */
    private const val PARTS = 6

    private fun open(u: URL, range: LongRange?): HttpURLConnection {
        require(u.protocol == "https") { "insecure" }
        return (u.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "Mohityar")
            setRequestProperty("X-Mohityar-Key", token)
            setRequestProperty("Accept-Encoding", "identity")
            if (range != null) setRequestProperty("Range", "bytes=${range.first}-${range.last}")
        }
    }

    /** Opens [m] for the byte range, following redirects; null when no source can serve it. */
    private fun connect(m: CountyMap, range: LongRange?): HttpURLConnection? {
        for (base in SOURCES) {
            val c = runCatching {
                var url = URL(base + m.file)
                var c = open(url, range)
                var redirects = 0
                while (c.responseCode in 300..399 && redirects < 5) {
                    val loc = c.getHeaderField("Location") ?: error("redirect")
                    c.disconnect()
                    url = URL(url, loc); redirects++
                    c = open(url, range)
                }
                if (c.responseCode == 200 || c.responseCode == 206) c else { c.disconnect(); null }
            }.getOrNull()
            if (c != null) return c
        }
        return null
    }

    private suspend fun run(context: Context, m: CountyMap) {
        val target = CountyMaps.local(context, m)
        val part = File(target.parentFile, m.file + ".part")
        val seg = File(target.parentFile, m.file + ".seg")
        try {
            val total = m.size
            require(total > 0) { "size" }
            // the file is cut into PARTS ranges, each fetched on its own connection; the bytes
            // already received per range are kept in a small side file so a retry resumes
            val n = if (total < 1_000_000) 1 else PARTS
            val bounds = (0 until n).map { i -> (total * i / n) until (total * (i + 1) / n) }
            val done = LongArray(n)
            if (part.exists() && part.length() == total && seg.exists()) {
                runCatching { seg.readText().split(',').map { it.trim().toLong() } }.getOrNull()
                    ?.takeIf { it.size == n }?.forEachIndexed { i, v -> done[i] = v.coerceIn(0, bounds[i].last - bounds[i].first + 1) }
            } else {
                part.delete(); seg.delete()
                java.io.RandomAccessFile(part, "rw").use { it.setLength(total) }
            }
            fun received() = done.sum()
            fun saveSeg() = runCatching { seg.writeText(done.joinToString(",")) }
            set(m, Download(m, received(), total, Download.State.RUNNING))
            var failure: Exception? = null
            kotlinx.coroutines.coroutineScope {
                val ticker = launch {
                    while (true) {
                        kotlinx.coroutines.delay(250)
                        set(m, Download(m, received(), total, Download.State.RUNNING))
                        saveSeg()
                    }
                }
                val workers = bounds.mapIndexed { i, r ->
                    launch(Dispatchers.IO) {
                        var attempts = 0
                        while (done[i] < r.last - r.first + 1 && kotlinx.coroutines.currentCoroutineContext().isActive) {
                            val from = r.first + done[i]
                            try {
                                val c = connect(m, from..r.last) ?: throw java.net.ConnectException("no source")
                                // a server that ignores ranges sends the whole file: only one connection can use that
                                if (c.responseCode == 200 && (from != 0L || n > 1)) { c.disconnect(); error("norange") }
                                java.io.RandomAccessFile(part, "rw").use { raf ->
                                    raf.seek(from)
                                    c.inputStream.use { input ->
                                        val buf = ByteArray(1 shl 16)
                                        while (kotlinx.coroutines.currentCoroutineContext().isActive && done[i] < r.last - r.first + 1) {
                                            val k = input.read(buf)
                                            if (k < 0) break
                                            val take = minOf(k.toLong(), r.last - r.first + 1 - done[i]).toInt()
                                            raf.write(buf, 0, take)
                                            done[i] += take
                                        }
                                    }
                                }
                                c.disconnect()
                                attempts = 0
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (e.message == "norange" || ++attempts >= 4) { failure = e; return@launch }
                                kotlinx.coroutines.delay(1500L * attempts)
                            }
                        }
                    }
                }
                workers.forEach { it.join() }
                ticker.cancel()
            }
            saveSeg()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (failure?.message == "norange") {
                // the host does not support ranges: one plain download from the start
                part.delete(); seg.delete()
                val c = connect(m, null) ?: throw java.net.ConnectException("no source")
                var got = 0L
                c.inputStream.use { input -> FileOutputStream(part).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var last = 0L
                    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                        val k = input.read(buf); if (k < 0) break
                        out.write(buf, 0, k); got += k
                        val now = System.currentTimeMillis()
                        if (now - last > 250) { last = now; set(m, Download(m, got, total, Download.State.RUNNING)) }
                    }
                } }
                c.disconnect()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
            } else if (failure != null) {
                throw failure!!
            }
            if (part.length() != total) error("size")
            if (failure == null && received() != total) error("size")
            seg.delete()
            // decrypt next to the target, check it really is a map, then put it in place
            val plain = File(target.parentFile, m.file + ".tmp")
            runCatching { decrypt(part, plain) }.onFailure { plain.delete(); part.delete(); error("invalid") }
            val ok = runCatching { OfflineMap(plain).use { it.isValid() } }.getOrDefault(false)
            if (!ok) { plain.delete(); part.delete(); error("invalid") }
            part.delete()
            if (target.exists()) target.delete()
            plain.renameTo(target)
            set(m, Download(m, target.length(), target.length(), Download.State.FINISHED))
            _finished.tryEmit(target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = when {
                e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException ->
                    "اتصال به اینترنت برقرار نشد. دوباره تلاش کنید؛ دانلود از همان‌جا ادامه پیدا می‌کند."
                e.message == "invalid" || e.message == "size" -> "فایل کامل دریافت نشد. دوباره تلاش کنید."
                else -> "دانلود انجام نشد (${e.message ?: "خطا"}). دوباره تلاش کنید."
            }
            val got = runCatching { if (seg.exists()) seg.readText().split(',').sumOf { it.trim().toLong() } else 0L }.getOrDefault(0L)
            set(m, Download(m, got, m.size, Download.State.FAILED, msg))
        } finally {
            synchronized(jobs) { jobs.remove(m.file) }
        }
    }
}
