// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kapt) apply false
    alias(libs.plugins.room) apply false
}

// Apply common build configuration to all sub-projects
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.wear.compose.material.ExperimentalWearMaterialApi",
                "-opt-in=kotlin.experimental.ExperimentalTypeInference"
            )
        }
    }

    // Configure KAPT to suppress experimental API warnings
    tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.wear.compose.material.ExperimentalWearMaterialApi"
            )
        }
    }

    // Configure Android modules
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<com.android.build.gradle.AppExtension> {
            configureAndroid()
        }
    }
    
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            configureAndroid()
        }
    }
}

fun com.android.build.gradle.BaseExtension.configureAndroid() {
    compileSdkVersion(34)
    
    defaultConfig {
        minSdk = 26
        targetSdk = 34
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
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

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory.get())
}