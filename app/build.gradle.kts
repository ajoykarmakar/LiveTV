plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    // removed alias(libs.plugins.kotlin.android) which causes conflict because android block implicitly adds it, or another plugin did.
}

android {
    namespace = "com.clean.livetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clean.livetv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Aggressive size reduction: Keep only English resources
        // Drops hundreds of KB of translations built into AndroidX & Compose
        androidResources {
            localeFilters.add("en")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
        buildConfig = false
        resValues = false
        shaders = false
    }
    packaging {
        resources {
            // Strip out tiny useless metadata files added by kotlin and other libraries
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("/META-INF/DEPENDENCIES")
            excludes.add("/META-INF/LICENSE")
            excludes.add("/META-INF/LICENSE.txt")
            excludes.add("/META-INF/license.txt")
            excludes.add("/META-INF/NOTICE")
            excludes.add("/META-INF/NOTICE.txt")
            excludes.add("/META-INF/notice.txt")
            excludes.add("/*.kotlin_module")
            excludes.add("/META-INF/*.kotlin_module")
            excludes.add("**/kotlin/**")
            excludes.add("**/*.version")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.ui.tooling)
    
    // Compose TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    // Network & Serialization
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    
    // Coil & SVG
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
}