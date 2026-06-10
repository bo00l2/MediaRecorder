plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.mediarecorder"
    compileSdk = 36 // targetSdk와 일치하도록 간결하게 표현하거나 기존 형식을 유지하셔도 됩니다.

    defaultConfig {
        applicationId = "com.example.mediarecorder"
        minSdk = 24
        targetSdk = 36
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    // --- 기존 기본 의존성 ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // --- [추가] 프로젝트 기능 요구사항을 위한 라이브러리 ---

    // 1. 오디오 재생 (FR-05: Jetpack Media3 ExoPlayer)
    // Compose 환경에서는 미디어 컨트롤러 결합을 위해 media3-exoplayer와 UI가 필요합니다.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // 2. 상황 정보 수집 (FR-02: 구글 위치 서비스)
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // 3. AI 및 백엔드 연동 (FR-03, FR-04: Retrofit & 가속도 센서 통신용)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 4. Compose에서 위치/마이크 권한을 쉽게 요청하기 위한 Accompanist 라이브러리 (선택 권장)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // --- 기존 테스트 의존성 ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}