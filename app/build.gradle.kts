import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

// 敏感配置：签名盐值、API 地址等从 secrets.properties 读取，不提交到版本控制。
val secretsFile = rootProject.file("secrets.properties")
val secretsProps = Properties()
if (secretsFile.exists()) {
    secretsProps.load(secretsFile.inputStream())
}
val apiGateway = secretsProps.getProperty("API_GATEWAY", "")
val signSalt = secretsProps.getProperty("SIGN_SALT", "")
val apiCid = secretsProps.getProperty("API_CID", "")

android {
    namespace = "com.water.widget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.water.widget"
        // 固定正式包名，所有本地与 CI 构建都保持一致；不要按 buildType 添加后缀。
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "5.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 敏感配置注入 BuildConfig，源码不包含实际值。
        buildConfigField("String", "API_GATEWAY", "\"${apiGateway}\"")
        buildConfigField("String", "SIGN_SALT", "\"${signSalt}\"")
        buildConfigField("String", "API_CID", "\"${apiCid}\"")

        // 面向现代安卓真机分发；移除 32 位 ARM 与 x86 系列原生库以缩减 bundled ML Kit 扫码库体积。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../src/main/AndroidManifest.xml")
            java.srcDirs("../src/main/java", "../src/main/kotlin")
            res.srcDirs("../src/main/res")
        }
        getByName("test") {
            java.srcDirs("../src/test/kotlin")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    // 与 ImageToolbox 相同的 CameraX + ML Kit 扫码路线；bundled 模型无需依赖 Google Play 服务。
    implementation("com.github.T8RIN.QuickieExtended:quickie-bundled:1.18.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
