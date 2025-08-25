plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
}

android {
    namespace = "com.projectecho.watch"

    defaultConfig {
        applicationId = "com.projectecho.watch"
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Project modules
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:wearable"))
    implementation(project(":features:audio"))
    implementation(project(":features:permissions"))

    // Wear OS
    implementation(libs.bundles.wear.os)
    compileOnly(libs.wearable.support)

    // Compose for Wear OS
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.wear)
    implementation(libs.wear.compose.navigation)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.android.compiler)

    // Navigation
    implementation(libs.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.bundles.android.testing)
    debugImplementation(libs.bundles.debug)
}