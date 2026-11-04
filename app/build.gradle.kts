import java.net.URI
import java.security.MessageDigest

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
            // Drops unused code and resources; MediaPipe's keep rules are in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
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
// SHA-256 of the model as first downloaded (4 November 2026). Google doesn't publish one, so this pins
// that copy: a changed or corrupted file stops the build instead of going into the APK.
val poseModelSha256 = "5134a3aad27a58b93da0088d431f366da362b44e3ccfbe3462b3827a839011b1"

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadPoseModel = tasks.register("downloadPoseModel") {
    val model = layout.projectDirectory.file("src/main/assets/pose_landmarker_full.task").asFile
    outputs.file(model)
    doLast {
        if (!model.exists()) {
            model.parentFile.mkdirs()
            val partial = File(model.parentFile, "${model.name}.part")
            URI(poseModelUrl).toURL().openStream().use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            partial.renameTo(model)
        }
        val actual = sha256(model)
        if (actual != poseModelSha256) {
            model.delete()
            throw GradleException(
                "The pose model's checksum doesn't match (got $actual). It has been deleted; " +
                    "build again to re-download, and update poseModelSha256 only if Google has published a new model.",
            )
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
