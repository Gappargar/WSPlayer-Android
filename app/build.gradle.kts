plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.wsplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.wsplayer"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {

    // Základní AndroidX (některé zde už možná budou)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout) // Knihovna pro ConstraintLayout - DŮLEŽITÉ pro váš layout

    // ViewModel a LiveData (pro moderní správu stavu UI)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Knihovna pro načítání obrázků (např. Coil)
    implementation(libs.coil)

    // Coroutines (pro snadnou práci s asynchronními operacemi, jako jsou síťová volání)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Retrofit pro síťovou komunikaci s API
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars) // Converter pro práci s XML jako String
    // Volitelně: implementation("com.squareup.retrofit2:converter-simplexml:2.9.0") // Pokud byste našel(a) funkční XML Converter

    // OkHttp (používáno Retrofitem, pro nízkoúrovňové síťové operace a interceptory)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor) // Užitečné pro logování síťových požadavků při debugování

    // ExoPlayer pro přehrávání videa
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.ui)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3) // UI komponenty přehrávače (např. StyledPlayerView)

    // Hashing - Zde budete potřebovat knihovny pro MD5-Crypt a SHA1
    // SHA1 je v Java SDK, ale MD5-Crypt musíte najít nebo implementovat.
    // Příklad (tuto závislost musíte najít/implementovat):
    // implementation("com.example.crypt:md5-crypt:1.0.0") // Zástupný symbol

    // Testovací závislosti (ty už tam asi budou)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}