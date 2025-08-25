plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
}

android {
    namespace = "com.projectecho.core.common"
}

dependencies {
    // Compose BOM for Material3 APIs
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    
    // Wear Compose for Material APIs
    implementation(libs.wear.compose.material)

    // Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.bundles.testing)
}