import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

// Supabase credentials are read from android/local.properties (gitignored) into
// BuildConfig at build time. Only the anon key is client-safe; no service-role
// key or other secret is ever committed (see docs/specs/spec-android-app.md).
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun localProperty(key: String): String = localProperties.getProperty(key, "")

android {
    namespace = "de.rack.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.rack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperty("supabase.anonKey")}\"")
        buildConfigField("String", "MCP_BASE_URL", "\"${localProperty("mcp.baseUrl")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ktlint {
    android.set(true)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // ProcessLifecycleOwner: drives the foreground/background signal that binds the
    // per-user Realtime subscription to the app lifecycle (subscribe on foreground,
    // unsubscribe on background, reconnect + re-sync on return).
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Single shared SupabaseClient (Auth + Postgrest + Realtime + Storage).
    // Realtime/Storage are installed but unused until Phases 4/6. The okhttp
    // engine backs the ktor client. Pinned to a known-good supabase-kt BOM
    // aligned with Kotlin 2.0.21 (stdlib 2.0.21) and ktor 3.0.1.
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.0.2")
    implementation(supabaseBom)
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.1")

    // ktor content negotiation + kotlinx JSON for the dedicated MCP admin client
    // (key create/list/revoke over HTTP). Already on the classpath transitively via
    // supabase-kt; declared explicitly so the rack-MCP HttpClient owns its config.
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")

    // Vico (Apache-2.0): the Compose-native, Material 3 charting library backing the
    // Phase 11 dashboards (weekly volume, per-exercise progress). The compose-m3 module
    // brings the core compose API transitively; a Recomp-themed VicoTheme wrapper styles
    // it in the dark theme with the volt/tag palette (see ui/theme/RecompChartTheme.kt).
    // Pinned to 2.2.0 — the last release built against compileSdk 35; Vico 3.x / 2.3.0+
    // require compileSdk 36, which AGP 8.7.x does not support.
    implementation("com.patrykandpatrick.vico:compose-m3:2.2.0")

    // Room: the light local cache holding only UNSYNCED set logs (a pending-write
    // queue), flushed and deleted on reconnect. Reads always come from Supabase.
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // JVM unit tests for the pure timer engine math and the StateFlow timer ViewModel.
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
