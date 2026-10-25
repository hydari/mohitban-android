package ir.mohitban.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.mohitban.app.ui.navigation.AppNavHost
import ir.mohitban.app.ui.theme.MohitbanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MohitbanTheme {
                AppNavHost()
            }
        }
    }
}
