package ir.mohitban.app.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import ir.mohitban.app.BuildConfig
import ir.mohitban.app.MainActivity
import ir.mohitban.app.R
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.data.backup.BackupCrypto
import ir.mohitban.app.reminder.ReportReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One published version, as told by the host. */
data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    val size: Long,
    val sha256: String,
    val notes: String,
)

sealed interface UpdateState {
    /** Local build without the update key: nothing to check. */
    data object Disabled : UpdateState
    /** Not checked yet. */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** [progress] 0..1, or below 0 while the size is not known. */
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    /** The host could not be reached (no internet) or answered badly. [lastCheckedAt]: the last good check. */
    data class Offline(val lastCheckedAt: Long?) : UpdateState
    data class Failed(val message: String, val release: AppRelease?) : UpdateState
}

/**
 * In-app update from the ranger's own host (muhitban.ir/app/application), not from a store.
 *
 * The host keeps two encrypted files (made by tools/pack_update.py in the APK workflow): version.bin, which says
 * what the newest version is, and app.bin, the APK. Both are encrypted with the app's update key (AES-256-GCM, the
 * format of [BackupCrypto]); the key is put into the app at build time from the repository secret and is not in the code.
 * They are handed out only by index.php, and only for a request signed with a key derived from the same secret,
 * so a browser gets nothing, and a file copied some other way is unreadable.
 *
 * Before installing, the decrypted APK must match the SHA-256 of version.bin, carry this app's package name and a
 * higher version; Android itself then accepts it only when it is signed with the same key as the installed app.
 */
object AppUpdate {
    private const val BASE = "https://muhitban.ir/app/application/index.php"
    private const val PREFS = "app_update"
    const val OPEN_UPDATE = "update"
    private const val CHANNEL_ID = "app_update"
    private const val NOTIFICATION_ID = 1406
    /** The automatic check runs at most this often (the user's own "check again" always runs). */
    const val AUTO_EVERY_MS = 6 * 3_600_000L
    private const val MAX_MANIFEST = 64 * 1024
    private const val MAX_APK = 200L * 1024 * 1024

    private val mutex = Mutex()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> get() = _state
    private var loaded = false

    val enabled: Boolean get() = BuildConfig.UPDATE_KEY.length == 64

    val installedCode: Long get() = BuildConfig.VERSION_CODE.toLong()
    val installedName: String get() = BuildConfig.VERSION_NAME

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The state the phone knew before this check (shown at once, also without internet). */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        if (!enabled) { _state.value = UpdateState.Disabled; return }
        val saved = savedRelease(context)
        val at = prefs(context).getLong("checkedAt", 0L)
        _state.value = when {
            saved != null && saved.versionCode > installedCode -> UpdateState.Available(saved)
            at > 0 -> UpdateState.UpToDate(at)
            else -> UpdateState.Idle
        }
    }

    fun lastCheckedAt(context: Context): Long? = prefs(context).getLong("checkedAt", 0L).takeIf { it > 0 }

    private fun savedRelease(context: Context): AppRelease? {
        val json = prefs(context).getString("release", null) ?: return null
        return runCatching { parse(JSONObject(json)) }.getOrNull()
    }

    private fun parse(o: JSONObject) = AppRelease(
        versionCode = o.getLong("versionCode"),
        versionName = o.optString("versionName"),
        size = o.optLong("size"),
        sha256 = o.getString("sha256").lowercase(Locale.US),
        notes = o.optString("notes"),
    )

    // ------------------------------------------------------------------ talking to the host

    private fun hex(b: ByteArray) = b.joinToString("") { String.format(Locale.US, "%02x", it) }

    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun hmac(key: ByteArray, text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return hex(mac.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    /** The secret of index.php (the same derivation as tools/pack_update.py). */
    private val gateKey: ByteArray by lazy { hmac(unhex(BuildConfig.UPDATE_KEY), "gate").toByteArray(Charsets.US_ASCII) }

    private fun request(file: String, skewSec: Long): HttpURLConnection {
        val t = System.currentTimeMillis() / 1000 + skewSec
        val c = URL("$BASE?f=$file").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.useCaches = false
        c.setRequestProperty("User-Agent", "Mohityar/$installedName (Android)")
        c.setRequestProperty("X-Mohityar-Time", t.toString())
        c.setRequestProperty("X-Mohityar-Sig", hmac(gateKey, "$file|$t"))
        return c
    }

    /**
     * An open connection with an answer of 200, or null. A phone whose clock is wrong is refused at first; the host
     * then tells its own time and the request is signed again with the corrected clock.
     */
    private fun connect(context: Context, file: String): HttpURLConnection? {
        val p = prefs(context)
        var c = request(file, p.getLong("skew", 0L))
        if (c.responseCode == 200) return c
        val now = c.getHeaderField("X-Mohityar-Now")?.toLongOrNull()
        c.disconnect()
        if (now == null) return null
        val skew = now - System.currentTimeMillis() / 1000
        p.edit().putLong("skew", skew).apply()
        c = request(file, skew)
        if (c.responseCode == 200) return c
        c.disconnect()
        return null
    }

    // ------------------------------------------------------------------ check

    /**
     * Asks the host for the newest version. With [maxAgeMs] a recent answer is kept instead (the automatic checks);
     * pass 0 to always ask. Never throws: without internet the state becomes [UpdateState.Offline].
     */
    suspend fun check(context: Context, maxAgeMs: Long = AUTO_EVERY_MS, notify: Boolean = true): UpdateState {
        load(context)
        if (!enabled) return UpdateState.Disabled
        return mutex.withLock {
            val current = _state.value
            if (current is UpdateState.Downloading) return@withLock current
            val p = prefs(context)
            val at = p.getLong("checkedAt", 0L)
            if (maxAgeMs > 0 && at > 0 && System.currentTimeMillis() - at < maxAgeMs && current !is UpdateState.Idle) return@withLock current
            _state.value = UpdateState.Checking
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val c = connect(context, "version") ?: throw IOException("no answer")
                    val body = try { c.inputStream.use { readCapped(it, MAX_MANIFEST) } } finally { c.disconnect() }
                    if (!BackupCrypto.looksEncrypted(body)) throw ir.mohitban.app.data.backup.BackupWrongPassword()
                    val plain = BackupCrypto.open(ByteArrayInputStream(body), BuildConfig.UPDATE_KEY).use { it.readBytes() }
                    parse(JSONObject(String(plain, Charsets.UTF_8)))
                }
            }
            val release = result.getOrNull()
            val next = if (release == null) {
                UpdateState.Offline(at.takeIf { it > 0 })
            } else {
                val now = System.currentTimeMillis()
                p.edit().putLong("checkedAt", now).putString("release", JSONObject().apply {
                    put("versionCode", release.versionCode); put("versionName", release.versionName); put("size", release.size)
                    put("sha256", release.sha256); put("notes", release.notes)
                }.toString()).apply()
                if (release.versionCode > installedCode) {
                    if (notify && p.getLong("notified", 0L) != release.versionCode) {
                        if (showNotification(context, release)) p.edit().putLong("notified", release.versionCode).apply()
                    }
                    UpdateState.Available(release)
                } else {
                    cleanup(context)
                    UpdateState.UpToDate(now)
                }
            }
            // without internet keep showing a known newer version rather than only "offline"
            _state.value = if (next is UpdateState.Offline) savedRelease(context)?.takeIf { it.versionCode > installedCode }
                ?.let { UpdateState.Available(it) } ?: next else next
            _state.value
        }
    }

    private fun readCapped(input: java.io.InputStream, cap: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            if (out.size() + r > cap) throw IOException("answer too large")
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ download and install

    private fun dir(context: Context) = File(context.cacheDir, "update").apply { mkdirs() }

    /** Removes downloaded files (after a successful update or when they are no longer needed). */
    fun cleanup(context: Context) {
        runCatching { File(context.cacheDir, "update").deleteRecursively() }
    }

    /** True when Android lets this app ask to install (Android 8+: "install unknown apps" for Mohityar). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the system page where the ranger allows Mohityar to install updates. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Downloads, decrypts and checks the APK of [release], then hands it to Android's installer (which asks the
     * ranger to confirm). Never throws: problems end in [UpdateState.Failed].
     */
    suspend fun downloadAndInstall(context: Context, release: AppRelease) {
        if (!enabled) return
        if (_state.value is UpdateState.Downloading) return
        // waits for a check that may be running (opening settings starts one)
        mutex.lock()
        try {
            if (_state.value is UpdateState.Downloading) return
            _state.value = UpdateState.Downloading(release, -1f)
            val result = withContext(Dispatchers.IO) { runCatching { download(context, release) } }
            val apk = result.getOrElse { e ->
                _state.value = UpdateState.Failed(
                    when (e) {
                        is UpdateError -> e.message.orEmpty()
                        is ir.mohitban.app.data.backup.BackupWrongPassword -> "فایل به‌روزرسانی روی سرور معتبر نیست یا آسیب دیده است."
                        else -> "دانلود انجام نشد. اتصال اینترنت را بررسی کنید و دوباره تلاش کنید."
                    },
                    release,
                )
                return
            }
            val installed = withContext(Dispatchers.IO) { runCatching { install(context, apk) } }
            _state.value = if (installed.isSuccess) UpdateState.Available(release)
            else UpdateState.Failed("نصب شروع نشد: ${installed.exceptionOrNull()?.message.orEmpty()}", release)
        } finally {
            mutex.unlock()
        }
    }

    private class UpdateError(message: String) : IOException(message)

    private fun download(context: Context, release: AppRelease): File {
        val d = dir(context)
        val enc = File(d, "app.bin")
        val apk = File(d, "Mohityar.apk")
        enc.delete(); apk.delete()
        val c = connect(context, "app") ?: throw IOException("no answer")
        try {
            val total = c.contentLength.toLong()
            if (total > MAX_APK) throw UpdateError("فایل به‌روزرسانی بیش از حد بزرگ است.")
            c.inputStream.use { input ->
                enc.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    var shown = -1
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        got += r
                        if (got > MAX_APK) throw UpdateError("فایل به‌روزرسانی بیش از حد بزرگ است.")
                        out.write(buf, 0, r)
                        if (total > 0) {
                            val pct = (got * 100 / total).toInt()
                            if (pct != shown) { shown = pct; _state.value = UpdateState.Downloading(release, pct / 100f) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        // only an encrypted file is accepted (BackupCrypto would pass a plain one through unchanged)
        val head = ByteArray(BackupCrypto.headerProbeSize)
        val got = enc.inputStream().use { it.read(head) }
        if (got != head.size || !BackupCrypto.looksEncrypted(head)) { enc.delete(); throw ir.mohitban.app.data.backup.BackupWrongPassword() }
        // decrypt while computing the fingerprint of what comes out
        val digest = MessageDigest.getInstance("SHA-256")
        BackupCrypto.open(enc.inputStream().buffered(), BuildConfig.UPDATE_KEY).use { input ->
            apk.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    digest.update(buf, 0, r)
                    out.write(buf, 0, r)
                }
            }
        }
        enc.delete()
        if (hex(digest.digest()) != release.sha256) { apk.delete(); throw UpdateError("فایل دانلودشده با نسخه‌ی اعلام‌شده یکی نیست.") }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        val code = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        if (info?.packageName != context.packageName || code <= installedCode) {
            apk.delete(); throw UpdateError("فایل دانلودشده نسخه‌ی تازه‌تری از محیط‌یار نیست.")
        }
        return apk
    }

    /** Gives the APK to Android's package installer; the confirmation screen comes back to [UpdateInstallReceiver]. */
    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(context, id, Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName), flags)
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    internal fun installFailed(message: String) {
        val r = (_state.value as? UpdateState.Available)?.release ?: (_state.value as? UpdateState.Failed)?.release
        _state.value = UpdateState.Failed(message, r)
    }

    // ------------------------------------------------------------------ notification

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL_ID, "به‌روزرسانی برنامه", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "وقتی نسخه‌ی تازه‌ی محیط‌یار منتشر شود خبر می‌دهد"
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, r: AppRelease): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        channel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReportReminder.EXTRA_OPEN, OPEN_UPDATE)
        }
        val pi = PendingIntent.getActivity(context, 9, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = "نسخه‌ی ${r.versionName.fa()} آماده است. برای به‌روزرسانی بزنید."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFF1F6B3A.toInt())
            .setContentTitle("به‌روزرسانی محیط‌یار")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (r.notes.isBlank()) text else "$text\n${r.notes}"))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n); true }.getOrDefault(false)
    }
}

/** The "new version" dialog when the app opens: once a day at most for the same version. */
object UpdatePrompt {
    private const val PREFS = "app_update_prompt"
    private const val AGAIN_MS = 24 * 3_600_000L

    fun shouldAsk(context: Context, versionCode: Long): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getLong("version", 0L) != versionCode || System.currentTimeMillis() - p.getLong("at", 0L) > AGAIN_MS
    }

    fun asked(context: Context, versionCode: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("version", versionCode).putLong("at", System.currentTimeMillis()).apply()
    }
}

/** Answers of Android's package installer for the update session. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android's own "do you want to install this update?" screen
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { AppUpdate.installFailed("صفحه‌ی نصب باز نشد. دوباره «نصب» را بزنید.") }
            }
            PackageInstaller.STATUS_SUCCESS -> AppUpdate.cleanup(context)
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdate.installFailed("نصب لغو شد.")
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                AppUpdate.installFailed(
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                        "این نسخه با برنامه‌ی نصب‌شده سازگار نیست (امضای متفاوت)."
                    else "نصب انجام نشد. ${why}".trim(),
                )
            }
        }
    }
}
