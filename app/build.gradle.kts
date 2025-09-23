import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read properties from gradle.properties
val properties = Properties()
properties.load(FileInputStream(rootProject.file("gradle.properties")))

android {
    namespace = "com.templatefinder"
    compileSdk = 34

    signingConfigs {
        create("release") {
            keyAlias = properties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD")
            storeFile = file(properties.getProperty("RELEASE_STORE_FILE"))
            storePassword = properties.getProperty("RELEASE_STORE_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.easyclicker"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    
    // Image processing
    implementation("org.opencv:opencv:4.12.0")
    
    // For foreground service
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Battery optimization
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    
    // User onboarding
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // Image loading and processing
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    
    // Analytics and crash reporting (temporarily disabled)
    // implementation("ch.acra:acra-core:5.11.3")
    // implementation("ch.acra:acra-dialog:5.11.3")
    // implementation("ch.acra:acra-notification:5.11.3")
    
    // Accessibility improvements (already included in core-ktx)
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.1.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}