plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/* Where the in-app updater looks for new versions. GitHub fills in GITHUB_REPOSITORY when it builds,
   so the repository is never written into the code. */
val updateRepo: String = System.getenv("GITHUB_REPOSITORY") ?: (findProperty("updateRepo") as String?) ?: "ryangjpriorx/LoonieMate"
val ciVersionCode: Int = (System.getenv("VERSION_CODE") ?: "1").toInt()
val ciVersionName: String = System.getenv("VERSION_NAME") ?: "0.1.0"
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE")

android {
    namespace = "looniemate.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ryangjpriorx.looniemate"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystorePath != null) create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            /* releases must always be signed with the same key, or phones refuse the update */
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
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

    /* the screenshot test draws every screen on a simulated phone (Robolectric) */
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("fixtures", rootProject.file("parity/fixtures.json").absolutePath)
                it.systemProperty("screens", layout.buildDirectory.dir("screens").get().asFile.absolutePath)
                it.maxHeapSize = "3g"
                it.testLogging { events("passed", "failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
            }
        }
    }

    /* no Google Play dependency metadata in the file; it's installed straight from GitHub */
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
