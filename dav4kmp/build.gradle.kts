/*
 *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
}

group = "io.github.triangleofice"
version = "0.0.2"

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
    }
    jvmToolchain(17)

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
    namespace = "io.github.triangleofice"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

mavenPublishing {
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
        developers {
            developer {
                id = "troice"
                name = "TrinagleOfIce"
                url = "https://github.com/TriangleOfIce"
                email = "justhear.master@gmail.com"
            }
        }
        scm {
            url = "https://github.com/TriangleOfIce/dav4kmp"
            connection = "scm:git:git:https://github.com/TriangleOfIce/dav4kmp.git"
            developerConnection = "scm:git:ssh://git@github.com/TriangleOfIce/dav4kmp.git"
        }
    }

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()
}
