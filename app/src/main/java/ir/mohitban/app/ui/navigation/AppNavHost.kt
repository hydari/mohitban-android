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
import ir.mohitban.app.core.text.fa
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
    const val GASHT = "gasht"
    const val VEHICLE = "vehicle/{id}"
    const val SOS = "sos/{id}"
    const val HELP = "help"
    const val DAILY = "daily"
    const val IDENTIFY = "identify"
    const val DAILY_EDIT = "daily_edit?id={id}"
    const val DAILY_VIEW = "daily_view/{id}"

    fun fuel(id: Long = 0L) = "fuel?entryId=$id"
    fun crop(path: String) = "crop?path=${Uri.encode(path)}"
    fun vehicle(id: Long) = "vehicle/$id"
    fun dailyEdit(id: Long = 0L) = "daily_edit?id=$id"
    fun dailyView(id: Long) = "daily_view/$id"
}

private const val RECEIPT_KEY = "cropped_receipt"
private const val VEHICLE_KEY = "new_vehicle"

private val TAB_ROUTES = setOf(Routes.HOME, Routes.GASHT, Routes.REPORTS, Routes.SETTINGS)

/** Tab switches and the splash/registration hand-over only cross-fade. */
private fun calm(from: String?, to: String?): Boolean =
    (from in TAB_ROUTES && to in TAB_ROUTES) || from == Routes.START || from == Routes.ONBOARDING || to == Routes.START

private fun NavHostController.openTab(tab: MainTab) {
    val route = when (tab) {
        MainTab.HOME -> Routes.HOME
        MainTab.GASHT -> Routes.GASHT
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
fun AppNavHost(openReportsRequest: Int = 0, openShiftRequest: Int = 0, openGashtRequest: Int = 0, openUpdateRequest: Int = 0) {
    val nav = rememberNavController()
    val context = LocalContext.current
    var started by remember { mutableStateOf(false) }

    // a new version on the host: checked by itself when the app opens (if the phone has internet); the ranger is
    // told once per version with a dialog, and the update itself is in settings
    val update by ir.mohitban.app.update.AppUpdate.state.collectAsState()
    var updateAsked by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(started) {
        // the app is open, so the dialog below tells the ranger (the notification is for the background check)
        if (started) ir.mohitban.app.update.AppUpdate.check(context.applicationContext, notify = false)
    }
    LaunchedEffect(openUpdateRequest, started) {
        if (openUpdateRequest > 0 && started) { updateAsked = (update as? ir.mohitban.app.update.UpdateState.Available)?.release?.versionCode; nav.openTab(MainTab.SETTINGS) }
    }
    val offered = (update as? ir.mohitban.app.update.UpdateState.Available)?.release
    // only over the app itself: never over the lock screen or an emergency alert
    val appOpen = ir.mohitban.app.security.AppLockStatus.open
    if (started && appOpen && offered != null && updateAsked != offered.versionCode &&
        ir.mohitban.app.update.UpdatePrompt.shouldAsk(context, offered.versionCode)
    ) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { updateAsked = offered.versionCode; ir.mohitban.app.update.UpdatePrompt.asked(context, offered.versionCode) },
            title = { Text("نسخه‌ی جدید محیط‌یار") },
            text = {
                Text(
                    "نسخه‌ی ${offered.versionName.fa()} آماده است. برای استفاده از امکانات و اصلاحات تازه، برنامه را به‌روز کنید." +
                        (if (offered.notes.isNotBlank()) "\n\n${offered.notes}" else ""),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    updateAsked = offered.versionCode
                    ir.mohitban.app.update.UpdatePrompt.asked(context, offered.versionCode)
                    nav.openTab(MainTab.SETTINGS)
                }) { Text("به‌روزرسانی") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    updateAsked = offered.versionCode
                    ir.mohitban.app.update.UpdatePrompt.asked(context, offered.versionCode)
                }) { Text("بعداً") }
            },
        )
    }

    // a tap on the quarterly-report reminder opens the reports tab
    LaunchedEffect(openReportsRequest, started) {
        if (openReportsRequest > 0 && started) { ir.mohitban.app.ui.report.ReportsHubTab.tab = 1; nav.openTab(MainTab.REPORTS) }
    }
    // a tap on the گشت‌یار notification (recording / off-route) opens it
    LaunchedEffect(openGashtRequest, started) {
        if (openGashtRequest > 0 && started) nav.openTab(MainTab.GASHT)
    }
    // a colleague's emergency SMS opens his position on the map
    val sosOpen by ir.mohitban.app.gasht.SosStore.open.collectAsState()
    LaunchedEffect(sosOpen, started) {
        val id = sosOpen ?: return@LaunchedEffect
        if (!started) return@LaunchedEffect
        ir.mohitban.app.gasht.SosStore.open.value = null
        nav.navigate("sos/$id") { launchSingleTop = true }
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
                onRecords = { ir.mohitban.app.ui.report.ReportsHubTab.tab = 0; nav.openTab(MainTab.REPORTS) },
                onReport = { ir.mohitban.app.ui.report.ReportsHubTab.tab = 1; nav.openTab(MainTab.REPORTS) },
                onVehicle = { nav.navigate(Routes.VEHICLES) },
                onProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                onSettings = { nav.openTab(MainTab.SETTINGS) },
                onAlerts = { nav.navigate(Routes.ALERTS) },
                onShift = { nav.navigate(Routes.SHIFT) },
                onGasht = { nav.openTab(MainTab.GASHT) },
                onDaily = { nav.navigate(Routes.DAILY) },
                onIdentify = { nav.navigate(Routes.IDENTIFY) },
                onTab = { nav.openTab(it) },
            )
        }
        // ---------------- daily patrol report
        composable(Routes.IDENTIFY) {
            ir.mohitban.app.ui.ai.IdentifyScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DAILY) {
            ir.mohitban.app.ui.daily.DailyReportsScreen(
                onBack = { nav.popBackStack() },
                onNew = { nav.navigate(Routes.dailyEdit()) },
                onOpen = { nav.navigate(Routes.dailyView(it)) },
            )
        }
        composable(Routes.DAILY_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val newVehicle by entry.savedStateHandle.getStateFlow<Long?>(VEHICLE_KEY, null).collectAsState()
            ir.mohitban.app.ui.daily.DailyReportEditScreen(
                id = id,
                newVehicleId = newVehicle,
                onVehicleConsumed = { entry.savedStateHandle[VEHICLE_KEY] = null },
                onAddVehicle = { nav.navigate(Routes.vehicle(0L)) },
                onBack = { nav.popBackStack() },
                onSaved = { saved ->
                    // the form replaces the editor, so "back" from the form returns to the list
                    nav.navigate(Routes.dailyView(saved)) { popUpTo(Routes.DAILY_EDIT) { inclusive = true } }
                },
            )
        }
        composable(Routes.DAILY_VIEW, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            ir.mohitban.app.ui.daily.DailyReportViewScreen(
                id = id,
                onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                onEdit = { nav.navigate(Routes.dailyEdit(it)) },
            )
        }
        composable(Routes.REPORTS) {
            ir.mohitban.app.ui.report.ReportsHub(onTab = { nav.openTab(it) }, onEdit = { nav.navigate(Routes.fuel(it)) })
        }
        composable(Routes.HELP) {
            ir.mohitban.app.ui.settings.HelpScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                onEditVehicle = { nav.navigate(Routes.VEHICLES) },
                onTab = { nav.openTab(it) },
                onBack = null,
                onHelp = { nav.navigate(Routes.HELP) },
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
                    if (id == 0L) { ir.mohitban.app.ui.report.ReportsHubTab.tab = 0; nav.openTab(MainTab.REPORTS) }
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
                        AlertTarget.REPORTS -> { ir.mohitban.app.ui.report.ReportsHubTab.tab = 1; nav.openTab(MainTab.REPORTS) }
                        AlertTarget.SETTINGS -> nav.openTab(MainTab.SETTINGS)
                        AlertTarget.SHIFT -> nav.navigate(Routes.SHIFT)
                    }
                },
            )
        }
        composable(Routes.GASHT) {
            ir.mohitban.app.ui.gasht.GashtScreen(onTab = { nav.openTab(it) }, onDaily = { nav.navigate(Routes.DAILY) }, onIdentify = { nav.navigate(Routes.IDENTIFY) })
        }
        composable(Routes.SOS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            ir.mohitban.app.ui.gasht.SosScreen(
                id = e.arguments?.getLong("id") ?: 0L,
                onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                onGuide = { nav.popBackStack(); nav.openTab(MainTab.GASHT) },
            )
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
