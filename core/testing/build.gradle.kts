plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.projectecho.core.testing"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))

    // Room testing
    implementation(libs.room.testing)
    
    // Testing framework
    implementation(libs.bundles.testing)
    implementation(libs.mockk)
    
    // AndroidX Testing
    implementation(libs.androidx.test.ext.junit)
    
    // Coroutines testing
    implementation(libs.kotlinx.coroutines.test)
    
    // Hilt testing
    implementation(libs.hilt.android.testing)
}