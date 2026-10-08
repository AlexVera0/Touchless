import java.io.InputStream
import java.io.OutputStream
import java.net.URI

plugins { id("com.android.application") }

android {
    namespace = "com.touchless.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.touchless.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "0.8.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core:1.3.2")
    val cameraVersion = "1.6.2"
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.32")
    testImplementation("junit:junit:4.13.2")
}

val handModel = layout.projectDirectory.file("src/main/assets/hand_landmarker.task")
val downloadHandModel = tasks.register("downloadHandModel") {
    outputs.file(handModel)
    doLast {
        val file = handModel.asFile
        if (!file.exists()) {
            file.parentFile.mkdirs()
            val url = URI("https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task").toURL()
            url.openStream().use { input: InputStream ->
                file.outputStream().use { output: OutputStream ->
                    input.copyTo(output)
                }
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadHandModel) }
