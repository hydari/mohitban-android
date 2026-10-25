package ir.mohitban.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.mohitban.app.R
import ir.mohitban.app.container
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.dashboard.DashboardScreen
import ir.mohitban.app.ui.fuel.FuelFormScreen
import ir.mohitban.app.ui.fuel.ReceiptCropScreen
import ir.mohitban.app.ui.onboarding.OnboardingScreen
import ir.mohitban.app.ui.profile.EditSection
import ir.mohitban.app.ui.profile.ProfileEditScreen
import ir.mohitban.app.ui.records.RecordsScreen
import ir.mohitban.app.ui.report.ReportScreen
import ir.mohitban.app.ui.settings.SettingsScreen

object Routes {
    const val START = "start"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val RECORDS = "records"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val FUEL = "fuel?entryId={entryId}"
    const val CROP = "crop?path={path}"
    const val EDIT = "edit/{section}"

    fun fuel(id: Long = 0L) = "fuel?entryId=$id"
    fun crop(path: String) = "crop?path=${Uri.encode(path)}"
    fun edit(section: EditSection) = "edit/${section.name}"
}

private const val RECEIPT_KEY = "cropped_receipt"

private fun NavHostController.openTab(tab: MainTab) {
    val route = when (tab) {
        MainTab.HOME -> Routes.HOME
        MainTab.RECORDS -> Routes.RECORDS
        MainTab.REPORTS -> Routes.REPORTS
        MainTab.SETTINGS -> Routes.SETTINGS
    }
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = nav, startDestination = Routes.START) {
        composable(Routes.START) {
            // tiny splash while deciding between first-run registration and the dashboard
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.doe_logo_full), null, modifier = Modifier.size(120.dp))
            }
            LaunchedEffect(Unit) {
                val registered = context.container.profileRepository.isRegistered()
                nav.navigate(if (registered) Routes.HOME else Routes.ONBOARDING) {
                    popUpTo(Routes.START) { inclusive = true }
                }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            DashboardScreen(
                onFuel = { nav.navigate(Routes.fuel()) },
                onRecords = { nav.openTab(MainTab.RECORDS) },
                onReport = { nav.openTab(MainTab.REPORTS) },
                onVehicle = { nav.navigate(Routes.edit(EditSection.VEHICLE)) },
                onProfile = { nav.navigate(Routes.edit(EditSection.PROFILE)) },
                onSettings = { nav.openTab(MainTab.SETTINGS) },
                onTab = { nav.openTab(it) },
            )
        }
        composable(Routes.RECORDS) {
            RecordsScreen(onEdit = { nav.navigate(Routes.fuel(it)) }, onTab = { nav.openTab(it) }, onBack = null)
        }
        composable(Routes.REPORTS) {
            ReportScreen(onTab = { nav.openTab(it) }, onBack = null)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onEditProfile = { nav.navigate(Routes.edit(EditSection.PROFILE)) },
                onEditVehicle = { nav.navigate(Routes.edit(EditSection.VEHICLE)) },
                onTab = { nav.openTab(it) },
                onBack = null,
                onRestored = {
                    nav.navigate(Routes.START) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable(
            Routes.FUEL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType; defaultValue = 0L }),
        ) { entry ->
            val id = entry.arguments?.getLong("entryId") ?: 0L
            val cropped by entry.savedStateHandle.getStateFlow<String?>(RECEIPT_KEY, null).collectAsState()
            FuelFormScreen(
                entryId = id,
                croppedReceipt = cropped,
                onReceiptConsumed = { entry.savedStateHandle[RECEIPT_KEY] = null },
                onCropReceipt = { file -> nav.navigate(Routes.crop(file.absolutePath)) },
                onBack = { nav.popBackStack() },
                onDone = {
                    nav.popBackStack()
                    if (id == 0L) nav.openTab(MainTab.RECORDS)
                },
            )
        }
        composable(
            Routes.CROP,
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            ReceiptCropScreen(
                rawPath = path,
                onDone = { stored ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(RECEIPT_KEY, stored)
                    nav.popBackStack()
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("section") { type = NavType.StringType }),
        ) { entry ->
            val section = runCatching { EditSection.valueOf(entry.arguments?.getString("section").orEmpty()) }
                .getOrDefault(EditSection.PROFILE)
            ProfileEditScreen(section, onBack = { nav.popBackStack() })
        }
    }
}
