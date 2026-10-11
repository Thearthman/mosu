import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { input -> load(input) }
    }
}

fun configProperty(vararg names: String): String? {
    return names.firstNotNullOfOrNull { name ->
        providers.gradleProperty(name).orNull
            ?: providers.environmentVariable(name).orNull
            ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
    }
}

fun signingProperty(vararg names: String): String {
    return configProperty(*names) ?: error(
        "Signing property missing. Set one of ${names.joinToString()} in local.properties, Gradle properties, or the environment"
    )
}

// Resolve relative paths against the root project so the build keeps working
// regardless of where the repository is checked out.
fun projectFile(path: String): File {
    val candidate = File(path)
    return if (candidate.isAbsolute) candidate else rootProject.file(path)
}

// Locations searched, in order, when no store file is configured explicitly.
// Covers the keystore living next to the repo, inside the repo, or in the
// user's ~/personal/keystores directory, so moving the project never breaks
// signing as long as one of these exists.
val defaultMosuStoreFileCandidates = listOf(
    rootProject.file("../keystores/mosu.jks"),
    rootProject.file("keystores/mosu.jks"),
    projectFile(System.getProperty("user.home") + "/personal/keystores/mosu.jks"),
)

val configuredMosuStoreFile = configProperty(
    "MOSU_RELEASE_STORE_FILE",
    "MOSU_STORE_FILE",
    "storeFile",
)

val configuredStore = configuredMosuStoreFile?.let(::projectFile)
val mosuStoreFile: File? = configuredStore ?: defaultMosuStoreFileCandidates.firstOrNull { it.isFile }

if (configuredStore != null && !configuredStore.isFile) {
    error("Configured Mosu signing keystore not found at ${configuredStore.absolutePath}")
}

if (mosuStoreFile == null) {
    logger.warn(
        "Mosu signing keystore not found. Debug builds will use the default debug keystore and " +
            "release builds will be left unsigned. Set MOSU_RELEASE_STORE_FILE (in local.properties, " +
            "Gradle properties, or the environment) to sign, or place mosu.jks at one of: " +
            defaultMosuStoreFileCandidates.joinToString { it.absolutePath }
    )
}

android {
    namespace = "com.mosu.app"
    compileSdk = 34
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.mosu.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        val store = mosuStoreFile
        if (store != null) {
            create("mosu") {
                storeFile = store
                storePassword = signingProperty(
                    "MOSU_RELEASE_STORE_PASSWORD",
                    "MOSU_STORE_PASSWORD",
                    "KEYSTORE_PASSWORD",
                    "STORE_PASSWORD",
                    "storePassword"
                )
                keyAlias = signingProperty(
                    "MOSU_RELEASE_KEY_ALIAS",
                    "MOSU_KEY_ALIAS",
                    "KEY_ALIAS",
                    "keyAlias"
                )
                keyPassword = signingProperty(
                    "MOSU_RELEASE_KEY_PASSWORD",
                    "MOSU_KEY_PASSWORD",
                    "KEY_PASSWORD",
                    "keyPassword"
                )
            }
        }
    }

    buildTypes {
        debug {
            // Falls back to the default debug keystore when no Mosu keystore is configured.
            if (mosuStoreFile != null) {
                signingConfig = signingConfigs.getByName("mosu")
            }
        }
        release {
            isMinifyEnabled = false
            // Left unsigned when no Mosu keystore is configured.
            if (mosuStoreFile != null) {
                signingConfig = signingConfigs.getByName("mosu")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    
    // Compose
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.ui:ui-graphics:1.5.0")
    // Ships libandroidx.graphics.path.so (also pulled in transitively by Compose); pinned explicitly.
    implementation("androidx.graphics:graphics-path:1.0.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
    implementation("androidx.compose.material:material:1.5.0")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Coil for Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // DataStore for Token Persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // AppCompat for locale support (AppCompatDelegate.setApplicationLocales)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // WorkManager for scheduled background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Media3 (ExoPlayer)
    val media3Version = "1.2.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.0")
}
