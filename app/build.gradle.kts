import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.atsukio.runningcoach"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.atsukio.runningcoach"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // Also sets the Kotlin JVM target, now that AGP compiles Kotlin itself.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // MediaPipe memory-maps the model, so it must be stored uncompressed.
        noCompress += "task"
    }
}

// The pose model ships inside the APK; it is fetched once at build time, never by the app.
val poseModelUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task"
val downloadPoseModel = tasks.register("downloadPoseModel") {
    val model = layout.projectDirectory.file("src/main/assets/pose_landmarker_full.task").asFile
    outputs.file(model)
    onlyIf { !model.exists() }
    doLast {
        model.parentFile.mkdirs()
        URI(poseModelUrl).toURL().openStream().use { input ->
            model.outputStream().use { input.copyTo(it) }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadPoseModel) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
