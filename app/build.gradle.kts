plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "uk.ac.wlv.blogexpress"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "uk.ac.wlv.blogexpress"
        minSdk = 21
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
}

dependencies {
    // AndroidX + Material versions that still support minSdk 21
    // (newer AndroidX releases require minSdk 23, so versions are pinned)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.activity:activity:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // ExifInterface - reads photo rotation on all Android versions
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // OkHttp - HTTPS client used to upload posts to WordPress.com
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}