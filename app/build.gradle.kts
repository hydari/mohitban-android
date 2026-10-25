import java.net.HttpURLConnection
import java.net.URI

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
        // every GitHub build gets a higher version so it installs as an update
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
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
        debug {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("mohitban")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("mohitban") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
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
tasks.named("preBuild") { dependsOn(downloadFonts) }
