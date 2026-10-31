package ir.mohitban.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ir.mohitban.app.ui.alerts.AlertTarget
import ir.mohitban.app.ui.alerts.AlertsScreen
import ir.mohitban.app.ui.components.MainTab
import ir.mohitban.app.ui.dashboard.DashboardScreen
import ir.mohitban.app.ui.fuel.FuelFormScreen
import ir.mohitban.app.ui.fuel.ReceiptCropScreen
import ir.mohitban.app.ui.onboarding.OnboardingScreen
import ir.mohitban.app.ui.profile.VehicleEditScreen
import ir.mohitban.app.ui.profile.VehiclesScreen
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
    const val EDIT_PROFILE = "edit_profile"
    const val VEHICLES = "vehicles"
    const val ALERTS = "alerts"
    const val SHIFT = "shift"
    const val GASHT = "gasht?tab={tab}"
    fun gasht(tab: Int = 0) = "gasht?tab=$tab"
    const val VEHICLE = "vehicle/{id}"

    fun fuel(id: Long = 0L) = "fuel?entryId=$id"
    fun crop(path: String) = "crop?path=${Uri.encode(path)}"
    fun vehicle(id: Long) = "vehicle/$id"
}

private const val RECEIPT_KEY = "cropped_receipt"
private const val VEHICLE_KEY = "new_vehicle"

private val TAB_ROUTES = setOf(Routes.HOME, Routes.RECORDS, Routes.REPORTS, Routes.SETTINGS)

/** Tab switches and the splash/registration hand-over only cross-fade. */
private fun calm(from: String?, to: String?): Boolean =
    (from in TAB_ROUTES && to in TAB_ROUTES) || from == Routes.START || from == Routes.ONBOARDING || to == Routes.START

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
fun AppNavHost(openReportsRequest: Int = 0, openShiftRequest: Int = 0, openGashtRequest: Int = 0) {
    val nav = rememberNavController()
    val context = LocalContext.current
    var started by remember { mutableStateOf(false) }

    // a tap on the quarterly-report reminder opens the reports tab
    LaunchedEffect(openReportsRequest, started) {
        if (openReportsRequest > 0 && started) nav.openTab(MainTab.REPORTS)
    }
    // a tap on the گشت‌یار notification (recording / off-route) opens it
    LaunchedEffect(openGashtRequest, started) {
        if (openGashtRequest > 0 && started && nav.currentDestination?.route != Routes.GASHT) nav.navigate(Routes.gasht())
    }
    // a tap on the shift reminder opens the shift calendar
    LaunchedEffect(openShiftRequest, started) {
        if (openShiftRequest > 0 && started && nav.currentDestination?.route != Routes.SHIFT) nav.navigate(Routes.SHIFT)
    }

    NavHost(
        navController = nav,
        startDestination = Routes.START,
        // tabs cross-fade; deeper pages slide in from the reading side and back out the same way
        enterTransition = {
            if (calm(initialState.destination.route, targetState.destination.route)) fadeIn(tween(220, delayMillis = 60))
            else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320, easing = EaseOutCubic)) { it / 4 } + fadeIn(tween(240))
        },
        exitTransition = {
            if (calm(initialState.destination.route, targetState.destination.route)) fadeOut(tween(140))
            else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320, easing = EaseOutCubic)) { it / 10 } + fadeOut(tween(220))
        },
        popEnterTransition = {
            if (calm(initialState.destination.route, targetState.destination.route)) fadeIn(tween(220, delayMillis = 60))
            else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300, easing = EaseOutCubic)) { it / 10 } + fadeIn(tween(220))
        },
        popExitTransition = {
            if (calm(initialState.destination.route, targetState.destination.route)) fadeOut(tween(140))
            else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(260, easing = EaseInCubic)) { it / 4 } + fadeOut(tween(200))
        },
    ) {
        composable(Routes.START) {
            // short animated splash while deciding between first-run registration and the dashboard
            val appear = remember { Animatable(0f) }
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(R.drawable.doe_logo_full), null,
                        modifier = Modifier.size(120.dp).graphicsLayer {
                            alpha = appear.value.coerceIn(0f, 1f)
                            val sc = 0.82f + 0.18f * appear.value
                            scaleX = sc; scaleY = sc
                        },
                    )
                    Text(
                        "محیط‌یار", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = MColors.Green,
                        modifier = Modifier.padding(top = 14.dp).graphicsLayer {
                            alpha = ((appear.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                            translationY = (1f - appear.value) * 18.dp.toPx()
                        },
                    )
                }
            }
            LaunchedEffect(Unit) {
                val started0 = android.os.SystemClock.elapsedRealtime()
                launch { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
                val registered = context.container.profileRepository.isRegistered()
                // keep the splash just long enough to be seen (about 0.7 s)
                val left = 700 - (android.os.SystemClock.elapsedRealtime() - started0)
                if (left > 0) delay(left)
                nav.navigate(if (registered) Routes.HOME else Routes.ONBOARDING) {
                    popUpTo(Routes.START) { inclusive = true }
                }
                started = registered
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                started = true
            })
        }
        composable(Routes.HOME) {
            DashboardScreen(
                onFuel = { nav.navigate(Routes.fuel()) },
                onRecords = { nav.openTab(MainTab.RECORDS) },
                onReport = { nav.openTab(MainTab.REPORTS) },
                onVehicle = { nav.navigate(Routes.VEHICLES) },
                onProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                onSettings = { nav.openTab(MainTab.SETTINGS) },
                onAlerts = { nav.navigate(Routes.ALERTS) },
                onShift = { nav.navigate(Routes.SHIFT) },
                onGasht = { nav.navigate(Routes.gasht()) },
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
                onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                onEditVehicle = { nav.navigate(Routes.VEHICLES) },
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
            val newVehicle by entry.savedStateHandle.getStateFlow<Long?>(VEHICLE_KEY, null).collectAsState()
            FuelFormScreen(
                entryId = id,
                newVehicleId = newVehicle,
                onVehicleConsumed = { entry.savedStateHandle[VEHICLE_KEY] = null },
                onAddVehicle = { nav.navigate(Routes.vehicle(0L)) },
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
        composable(Routes.EDIT_PROFILE) {
            ProfileEditScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ALERTS) {
            AlertsScreen(
                onBack = { nav.popBackStack() },
                onOpen = { target ->
                    nav.popBackStack()
                    when (target) {
                        AlertTarget.REPORTS -> nav.openTab(MainTab.REPORTS)
                        AlertTarget.SETTINGS -> nav.openTab(MainTab.SETTINGS)
                        AlertTarget.SHIFT -> nav.navigate(Routes.SHIFT)
                        AlertTarget.PATROL -> nav.navigate(Routes.gasht(3))
                    }
                },
            )
        }
        composable(Routes.GASHT, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { entry ->
            ir.mohitban.app.ui.gasht.GashtScreen(onBack = { nav.popBackStack() }, initialTab = entry.arguments?.getInt("tab") ?: 0)
        }
        composable(Routes.SHIFT) {
            ir.mohitban.app.ui.shift.ShiftScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.VEHICLES) {
            VehiclesScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.vehicle(it)) })
        }
        composable(
            Routes.VEHICLE,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            VehicleEditScreen(
                vehicleId = id,
                onBack = { nav.popBackStack() },
                onSaved = { savedId ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(VEHICLE_KEY, savedId)
                    nav.popBackStack()
                },
            )
        }
    }
}
