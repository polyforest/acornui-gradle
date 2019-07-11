/*
 * Copyright 2019 Poly Forest, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    `kotlin-dsl`
    `maven-publish`
//    id("com.gradle.plugin-publish") version "0.10.1"
}

repositories {
    gradlePluginPortal()
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

val kotlinVersion: String by extra
dependencies {
    implementation(kotlin("gradle-plugin", version = kotlinVersion))
}

gradlePlugin {
    plugins {
        create("kotlinMpp") {
            id = "$group.kotlin-mpp"
            implementationClass = "com.acornui.plugins.KotlinMppPlugin"
            displayName = "Kotlin multi-platform configuration for Acorn UI"
            description = "Configures an Acorn UI library project for Kotlin multi-platform."
        }
        create("kotlinJvm") {
            id = "$group.kotlin-jvm"
            displayName = "Kotlin jvm configuration for Acorn UI"
            implementationClass = "com.acornui.plugins.KotlinJvmPlugin"
            description = "Configures an Acorn UI library project for Kotlin jvm."
        }
        create("kotlinJs") {
            id = "$group.kotlin-js"
            implementationClass = "com.acornui.plugins.KotlinJsPlugin"
            displayName = "Kotlin js configuration for Acorn UI"
            description = "Configures an Acorn UI library project for Kotlin js."
        }
    }
}

val acornUiGradlePluginRepository: String? by extra
if (acornUiGradlePluginRepository != null) {
    publishing {
        repositories {
            maven {
                url = uri(acornUiGradlePluginRepository!!)
            }
        }
    }
}

// In the future if we add release artifacts to the gradle plugin portal.
//pluginBundle {
//    website = "http://www.acornui.com/"
//    vcsUrl = "https://github.com/polyforest/acornui"
//    description = "Kotlin multi-platform configurations."
//    tags = listOf("kotlin", "multi-platform", "configuration")
//
//    (plugins) {
//        "kotlinMpp" {
//            displayName = "Kotlin multi-platform configuration for Acorn UI"
//            description = "Configures an Acorn UI library project for Kotlin multi-platform."
//            tags = listOf("acornui", "kotlin", "configuration", "multi-platform")
//            version = rootProject.version.toString()
//        }
//
//        "kotlinJvm" {
//            displayName = "Kotlin jvm configuration for Acorn UI"
//            description = "Configures an Acorn UI library project for Kotlin jvm."
//            tags = listOf("acornui", "kotlin", "configuration", "jvm")
//            version = rootProject.version.toString()
//        }
//
//        "kotlinJs" {
//            displayName = "Kotlin js configuration for Acorn UI"
//            description = "Configures an Acorn UI library project for Kotlin js."
//            tags = listOf("acornui", "kotlin", "configuration", "js")
//            version = rootProject.version.toString()
//        }
//    }
//}
