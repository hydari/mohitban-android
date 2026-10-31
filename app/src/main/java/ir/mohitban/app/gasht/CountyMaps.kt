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
    const val BASE = "https://github.com/hydari/mohitban-android/releases/download/maps-v1/"

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

    private suspend fun run(context: Context, m: CountyMap) {
        val target = CountyMaps.local(context, m)
        val part = File(target.parentFile, m.file + ".part")
        try {
            fun open(u: URL): HttpURLConnection {
                // only the published map files (GitHub hands the file over from its download host, https only)
                require(u.protocol == "https") { "insecure" }
                return (u.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000; readTimeout = 30_000
                    setRequestProperty("User-Agent", "Mohityar")
                    if (part.exists() && part.length() > 0) setRequestProperty("Range", "bytes=${part.length()}-")
                }
            }
            var url = URL(BASE + m.file)
            var conn = open(url)
            var redirects = 0
            while (conn.responseCode in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location") ?: error("redirect")
                conn.disconnect()
                url = URL(url, loc); redirects++
                conn = open(url)
            }
            val code = conn.responseCode
            val append = code == 206
            if (code == 416 && part.length() != m.size) { part.delete(); error("HTTP 416") }
            if (code == 416) {
                conn.disconnect()
            } else {
            if (code != 200 && code != 206) error("HTTP $code")
            val total = if (append) part.length() + conn.contentLengthLong.coerceAtLeast(0) else conn.contentLengthLong.takeIf { it > 0 } ?: m.size
            var done = if (append) part.length() else 0L
            set(m, Download(m, done, total, Download.State.RUNNING))
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var last = 0L
                    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 200) { last = now; set(m, Download(m, done, total, Download.State.RUNNING)) }
                    }
                }
            }
            conn.disconnect()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            }
            if (m.size > 0 && part.length() != m.size) error("size")
            val ok = runCatching { OfflineMap(part).use { it.isValid() } }.getOrDefault(false)
            if (!ok) { part.delete(); error("invalid") }
            if (target.exists()) target.delete()
            part.renameTo(target)
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
            set(m, Download(m, part.length(), m.size, Download.State.FAILED, msg))
        } finally {
            synchronized(jobs) { jobs.remove(m.file) }
        }
    }
}
