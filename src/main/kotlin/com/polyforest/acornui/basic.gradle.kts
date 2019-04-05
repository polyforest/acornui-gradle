package com.polyforest.acornui

repositories {
	jcenter()
}

plugins {
	id("org.jetbrains.kotlin.multiplatform")
}

val defaults = mapOf(
	"GRADLE_VERSION" to "5.3",
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

val KOTLIN_LANGUAGE_VERSION: String = extraOrDefault("KOTLIN_LANGUAGE_VERSION")
val KOTLIN_JVM_TARGET: String = extraOrDefault("KOTLIN_JVM_TARGET")
val GRADLE_VERSION: String = extraOrDefault("GRADLE_VERSION")
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

tasks.withType<Wrapper> {
	gradleVersion = GRADLE_VERSION
	distributionType = Wrapper.DistributionType.ALL
}

afterEvaluate {
	tasks.withType(Test::class).configureEach {
		jvmArgs("-ea")
	}
}

afterEvaluate {
	val clean = tasks.withType(Delete::class).tryNamed(BasePlugin.CLEAN_TASK_NAME) ?: tasks.register<Delete>(BasePlugin.CLEAN_TASK_NAME)
	clean {
		delete(file("out/"))
	}
}
