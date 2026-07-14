plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.homelab.kidguard.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 33
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // Адрес KidGuard-server (шаг 4.7): debug — локальный dev-сервер через алиас эмулятора,
        // release — боевой AdminVPS (пока по IP:порт, HTTPS/поддомен — отдельно позже).
        debug {
            // По умолчанию — локальный dev-сервер (10.0.2.2 = алиас эмулятора на localhost хоста).
            // Можно переопределить, чтобы отлаживать (с логами) против БОЕВОГО сервера и реальных
            // данных: ./gradlew assembleDebug -Pkidguard.serverUrl=http://157.22.172.217:3003/
            val debugServerUrl =
                (project.findProperty("kidguard.serverUrl") as String?) ?: "http://10.0.2.2:3003/"
            buildConfigField("String", "BASE_URL", "\"$debugServerUrl\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"http://157.22.172.217:3003/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    compileOnly(libs.error.prone.annotations)
}
