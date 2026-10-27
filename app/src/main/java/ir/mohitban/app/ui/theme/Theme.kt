@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package ir.mohitban.app.ui.theme

import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/* ---------- Colours taken from the visual reference ---------- */
object MColors {
    val Green = Color(0xFF1F6B3A)
    val Green2 = Color(0xFF2E8B4E)
    val GreenBright = Color(0xFF2FA05A)
    val GreenDark = Color(0xFF12301E)
    val GreenNav = Color(0xEB0E2417)
    val GreenLight = Color(0xFFE6F2E9)
    val GreenTint = Color(0xFFF2FAF4)
    val Leaf = Color(0xFF8FE0A8)
    val Ink = Color(0xFF1A2620)
    val Muted = Color(0xFF6B756F)
    val Hint = Color(0xFFA3ABA5)
    val Line = Color(0xFFE3E8E4)
    val Bg = Color(0xFFE8EEE9)
    val White = Color(0xFFFFFFFF)
    val PlateRed = Color(0xFFC62828)
    val PlateBlue = Color(0xFF1D4FA3)
    val FlagGreen = Color(0xFF239F40)
    val FlagRed = Color(0xFFDA0000)
    val Error = Color(0xFFC62828)
    val ErrorBg = Color(0xFFFBE9E7)
    val Amber = Color(0xFFC98A1B)
    val AmberBg = Color(0xFFFFF4DE)
    val GlassLight = Color(0xD1ECF4EE)
    val GlassLighter = Color(0xD9E2F0E6)
    val GlassDark = Color(0x80163422)
    val GlassBorder = Color(0xBFFFFFFF)
    val GlassDarkBorder = Color(0x38FFFFFF)
}

/* ---------- Vazirmatn from assets (falls back to the system font) ---------- */
object AppFonts {
    @Volatile private var family: FontFamily? = null
    @Volatile var androidRegular: android.graphics.Typeface = android.graphics.Typeface.DEFAULT
        private set
    @Volatile var androidBold: android.graphics.Typeface = android.graphics.Typeface.DEFAULT_BOLD
        private set

    private val files = mapOf(
        FontWeight.Normal to "fonts/Vazirmatn-Regular.ttf",
        FontWeight.Medium to "fonts/Vazirmatn-Medium.ttf",
        FontWeight.Bold to "fonts/Vazirmatn-Bold.ttf",
        FontWeight.Black to "fonts/Vazirmatn-Black.ttf",
    )

    fun init(context: Context) {
        if (family != null) return
        val available = runCatching { context.assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
        val present = files.filter { (_, path) -> path.removePrefix("fonts/") in available }
        family = if (present.isEmpty()) {
            FontFamily.Default
        } else {
            FontFamily(present.map { (w, path) -> Font(path, context.assets, w) })
        }
        if ("Vazirmatn-Regular.ttf" in available) {
            runCatching {
                androidRegular = android.graphics.Typeface.createFromAsset(context.assets, files.getValue(FontWeight.Normal))
            }
        }
        if ("Vazirmatn-Bold.ttf" in available) {
            runCatching {
                androidBold = android.graphics.Typeface.createFromAsset(context.assets, files.getValue(FontWeight.Bold))
            }
        } else {
            androidBold = android.graphics.Typeface.create(androidRegular, android.graphics.Typeface.BOLD)
        }
    }

    fun family(): FontFamily = family ?: FontFamily.Default
}

private fun typography(f: FontFamily): Typography {
    fun s(size: Int, w: FontWeight, lh: Int = (size * 1.55).toInt()) =
        TextStyle(fontFamily = f, fontSize = size.sp, fontWeight = w, lineHeight = lh.sp, textDirection = TextDirection.ContentOrRtl)
    return Typography(
        displaySmall = s(28, FontWeight.Black),
        headlineMedium = s(24, FontWeight.Bold),
        headlineSmall = s(21, FontWeight.Bold),
        titleLarge = s(18, FontWeight.Bold),
        titleMedium = s(16, FontWeight.Bold),
        titleSmall = s(14, FontWeight.Bold),
        bodyLarge = s(15, FontWeight.Medium),
        bodyMedium = s(14, FontWeight.Normal),
        bodySmall = s(12, FontWeight.Normal),
        labelLarge = s(15, FontWeight.Bold),
        labelMedium = s(12, FontWeight.Medium),
        labelSmall = s(11, FontWeight.Normal),
    )
}

@Composable
fun MohitbanTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    AppFonts.init(context.applicationContext)
    val scheme = lightColorScheme(
        primary = MColors.Green,
        onPrimary = Color.White,
        primaryContainer = MColors.GreenLight,
        onPrimaryContainer = MColors.Green,
        secondary = MColors.Green2,
        background = Color.White,
        onBackground = MColors.Ink,
        surface = Color.White,
        onSurface = MColors.Ink,
        surfaceVariant = MColors.Bg,
        onSurfaceVariant = MColors.Muted,
        outline = MColors.Line,
        error = MColors.Error,
    )
    // The whole application is right-to-left regardless of the phone's language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, typography = typography(AppFonts.family()), shapes = Corners.materialShapes, content = content)
    }
}

/** One modest corner radius for cards, buttons, fields, dialogs and the bottom bar (formal look). */
object Corners {
    val R = 16.dp
    val Shape = androidx.compose.foundation.shape.RoundedCornerShape(R)
    val materialShapes = androidx.compose.material3.Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        medium = Shape,
        large = Shape,
        extraLarge = Shape,
    )
}
