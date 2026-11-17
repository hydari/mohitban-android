import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ir.mohitban.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.mohitban.app"
        minSdk = 21
        targetSdk = 35
        // every GitHub build gets a higher version so it installs as an update. The base (1000) keeps the numbers
        // above those of the older builds (which used the bare run number) and leaves room should the run counter
        // ever start again (new repository or workflow): then raise versionBase above the last published code.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        val versionBase = 1000
        versionCode = versionBase + run
        versionName = "1.1.$run"
        vectorDrawables { useSupportLibrary = true }
        // only Persian and English UI strings from libraries (smaller APK, nothing visible changes)
        resourceConfigurations += listOf("fa", "en")
        // native code (the offline species recogniser, TensorFlow Lite) only for real phones: keeps the APK small
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        // key of the encrypted in-app update (see update/AppUpdate.kt); made by the workflow from the repository
        // secret, never stored in the code. Empty in local builds: the update check is then switched off.
        val updateKey = System.getenv("MOHITBAN_UPDATE_KEY").orEmpty().filter { it.isLetterOrDigit() }
        buildConfigField("String", "UPDATE_KEY", "\"$updateKey\"")
    }

    // A fixed release key so every new APK installs as an update over the previous one.
    // The key is stored encrypted (signing/mohitban.jks.enc); GitHub Actions decrypts it with the
    // repository secret MOHITBAN_KEY_PASSWORD. Builds without the password use the debug key.
    val releaseKey = rootProject.file("signing/mohitban.jks")
    val ksPassword = System.getenv("MOHITBAN_KEY_PASSWORD")
    val hasReleaseKey = releaseKey.exists() && !ksPassword.isNullOrBlank()
    signingConfigs {
        if (hasReleaseKey) {
            create("mohitban") {
                storeFile = releaseKey
                storePassword = ksPassword
                keyAlias = "mohitban"
                keyPassword = ksPassword
            }
        }
    }

    buildTypes {
        // debug builds keep the debug key on purpose: a debuggable APK signed with the release key could be
        // installed over the real app and its data read out with "run-as"
        release {
            // R8 removes unused library code/resources (mostly the large icon set); the app's own
            // classes are kept as-is in proguard-rules.pro so behaviour does not change
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("mohitban") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // offline species recognition from a photo (ai/SpeciesClassifier.kt)
    implementation(libs.tensorflow.lite)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

/*
 * Vazirmatn (SIL Open Font License) is downloaded once into assets/fonts.
 * If the download is not possible (no internet), the build still succeeds and
 * the app falls back to the system font. You can also copy the TTF files there by hand.
 */
val downloadFonts by tasks.registering {
    val dir = file("src/main/assets/fonts")
    outputs.dir(dir)
    doLast {
        dir.mkdirs()
        val weights = listOf("Regular", "Medium", "Bold", "Black")
        for (w in weights) {
            val target = File(dir, "Vazirmatn-$w.ttf")
            if (target.exists() && target.length() > 10_000) continue
            val urls = listOf(
                "https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@v33.003/fonts/ttf/Vazirmatn-$w.ttf",
                "https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/ttf/Vazirmatn-$w.ttf",
            )
            var ok = false
            for (u in urls) {
                try {
                    val conn = URI(u).toURL().openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    if (conn.responseCode == 200) {
                        conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                        if (target.length() > 10_000) { ok = true; break }
                    }
                } catch (e: Exception) {
                    logger.warn("Font download failed from $u: ${e.message}")
                }
            }
            if (!ok) {
                target.delete()
                logger.warn("Vazirmatn-$w.ttf not available; the app will use the system font.")
            }
        }
    }
}
/*
 * The offline bird recogniser: Google's AIY "birds_V1" model (MobileNet, 965 species, Apache 2.0), about 3.5 MB.
 * It is downloaded once at build time into assets/ai (not kept in the repository). If no address answers, the
 * build still succeeds and the app says the recogniser is not in this version. To add it by hand, put the file
 * as app/src/main/assets/ai/birds_v1.tflite, or upload it once to a release "ai-models" of this repository.
 */
fun isTflite(b: ByteArray) = b.size > 8 && String(b, 4, 4, Charsets.US_ASCII) == "TFL3"

/** The .tflite inside a plain, gzip or tar.gz download (Kaggle hands out a tar.gz). */
fun extractTflite(raw: ByteArray): ByteArray? {
    var data = raw
    if (data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
        data = GZIPInputStream(data.inputStream()).use { it.readBytes() }
    }
    if (isTflite(data)) return data
    var off = 0
    while (off + 512 <= data.size) {
        val name = String(data, off, 100, Charsets.US_ASCII).trimEnd('\u0000')
        if (name.isEmpty()) break
        val size = String(data, off + 124, 12, Charsets.US_ASCII).trim('\u0000', ' ').toLongOrNull(8) ?: 0L
        val start = off + 512
        if (name.endsWith(".tflite") && start + size <= data.size) {
            val f = data.copyOfRange(start, start + size.toInt())
            if (isTflite(f)) return f
        }
        off = start + (((size + 511) / 512) * 512).toInt()
    }
    return null
}

val downloadAiModel by tasks.registering {
    val target = file("src/main/assets/ai/birds_v1.tflite")
    outputs.file(target)
    doLast {
        if (target.exists() && isTflite(target.readBytes())) return@doLast
        target.parentFile.mkdirs()
        val urls = listOf(
            "https://github.com/hydari/mohitban-android/releases/download/ai-models/birds_v1.tflite",
            "https://www.kaggle.com/api/v1/models/google/aiy/tfLite/vision-classifier-birds-v1/3/download",
            "https://tfhub.dev/google/lite-model/aiy/vision/classifier/birds_V1/3?lite-format=tflite",
        )
        for (u in urls) {
            try {
                var url = URI(u).toURL()
                var bytes: ByteArray? = null
                // follow redirects by hand (also across hosts)
                for (hop in 0 until 6) {
                    val conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 60_000
                    conn.setRequestProperty("User-Agent", "Mohityar-build")
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val next = conn.getHeaderField("Location") ?: break
                        url = URI(url.toString()).resolve(next).toURL()
                        conn.disconnect()
                        continue
                    }
                    if (code == 200) bytes = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    break
                }
                val model = bytes?.let { extractTflite(it) }
                if (model != null) {
                    target.writeBytes(model)
                    logger.lifecycle("AI model: ${model.size} bytes from $u")
                    return@doLast
                }
                logger.warn("AI model: no model at $u")
            } catch (e: Exception) {
                logger.warn("AI model download failed from $u: ${e.message}")
            }
        }
        logger.warn("AI model not available; the app is built without the species recogniser.")
    }
}

tasks.named("preBuild") { dependsOn(downloadFonts, downloadAiModel) }
