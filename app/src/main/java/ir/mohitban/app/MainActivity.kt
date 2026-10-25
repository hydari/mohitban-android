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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            MohitbanTheme {
                AppNavHost(openReportsRequest = openReportsRequest.intValue, openShiftRequest = openShiftRequest.intValue)
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
            else -> return
        }
        intent.removeExtra(ReportReminder.EXTRA_OPEN)
    }
}
