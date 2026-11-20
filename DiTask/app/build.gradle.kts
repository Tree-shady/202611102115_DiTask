import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// ---------------------------------------------------------------------------
// 签名配置全部从本地注入，仓库内不保存任何真实口令。
// 读取优先级：Gradle 属性（-P 参数 / ~/.gradle/gradle.properties）
//             → 环境变量 ORG_GRADLE_PROJECT_<属性名>
// 键名与说明见 DiTask/keystore.properties.example（仅占位模板）。
// ---------------------------------------------------------------------------
val signingStoreFile = providers.gradleProperty("DITASK_STORE_FILE").orNull?.takeIf { it.isNotBlank() }
val signingStorePassword = providers.gradleProperty("DITASK_STORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val signingKeyAlias = providers.gradleProperty("DITASK_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
val signingKeyPassword = providers.gradleProperty("DITASK_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }

val signingValues = listOf(
    "DITASK_STORE_FILE" to signingStoreFile,
    "DITASK_STORE_PASSWORD" to signingStorePassword,
    "DITASK_KEY_ALIAS" to signingKeyAlias,
    "DITASK_KEY_PASSWORD" to signingKeyPassword
)
val signingReady = signingValues.all { it.second != null }
val missingSigningKeys = signingValues.filter { it.second == null }.map { it.first }

when {
    // 部分注入视为配置错误：立即失败，避免带着错误签名参数继续构建
    !signingReady && missingSigningKeys.size < signingValues.size -> throw GradleException(
        "签名配置不完整，缺少：${missingSigningKeys.joinToString("、")}。" +
            "请在 ~/.gradle/gradle.properties 中补齐，或通过 -P / ORG_GRADLE_PROJECT_ 环境变量注入；" +
            "键名参考 DiTask/keystore.properties.example。"
    )
    // 完全未注入：安全降级为未签名包，并给出明确警告
    !signingReady -> logger.warn(
        "[DiTask] 未检测到 release 签名配置，release 将输出未签名包（不会使用任何默认密钥）。" +
            "配置方式见 DiTask/keystore.properties.example。"
    )
}

// ---------------------------------------------------------------------------
// 服务端地址：仓库只保留不含真实内网信息的默认值（Android 模拟器访问宿主机）。
// 本地配置方式（优先级从高到低）：
//   1. DiTask/local.properties（已被 .gitignore 忽略，不会提交）
//        DITASK_SERVER_URL=http://your-server-host:3000
//   2. Gradle 属性 ~/.gradle/gradle.properties 或 -P 参数
//   3. 环境变量 ORG_GRADLE_PROJECT_DITASK_SERVER_URL
// 未配置时使用默认值，保证模拟器场景开箱即可编译运行。
// ---------------------------------------------------------------------------
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.reader(Charsets.UTF_8).use { load(it) }
    }
}
val serverBaseUrl = localProperties.getProperty("DITASK_SERVER_URL")?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("DITASK_SERVER_URL").orNull?.takeIf { it.isNotBlank() }
    ?: "http://10.0.2.2:3000"

android {
    namespace = "com.example.ditask"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.ditask"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 服务端基址由本地配置注入，仓库内只保留默认占位值
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = rootProject.file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                // 三种签名方案全部开启
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // 与已安装正式版共存，避免自动化测试覆盖正式版数据
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}