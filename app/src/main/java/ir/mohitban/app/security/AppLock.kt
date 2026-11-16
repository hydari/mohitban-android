package ir.mohitban.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.mohitban.app.R
import ir.mohitban.app.container
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Whether the ranger wants the app locked (on by default). */
object AppLockPrefs {
    private const val FILE = "app_lock"
    private const val KEY = "enabled"
    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}

/**
 * Fingerprint or face when the phone has them; otherwise (older or low-end phones) the screen lock of
 * the device itself: pattern, PIN or password. Nothing is stored by the app.
 */
object AppLock {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True when the phone can ask for a biometric or for its pattern / PIN / password. */
    fun available(context: Context): Boolean =
        runCatching { BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: (code: Int, message: String) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errorCode, errString.toString())
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود به محیط‌یار")
            .setSubtitle("برای ورود، هویت خود را تأیید کنید")
            .setAllowedAuthenticators(AUTHENTICATORS) // with a device credential allowed there is no negative button
            .build()
        runCatching { BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info) }
            .onFailure { onFailure(BiometricPrompt.ERROR_HW_UNAVAILABLE, it.message.orEmpty()) }
    }
}

/** Lives as long as the app's process: a killed or swiped-away app always asks again. */
private object LockState {
    var unlocked by mutableStateOf(false)
    /**
     * A colleague's emergency shown on its own: only that alert (its map, distance and actions) is visible,
     * never the rest of the app, until the ranger proves who they are. Null when there is none.
     */
    var alertId by mutableStateOf<Long?>(null)
    /** The alert was opened over the phone's own lock screen (the phone itself was locked). */
    var alertOverKeyguard = false
    var alertUntil = 0L
    var leftAfterAlert = false
    /** How long an emergency stays on screen without the lock. */
    const val ALERT_WINDOW_MS = 3 * 60_000L
    var stoppedAt = 0L
    /** How long the app may stay in the background before it asks again. */
    const val GRACE_MS = 60_000L
}

private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/** True while the app itself is on screen (not the lock, nor an emergency alert alone); the app's own dialogs wait for it. */
object AppLockStatus {
    var open by mutableStateOf(false)
        internal set
}

/** Stops showing the app over the phone's lock screen (turned on only for a colleague's emergency). */
fun Activity.leaveLockScreen() {
    if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(false)
        setTurnScreenOn(false)
    } else {
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
    }
}

private fun phoneLocked(context: Context): Boolean =
    runCatching { ContextCompat.getSystemService(context, KeyguardManager::class.java)?.isKeyguardLocked == true }.getOrDefault(false)

/**
 * Covers the app with a lock screen until the ranger proves it is them. Only after registration
 * (there is nothing to protect before), and only when the phone has some way to ask. [bypass] goes up by one
 * for every real, verified emergency from a colleague ([bypassAlertId]). An emergency never opens the whole app:
 * for a few minutes (and only while it stays in front) it shows just that alert. The same happens when the
 * phone itself is locked, even if the app lock is off, so nobody holding a locked phone can reach the ranger's data.
 */
@Composable
fun AppLockGate(bypass: Int, bypassAlertId: Long = 0L, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.fragmentActivity() }
    var enabled by remember { mutableStateOf(AppLockPrefs.isEnabled(context)) }
    var available by remember { mutableStateOf(AppLock.available(context)) }
    var hasData by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }

    fun closeAlert() {
        if (LockState.alertId == null) return
        LockState.alertId = null
        if (LockState.alertOverKeyguard) activity?.leaveLockScreen()
        LockState.alertOverKeyguard = false
    }

    LaunchedEffect(Unit) { hasData = withContext(Dispatchers.IO) { runCatching { context.container.profileRepository.getProfile() != null }.getOrDefault(true) } }
    // an emergency shows only its own alert, for a few minutes and only while the app stays in front
    LaunchedEffect(bypass) {
        if (bypass > 0) {
            val overKeyguard = phoneLocked(context)
            if (overKeyguard || !LockState.unlocked) {
                LockState.alertId = bypassAlertId
                LockState.alertOverKeyguard = overKeyguard
                LockState.leftAfterAlert = false
                LockState.alertUntil = SystemClock.elapsedRealtime() + LockState.ALERT_WINDOW_MS
            }
        }
    }
    LaunchedEffect(LockState.alertId) {
        if (LockState.alertId != null) {
            val left = LockState.alertUntil - SystemClock.elapsedRealtime()
            if (left > 0) delay(left)
            closeAlert()
        }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { LockState.stoppedAt = SystemClock.elapsedRealtime(); resumed = false; if (LockState.alertId != null) LockState.leftAfterAlert = true }
                Lifecycle.Event.ON_START -> {
                    enabled = AppLockPrefs.isEnabled(context)
                    available = AppLock.available(context)
                    if (LockState.leftAfterAlert) { LockState.leftAfterAlert = false; closeAlert() }
                    if (LockState.unlocked && LockState.stoppedAt > 0 && SystemClock.elapsedRealtime() - LockState.stoppedAt > LockState.GRACE_MS) LockState.unlocked = false
                }
                Lifecycle.Event.ON_RESUME -> resumed = true
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumed = true
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val mustAsk = enabled && available && hasData == true && !LockState.unlocked
    // only the emergency itself, never the rest of the app, while the ranger has not proved who they are
    // (or while the phone itself is still locked)
    val alertOnly = LockState.alertId != null && (LockState.alertOverKeyguard || mustAsk)
    // before the answer about registration is known nothing is shown, so the dashboard never flashes
    val covered = hasData == null || mustAsk || alertOnly
    SideEffect { AppLockStatus.open = !covered }

    fun ask() {
        val a = activity ?: run { LockState.unlocked = true; return }
        if (asking) return
        asking = true
        message = null
        AppLock.prompt(
            a,
            onSuccess = { asking = false; LockState.unlocked = true },
            onFailure = { code, text ->
                asking = false
                when (code) {
                    // the phone has nothing to ask with: do not lock the ranger out of their own app
                    BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> LockState.unlocked = true
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> message = null
                    else -> message = text.ifBlank { "تأیید هویت انجام نشد. دوباره تلاش کنید." }
                }
            },
        )
    }

    // ask by itself as soon as the lock screen is in front
    LaunchedEffect(mustAsk, resumed, alertOnly) { if (mustAsk && resumed && !alertOnly) ask() }

    Box(Modifier.fillMaxSize()) {
        content()
        if (covered) {
            // the back button must not reach the screens underneath
            BackHandler(enabled = true) { activity?.moveTaskToBack(true) }
            Box(
                Modifier.fillMaxSize().background(MColors.Bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) {
                val alertId = LockState.alertId
                if (alertOnly && alertId != null) {
                    // leaving the alert (back or "guide me there") goes to the lock: the rest of the app needs the ranger
                    ir.mohitban.app.ui.gasht.SosScreen(id = alertId, onBack = { closeAlert() }, onGuide = { closeAlert() })
                } else if (mustAsk) {
                    Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        // the app's own badge (it has its own empty margin, so it is drawn larger than it looks)
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, modifier = Modifier.size(200.dp))
                        Text("محیط‌یار", style = MaterialTheme.typography.headlineSmall, color = MColors.Ink, modifier = Modifier.padding(top = 0.dp))
                        Text(
                            "برنامه قفل است. با اثر انگشت، چهره یا الگو و رمز گوشی وارد شوید.",
                            style = MaterialTheme.typography.bodyMedium, color = MColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                        )
                        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MColors.Error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
                        PillButton("ورود", { ask() }, Modifier.padding(top = 28.dp), icon = Icons.Outlined.Fingerprint, enabled = !asking)
                    }
                }
            }
        }
    }
}
