package ir.mohitban.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import ir.mohitban.app.security.AppLockGate
import ir.mohitban.app.security.leaveLockScreen
import ir.mohitban.app.reminder.ReportReminder
import ir.mohitban.app.ui.navigation.AppNavHost
import ir.mohitban.app.ui.theme.MohitbanTheme

class MainActivity : FragmentActivity() {
    /** Increases every time a reminder notification asks to open the reports screen. */
    private val openReportsRequest = mutableIntStateOf(0)
    /** Increases every time the shift reminder asks to open the shift screen. */
    private val openShiftRequest = mutableIntStateOf(0)
    private val openGashtRequest = mutableIntStateOf(0)
    /** Increases when the "new version" notification asks to open the update (in settings). */
    private val openUpdateRequest = mutableIntStateOf(0)
    /** A colleague's emergency must show at once, so that alert (and only it) is shown before the app lock. */
    private val skipLock = mutableIntStateOf(0)
    private val skipLockAlert = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            MohitbanTheme {
                AppLockGate(bypass = skipLock.intValue, bypassAlertId = skipLockAlert.longValue) {
                    AppNavHost(
                        openReportsRequest = openReportsRequest.intValue, openShiftRequest = openShiftRequest.intValue,
                        openGashtRequest = openGashtRequest.intValue, openUpdateRequest = openUpdateRequest.intValue,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        // showing over the phone's lock screen is only for the alert that was just received
        leaveLockScreen()
    }

    private fun handle(intent: Intent?) {
        when (intent?.getStringExtra(ReportReminder.EXTRA_OPEN)) {
            ReportReminder.OPEN_REPORTS -> openReportsRequest.intValue += 1
            ir.mohitban.app.reminder.ShiftReminder.OPEN_SHIFT -> openShiftRequest.intValue += 1
            ir.mohitban.app.gasht.GashtService.OPEN_GASHT -> openGashtRequest.intValue += 1
            ir.mohitban.app.update.AppUpdate.OPEN_UPDATE -> openUpdateRequest.intValue += 1
            ir.mohitban.app.gasht.SosNotifier.OPEN_SOS -> {
                // a colleague's emergency: show over the lock screen and wake the display. Only for an alert that really
                // arrived by SMS (not for any app that sends this extra), passed the team-code check and is recent; and even
                // then the lock gate shows just that alert, never the rest of the app.
                val sosId = intent.getLongExtra(ir.mohitban.app.gasht.SosNotifier.EXTRA_ID, 0L)
                val real = ir.mohitban.app.gasht.SosStore.get(this, sosId)
                    ?.let { it.verified && System.currentTimeMillis() - it.receivedAt < 15 * 60_000L } == true
                if (real) {
                    skipLockAlert.longValue = sosId
                    skipLock.intValue++ // a counter, so every new real alert asks the gate again
                    if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
                    else @Suppress("DEPRECATION") window.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    )
                }
                ir.mohitban.app.gasht.SosStore.open.value = intent.getLongExtra(ir.mohitban.app.gasht.SosNotifier.EXTRA_ID, 0L)
            }
            else -> return
        }
        intent.removeExtra(ReportReminder.EXTRA_OPEN)
    }
}
