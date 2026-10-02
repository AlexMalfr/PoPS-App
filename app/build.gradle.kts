import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Full Git history makes the code monotonic for builds distributed from main.
fun gitVersionInput(vararg arguments: String): ByteArray {
    val process = ProcessBuilder(listOf("git") + arguments)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.readBytes()
    check(process.waitFor() == 0) { "App version requires Git and a checkout with full history." }
    return output
}

check(gitVersionInput("rev-parse", "--is-shallow-repository").toString(Charsets.UTF_8).trim() == "false") {
    "App version requires full Git history. Run git fetch --unshallow (CI: fetch-depth: 0)."
}
val gitVersionCode = gitVersionInput("rev-list", "--count", "HEAD").toString(Charsets.UTF_8).trim().toInt()
check(gitVersionCode in 1..2_100_000_000)
val gitRevision = gitVersionInput("rev-parse", "--short=8", "HEAD").toString(Charsets.UTF_8).trim()
val versionedSources = arrayOf("app/src", "app/build.gradle.kts", "build.gradle.kts", "gradle", "gradle.properties", "settings.gradle.kts")
val sourceDiff = gitVersionInput("diff", "--binary", "HEAD", "--", *versionedSources)
val untrackedSources = gitVersionInput("ls-files", "--others", "--exclude-standard", "-z", "--", *versionedSources)
    .toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }.sorted()
val localChangesSuffix = if (sourceDiff.isNotEmpty() || untrackedSources.isNotEmpty()) {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(sourceDiff)
    untrackedSources.forEach { path ->
        digest.update(path.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(rootProject.file(path).readBytes())
        digest.update(0.toByte())
    }
    "-dirty." + digest.digest().take(4).joinToString("") { "%02x".format(it.toInt() and 0xff) }
} else ""
val gitVersionName = "0.$gitVersionCode+$gitRevision$localChangesSuffix"

tasks.register("printAppVersion") {
    doLast { println("PoPS version: $gitVersionName (code $gitVersionCode; debug suffix: -debug)") }
}

val localAppData = System.getenv("LOCALAPPDATA")
if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true) && !localAppData.isNullOrBlank()) {
    layout.buildDirectory.set(file("$localAppData/PoPS-App-Build/app"))
}

android {
    namespace = "com.malfreyt.alexandre.pops_app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.malfreyt.alexandre.pops_app"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode
        versionName = gitVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
