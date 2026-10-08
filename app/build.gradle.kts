import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val debugApiUrl = providers.gradleProperty("collabpro.debugBaseUrl")
    .getOrElse("http://10.0.2.2:8081/api/v1/")
val releaseApiUrl = providers.gradleProperty("collabpro.releaseBaseUrl")
    .getOrElse("https://unconfigured.invalid/api/v1/")
fun apiUrlLiteral(url: String, httpsRequired: Boolean): String {
    val uri = URI(url)
    require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
    require(uri.path == "/api/v1/") { "API URL must end in /api/v1/" }
    require(uri.scheme == "https" || (!httpsRequired && uri.scheme == "http"))
    return "\"$url\""
}

android {
    namespace = "com.example.collabpro"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.collabpro"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", apiUrlLiteral(debugApiUrl, false))
        }
        release {
            buildConfigField("String", "API_BASE_URL", apiUrlLiteral(releaseApiUrl, true))
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.gson)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    coreLibraryDesugaring(libs.desugar)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Switching the explicit integration environment must rerun tests rather than reuse a skipped result.
    inputs.property("collabproAuthTestApi", providers.environmentVariable("COLLABPRO_AUTH_TEST_API").getOrElse(""))
    inputs.property("collabproAuthTestMailpit", providers.environmentVariable("COLLABPRO_AUTH_TEST_MAILPIT").getOrElse(""))
    inputs.property("collabproIdentityTestApi", providers.environmentVariable("COLLABPRO_IDENTITY_TEST_API").getOrElse(""))
    inputs.property("collabproCampaignTestApi", providers.environmentVariable("COLLABPRO_CAMPAIGN_TEST_API").getOrElse(""))
}
