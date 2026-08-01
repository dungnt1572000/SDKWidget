import java.io.ByteArrayOutputStream
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

// Single source of truth for the SDK version. Bump this, commit, then run:
//   ./gradlew :sdk-widget:releaseToJitpack
val sdkVersion = "1.0.0"

android {
    namespace = "com.dungz.widgetsdk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
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
    buildFeatures {
        compose = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

// JitPack overrides group/version from the git tag; these values are for local publishing.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.dungnt1572000"
            artifactId = "sdk-widget"
            version = sdkVersion
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

/**
 * Releases the SDK to JitPack: verifies the build, then tags the current commit with
 * [sdkVersion] and pushes branch + tag. JitPack builds the tag on first request.
 *
 *   ./gradlew :sdk-widget:releaseToJitpack            # tag = sdkVersion
 *   ./gradlew :sdk-widget:releaseToJitpack -Pver=X.Y.Z # override the tag
 */
abstract class ReleaseToJitpackTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @get:Input
    abstract val releaseVersion: Property<String>

    @TaskAction
    fun release() {
        val v = releaseVersion.get()
        val status = ByteArrayOutputStream()
        execOps.exec {
            commandLine("git", "status", "--porcelain")
            standardOutput = status
        }
        check(status.toString().isBlank()) {
            "Working tree has uncommitted changes — commit them before releasing."
        }
        execOps.exec { commandLine("git", "tag", v) }
        execOps.exec { commandLine("git", "push", "origin", "HEAD", v) }
        println("Pushed tag $v.")
        println("Dependency: com.github.dungnt1572000:SDKWidget:$v")
        println("Build status: https://jitpack.io/#dungnt1572000/SDKWidget/$v")
    }
}

tasks.register<ReleaseToJitpackTask>("releaseToJitpack") {
    group = "publishing"
    description = "Verify build, tag with sdkVersion and push so JitPack publishes it."
    releaseVersion.set(providers.gradleProperty("ver").orElse(sdkVersion))
    dependsOn("publishToMavenLocal")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // api: host apps that inject custom Compose widget UI need Glance types on their classpath.
    api(libs.androidx.glance.appwidget)
}
