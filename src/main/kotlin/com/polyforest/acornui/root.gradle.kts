package com.polyforest.acornui

import org.gradle.kotlin.dsl.withType
import com.polyforest.acornui.build.*

/**
 * Plugin:  com.polyforest.acornui.root
 * Provide standard configuration for root build scripts
 */

val defaults = mapOf(
	"GRADLE_VERSION" to "5.3"
)

fun extraOrDefault(name: String, default: String = defaults.getValue(name)) : String {
	return try {
		@Suppress("UNCHECKED_CAST")
		extra[name] as String
	} catch (e: Exception) {
		default
	}
}

val GRADLE_VERSION: String = extraOrDefault("GRADLE_VERSION")

allprojects {
	tasks.withType<Wrapper> {
		gradleVersion = GRADLE_VERSION
		distributionType = Wrapper.DistributionType.ALL
	}
}

// Delete the IDE default build directory upon `clean`
// Also, for some reason a 'build' directory at the root project (with empty contents) gets created
maybeCreateCleanTask {
	delete("out/", "build/")
}
