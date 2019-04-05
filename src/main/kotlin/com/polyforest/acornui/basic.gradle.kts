package com.polyforest.acornui

import com.polyforest.acornui.build.*

/**
 * Plugin:  com.polyforest.acornui.basic
 * Used in acornui and consumer projects for basic kotlin multiplatform modules.
 * JS compilation main defaults to "noCall"
 */

repositories {
	jcenter()
}

plugins {
	id("org.jetbrains.kotlin.multiplatform")
}

// Enforce all Acorn UI dependencies use the same version
configurations.configureEach {
	resolutionStrategy.eachDependency {
		if (requested.group == "com.polyforest" && requested.name.startsWith("acornui-")) {
			useVersion(ACORNUI_VERSION)
			because("Adhering to the default target version for Acorn UI")
		}
	}
}

val defaults = mapOf(
	"ACORNUI_VERSION" to "1.0.0-SNAPSHOT",
	"PRODUCT_GROUP" to "",
	"PRODUCT_VERSION" to "1.0.0-SNAPSHOT",
	"KOTLIN_LANGUAGE_VERSION" to "1.3",
	"KOTLIN_JVM_TARGET" to "1.8"
)

fun extraOrDefault(name: String, default: String = defaults.getValue(name)) : String {
	return try {
		@Suppress("UNCHECKED_CAST")
		extra[name] as String
	} catch (e: Exception) {
		default
	}
}

val ACORNUI_VERSION: String = extraOrDefault("ACORNUI_VERSION")
val KOTLIN_LANGUAGE_VERSION: String = extraOrDefault("KOTLIN_LANGUAGE_VERSION")
val KOTLIN_JVM_TARGET: String = extraOrDefault("KOTLIN_JVM_TARGET")
val PRODUCT_VERSION: String = extraOrDefault("PRODUCT_VERSION")
val PRODUCT_GROUP: String = extraOrDefault("PRODUCT_GROUP")

version = PRODUCT_VERSION
group = PRODUCT_GROUP

kotlin {
	js {
		compilations.all {
			kotlinOptions {
				moduleKind = "amd"
				sourceMap = true
				sourceMapEmbedSources = "always"
				main = "noCall"
			}
		}
	}
	jvm {
		compilations.all {
			kotlinOptions {
				jvmTarget = KOTLIN_JVM_TARGET
			}
		}
	}
	targets.all {
		compilations.all {
			kotlinOptions {
				languageVersion = KOTLIN_LANGUAGE_VERSION
				apiVersion = KOTLIN_LANGUAGE_VERSION
				verbose = true
			}
		}
	}

	sourceSets {
		commonMain {
			dependencies {
				implementation(kotlin("stdlib-common"))
			}
		}
		commonTest {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
			}
		}
		named("jvmMain") {
			dependencies {
				implementation(kotlin("stdlib-jdk8"))
			}
		}
		named("jvmTest") {
			dependencies {
				implementation(kotlin("test"))
				implementation(kotlin("test-junit"))
			}
		}
		named("jsMain") {
			dependencies {
				implementation(kotlin("stdlib-js"))
			}
		}
		named("jsTest") {
			dependencies {
				implementation(kotlin("test-js"))
			}
		}
	}
}

afterEvaluate {
	tasks.withType(Test::class.java).configureEach {
		jvmArgs("-ea")
	}
}

// Enhance basice `clean` task description
maybeCreateCleanTask()
