import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/*
 * Signing continuity is intentionally external to Git.
 *
 * Resolution order:
 * 1. -PepicdashSigningProperties=/absolute/path/to/properties
 * 2. EPICDASH_SIGNING_PROPERTIES environment variable
 * 3. signing/epicdash-jz-signing.properties (ignored by Git)
 *
 * Without a continuity-signing file, debug builds use Android's normal debug
 * signing key. Such APKs compile and run, but cannot update an installed
 * continuity-signed EpicDash JZ build in place.
 */
val signingPropertiesPath = providers.gradleProperty("epicdashSigningProperties").orNull
    ?: System.getenv("EPICDASH_SIGNING_PROPERTIES")
    ?: "signing/epicdash-jz-signing.properties"
val jzSigningPropertiesFile = rootProject.file(signingPropertiesPath)
val hasJzSigning = jzSigningPropertiesFile.isFile
val jzSigningProperties = Properties().apply {
    if (hasJzSigning) {
        jzSigningPropertiesFile.inputStream().use(::load)
    }
}
val jzSigningKeystore: File? = if (hasJzSigning) {
    val configured = File(jzSigningProperties.getProperty("storeFile"))
    if (configured.isAbsolute) configured else jzSigningPropertiesFile.parentFile.resolve(configured.path)
} else null

android {
    namespace = "com.buttonbox.ble"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.buttonbox.ble.jz"
        minSdk = 26
        targetSdk = 34
        versionCode = 1114
        versionName = "0.11.12-stale1-jz"
    }

    signingConfigs {
        if (hasJzSigning) {
            create("jz") {
                storeFile = jzSigningKeystore
                storePassword = jzSigningProperties.getProperty("storePassword")
                keyAlias = jzSigningProperties.getProperty("keyAlias")
                keyPassword = jzSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasJzSigning) signingConfig = signingConfigs.getByName("jz")
        }
        release {
            isMinifyEnabled = false
            if (hasJzSigning) signingConfig = signingConfigs.getByName("jz")
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
        viewBinding = true
    }
}

tasks.register("syncWhatsNew") {
    doLast {
        val source = file("../whatsnew/en-US/default.txt")
        val dest = file("src/main/assets/whatsnew.txt")
        if (source.exists()) {
            source.copyTo(dest, overwrite = true)
            println("Synced whatsnew.txt to assets")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("syncWhatsNew")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.play.services.location)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
