import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
}

group = "io.github.triangleofice"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                api(libs.ktor.client.core)
                api(libs.ktor.client.auth)
                implementation(libs.xmlutil.core)
                implementation(libs.klock)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.framework.datatest)
                implementation(libs.kotest.assertions.core)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.auth)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotlinx.coroutines.debug)
                implementation(libs.logback.classic)
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

android {
    namespace = "org.jetbrains.kotlinx.multiplatform.library.template"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

//publishing {
//    publications {
//        withType<MavenPublication> {
//            artifactId = "dav4kmp-$version"
//        }
//    }
//}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.DEFAULT)
//    publishToMavenCentral(SonatypeHost.S01)

    signAllPublications()

    coordinates(group.toString(), "dav4kmp", version.toString())

    pom {
        name = "dav4kmp"
        description = "WebDAV (including CalDAV, CardDAV) library for Kotlin Multiplatform"
        inceptionYear = "2025"
        url = "https://github.com/TriangleOfIce/dav4kmp"
        licenses {
            license {
                name = "Mozilla Public License Version 2.0"
                url = "https://www.mozilla.org/en-US/MPL/2.0/"
                distribution = "https://www.mozilla.org/en-US/MPL/2.0/"
            }
        }
//        developers {
//            developer {
//                id = "XXX"
//                name = "YYY"
//                url = "ZZZ"
//            }
//        }
        scm {
            url = "https://github.com/TriangleOfIce/dav4kmp"
            connection = "scm:git:git:https://github.com/TriangleOfIce/dav4kmp.git"
            developerConnection = "scm:git:ssh://git@github.com/TriangleOfIce/dav4kmp.git"
        }
    }
}
