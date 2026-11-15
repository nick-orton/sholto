import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The release signing key, from `keystore.properties` in the project root
 * (never committed) or the SHOLTO_* environment variables CI sets from
 * secrets. Without either, release builds are left unsigned. See RELEASING.md.
 */
val releaseSigning: Map<String, String>? =
    run {
        val file = rootProject.file("keystore.properties")
        val props = Properties().apply { if (file.exists()) file.inputStream().use(::load) }

        fun value(
            key: String,
            env: String,
        ): String? = props.getProperty(key) ?: System.getenv(env)
        val store = value("storeFile", "SHOLTO_KEYSTORE_FILE") ?: return@run null
        mapOf(
            "storeFile" to store,
            "storePassword" to (value("storePassword", "SHOLTO_KEYSTORE_PASSWORD") ?: return@run null),
            "keyAlias" to (value("keyAlias", "SHOLTO_KEY_ALIAS") ?: return@run null),
            "keyPassword" to (value("keyPassword", "SHOLTO_KEY_PASSWORD") ?: return@run null),
        )
    }

android {
    namespace = "com.nickorton.sholto"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nickorton.sholto"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        releaseSigning?.let { s ->
            create("release") {
                storeFile = rootProject.file(s.getValue("storeFile"))
                storePassword = s.getValue("storePassword")
                keyAlias = s.getValue("keyAlias")
                keyPassword = s.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
