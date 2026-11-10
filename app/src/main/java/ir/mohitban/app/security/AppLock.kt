package ir.mohitban.app.security

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
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
    var stoppedAt = 0L
    /** How long the app may stay in the background before it asks again. */
    const val GRACE_MS = 60_000L
}

private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/**
 * Covers the app with a lock screen until the ranger proves it is them. Only after registration
 * (there is nothing to protect before), and only when the phone has some way to ask. [bypass] lets an
 * emergency from a colleague open straight away.
 */
@Composable
fun AppLockGate(bypass: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.fragmentActivity() }
    var enabled by remember { mutableStateOf(AppLockPrefs.isEnabled(context)) }
    var available by remember { mutableStateOf(AppLock.available(context)) }
    var hasData by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { hasData = withContext(Dispatchers.IO) { runCatching { context.container.profileRepository.getProfile() != null }.getOrDefault(true) } }
    LaunchedEffect(bypass) { if (bypass) LockState.unlocked = true }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { LockState.stoppedAt = SystemClock.elapsedRealtime(); resumed = false }
                Lifecycle.Event.ON_START -> {
                    enabled = AppLockPrefs.isEnabled(context)
                    available = AppLock.available(context)
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
    // before the answer about registration is known nothing is shown, so the dashboard never flashes
    val covered = hasData == null || mustAsk

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
    LaunchedEffect(mustAsk, resumed) { if (mustAsk && resumed) ask() }

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
                if (mustAsk) {
                    Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(painterResource(R.drawable.yegan_logo), null, modifier = Modifier.size(120.dp))
                        Text("محیط‌یار", style = MaterialTheme.typography.headlineSmall, color = MColors.Ink, modifier = Modifier.padding(top = 16.dp))
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
