import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp)
    id("jacoco")
}

// Apply Google Services plugin only if google-services.json exists to allow CI builds without secrets
val hasGoogleServicesJson = file("google-services.json").exists()
if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle("Google Services plugin applied (google-services.json found).")
} else {
    logger.lifecycle("Google Services plugin NOT applied (google-services.json missing).")
}

// Resolve authorized emails from env, file, or local.properties and expose as BuildConfig field
val authorizedEmailsList: List<String> = run {
    val env = System.getenv("AUTHORIZED_EMAILS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    val file = rootProject.file("authorized_emails.txt")
    val fromFile = if (file.exists()) {
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    } else emptyList()

    val combined = when {
        env.isNotEmpty() -> env
        fromFile.isNotEmpty() -> fromFile
        else -> listOf("dummy1@example.com", "dummy2@example.com")
    }
    combined.distinct()
}

fun List<String>.toJavaStringArrayLiteral(): String {
    // Produce a Java array initializer literal: {"a","b"}
    val escaped = this.map { it.replace("\\", "\\\\").replace("\"", "\\\"") }
    return escaped.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ","
    ) { "\"$it\"" }
}

// Load API keys and secrets from environment or config.properties (excluded from VCS)
val props = Properties()
val configFile = rootProject.file("config.properties")
if (configFile.exists()) {
    props.load(configFile.inputStream())
}
val youtubeApiKey: String = System.getenv("YOUTUBE_API_KEY")
    ?: props.getProperty("youtubeApiKey")
    ?: ""

android {
    namespace = "dev.elainedb.android_junie"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.elainedb.android_junie"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose authorized emails into BuildConfig
        buildConfigField(
            "String[]",
            "AUTHORIZED_EMAILS",
            authorizedEmailsList.toJavaStringArrayLiteral()
        )
        // Expose YouTube API key into BuildConfig
        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"$youtubeApiKey\""
        )
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Room (local caching)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Image loading for Compose
    implementation(libs.coil.compose)

    // Google Sign-In
    implementation(libs.google.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val exclusions = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*"
)

tasks.withType(Test::class) {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

android {
    applicationVariants.all(closureOf<com.android.build.gradle.internal.api.BaseVariantImpl> {
        val variant = this@closureOf.name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.getDefault()
            ) else it.toString()
        }

        val unitTests = "test${variant}UnitTest"

        tasks.register<JacocoReport>("Jacoco${variant}CodeCoverage") {
            dependsOn(listOf(unitTests))
            group = "Reporting"
            description = "Execute ui and unit tests, generate and combine Jacoco coverage report"
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
            sourceDirectories.setFrom(layout.projectDirectory.dir("src/main"))
            classDirectories.setFrom(files(
                fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/${variant.lowercase()}")) {
                    exclude(exclusions)
                }
            ))
            executionData.setFrom(files(
                fileTree(layout.buildDirectory) {
                    include("jacoco/test${variant}UnitTest.exec")
                    include("outputs/unit_test_code_coverage/${variant.lowercase()}UnitTest/test${variant}UnitTest.exec")
                }
            ))
        }
    })
}