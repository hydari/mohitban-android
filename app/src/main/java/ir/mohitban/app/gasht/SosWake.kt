package ir.mohitban.app.gasht

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * Opens the app by itself for a colleague's emergency, also when it is closed and the phone is in use.
 *
 * Android lets an app start a screen from the background only in a few cases. The full-screen notification covers a
 * locked or dark screen; while the phone is in use Android shows it only as a banner. With "display over other apps"
 * allowed, the app may start its screen at once; Android 15 asks in addition that the app shows something over the
 * other apps at that moment, so a 1-pixel invisible window is put up for a few seconds.
 */
object SosWake {
    fun canOverlay(context: Context): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /** Brings [intent] (the alert screen) to the front now. Call on the main thread (a receiver's onReceive is). */
    fun bringToFront(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!canOverlay(app)) return
        val wm = ContextCompat.getSystemService(app, WindowManager::class.java)
        var view: View? = null
        if (wm != null && Build.VERSION.SDK_INT >= 26) {
            runCatching {
                val v = View(app)
                val lp = WindowManager.LayoutParams(
                    1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                ).apply { gravity = Gravity.TOP or Gravity.START }
                wm.addView(v, lp)
                view = v
            }
        }
        runCatching { app.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        view?.let { v -> Handler(Looper.getMainLooper()).postDelayed({ runCatching { wm?.removeView(v) } }, 4_000) }
    }
}

/** What the phone must allow so an emergency SMS reaches the ranger even when the app is closed. */
object SosReadiness {
    enum class Item(val title: String, val why: String) {
        SMS("دریافت پیامک", "بدون آن پیامک اضطراری همکاران خوانده نمی‌شود."),
        NOTIFY("اعلان‌ها", "بدون آن هشدار و صدای آژیر پخش نمی‌شود."),
        FULL_SCREEN("اعلان تمام‌صفحه", "برای باز شدن خودکار روی صفحه‌ی قفل."),
        OVERLAY("نمایش روی برنامه‌های دیگر", "برای باز شدن خودکار وقتی با گوشی کار می‌کنید."),
        BATTERY("بدون محدودیت باتری", "تا گوشی برنامه را در پس‌زمینه نبندد."),
    }

    fun missing(context: Context): List<Item> = buildList {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) add(Item.SMS)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) add(Item.NOTIFY)
        if (!SosNotifier.canFullScreen(context)) add(Item.FULL_SCREEN)
        if (!SosWake.canOverlay(context)) add(Item.OVERLAY)
        if (Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.getSystemService(context, PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == false
        ) add(Item.BATTERY)
    }

    /** The system page for an item that is not a plain runtime permission (SMS and notifications are asked directly). */
    @SuppressLint("BatteryLife")
    fun settingsIntent(context: Context, item: Item): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (item) {
            Item.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null
            Item.OVERLAY -> if (Build.VERSION.SDK_INT >= 23) Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg) else null
            Item.BATTERY -> if (Build.VERSION.SDK_INT >= 23) Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg) else null
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Some makers (Xiaomi, Huawei, Oppo, Vivo…) do not deliver any SMS to an app that was swiped away until "auto-start"
     * is allowed for it. Their page if this phone has one; null on other phones.
     */
    fun autoStartIntent(context: Context): Intent? {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        )
        val pm = context.packageManager
        return candidates.asSequence()
            .map { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { runCatching { pm.resolveActivity(it, 0) != null }.getOrDefault(false) }
    }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
