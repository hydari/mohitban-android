package ir.mohitban.app

import android.content.Context

/** Reads the installed version name without enabling BuildConfig generation. */
object BuildConfigCompat {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
}
