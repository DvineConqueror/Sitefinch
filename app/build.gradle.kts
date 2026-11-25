import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val androidNdkVersion = "28.2.13676358"
val androidMinimumApi = 29
val roomVersion = "2.8.4"
val python = providers.environmentVariable("WORK_PHOTO_LOG_PYTHON").orElse("python")
val nativeBuilder = rootProject.file("scripts/build_native.py")
val cargoSources = rootProject.fileTree("crates/proof-core") {
    include("**/*.rs", "**/*.toml")
}
val cargoManifests = rootProject.files("Cargo.toml", "Cargo.lock", "rust-toolchain.toml")
val bindingsOutput = layout.buildDirectory.dir("generated/rust/kotlin")

val generateRustBindings by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    inputs.files(cargoSources, cargoManifests, nativeBuilder)
    outputs.dir(bindingsOutput)
    commandLine(python.get(), nativeBuilder, "bindings", bindingsOutput.get().asFile)
}

fun registerNativeBuild(taskName: String, variant: String, vararg abis: String) =
    tasks.register<Exec>(taskName) {
        val output = layout.buildDirectory.dir("generated/rust/" + variant + "/jniLibs")
        workingDir(rootProject.projectDir)
        inputs.files(cargoSources, cargoManifests, nativeBuilder)
        inputs.property("ndkVersion", androidNdkVersion)
        inputs.property("minimumApi", androidMinimumApi)
        inputs.property("abis", abis.toList())
        outputs.dir(output)
        doFirst {
            environment("ANDROID_HOME", androidComponents.sdkComponents.sdkDirectory.get().asFile)
        }
        commandLine(
            listOf(
                python.get(), nativeBuilder, "android", output.get().asFile,
                androidNdkVersion, androidMinimumApi.toString(),
            ) + abis,
        )
    }

val buildDebugRust = registerNativeBuild("buildDebugRust", "debug", "arm64-v8a", "x86_64")
val buildReleaseRust = registerNativeBuild("buildReleaseRust", "release", "arm64-v8a")

android {
    namespace = "app.workphotolog.android"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    ndkVersion = androidNdkVersion

    defaultConfig {
        applicationId = "app.workphotolog.android"
        minSdk = androidMinimumApi
        targetSdk = 36
        versionCode = 10
        versionName = "0.6.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            ndk.abiFilters += listOf("arm64-v8a", "x86_64")
        }
        release {
            ndk.abiFilters += "arm64-v8a"
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        jniLibs.useLegacyPackaging = false
        resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    sourceSets.getByName("debug").jniLibs.srcDir(layout.buildDirectory.dir("generated/rust/debug/jniLibs"))
    sourceSets.getByName("release").jniLibs.srcDir(layout.buildDirectory.dir("generated/rust/release/jniLibs"))
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    sourceSets.getByName("main").kotlin.srcDir(bindingsOutput)
}

tasks.configureEach {
    when (name) {
        "preBuild" -> dependsOn(generateRustBindings)
        "preDebugBuild" -> dependsOn(buildDebugRust)
        "preReleaseBuild" -> dependsOn(buildReleaseRust)
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.absolutePath)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    val cameraVersion = "1.5.0"

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.camera:camera-core:" + cameraVersion)
    implementation("androidx.camera:camera-camera2:" + cameraVersion)
    implementation("androidx.camera:camera-lifecycle:" + cameraVersion)
    implementation("androidx.camera:camera-view:" + cameraVersion)
    implementation("androidx.camera:camera-video:" + cameraVersion)
    implementation("androidx.camera:camera-effects:" + cameraVersion)
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.room:room-runtime:" + roomVersion)
    implementation("androidx.room:room-ktx:" + roomVersion)
    kapt("androidx.room:room-compiler:" + roomVersion)
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
