package ir.mohitban.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import ir.mohitban.app.reminder.ReportReminder
import ir.mohitban.app.ui.navigation.AppNavHost
import ir.mohitban.app.ui.theme.MohitbanTheme

class MainActivity : ComponentActivity() {
    /** Increases every time a reminder notification asks to open the reports screen. */
    private val openReportsRequest = mutableIntStateOf(0)
    /** Increases every time the shift reminder asks to open the shift screen. */
    private val openShiftRequest = mutableIntStateOf(0)
    private val openGashtRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            MohitbanTheme {
                AppNavHost(openReportsRequest = openReportsRequest.intValue, openShiftRequest = openShiftRequest.intValue, openGashtRequest = openGashtRequest.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.getStringExtra(ReportReminder.EXTRA_OPEN)) {
            ReportReminder.OPEN_REPORTS -> openReportsRequest.intValue += 1
            ir.mohitban.app.reminder.ShiftReminder.OPEN_SHIFT -> openShiftRequest.intValue += 1
            ir.mohitban.app.gasht.GashtService.OPEN_GASHT -> openGashtRequest.intValue += 1
            ir.mohitban.app.gasht.SosNotifier.OPEN_SOS -> {
                // a colleague's emergency: show over the lock screen and wake the display
                if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
                else @Suppress("DEPRECATION") window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                )
                ir.mohitban.app.gasht.SosStore.open.value = intent.getLongExtra(ir.mohitban.app.gasht.SosNotifier.EXTRA_ID, 0L)
            }
            else -> return
        }
        intent.removeExtra(ReportReminder.EXTRA_OPEN)
    }
}
