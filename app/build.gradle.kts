plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Подпись release: если заданы переменные окружения (их выставляет workflow из секретов),
// используется ваш keystore, иначе release подписывается постоянным общим ключом из app/release.keystore.
val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "me.senkuro.unofficial"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.senkuro.unofficial"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.4-alpha"
    }

    signingConfigs {
        // Постоянный debug-ключ лежит в репозитории (app/debug.keystore): все сборки подписаны одним ключом,
        // поэтому новую сборку можно ставить поверх старой без удаления приложения.
        // Ключ не секретный (пароль android), для публикации он не подходит.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Постоянный общий (публичный) release-ключ лежит в репозитории (app/release.keystore, пароль senkuro-public).
        // Если workflow передал свои секреты подписи, они имеют приоритет.
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                storeFile = file("release.keystore")
                storePassword = "senkuro-public"
                keyAlias = "senkuro-release"
                keyPassword = "senkuro-public"
            }
        }
    }

    buildTypes {
        debug {
            // Debug ставится рядом с release: me.senkuro.unofficial.debug, название «Debug» (src/debug/res/values/strings.xml).
            applicationIdSuffix = ".debug"
            // Отладочные инструменты (перехват запросов) включены.
            buildConfigField("boolean", "DEBUG_TOOLS", "true")
        }
        release {
            isMinifyEnabled = false
            // В release отладочных инструментов нет: код лежит только в src/debug, здесь пустая заглушка.
            buildConfigField("boolean", "DEBUG_TOOLS", "false")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil-gif:3.0.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Только для debug-сборки (перехват запросов в WebView).
    debugImplementation("androidx.webkit:webkit:1.12.1")
}
