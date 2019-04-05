package com.polyforest.acornui

import com.polyforest.acornui.build.*

/**
 * Plugin:  com.polyforest.acornui.builder
 * Used in Acorn UI consumer projects for the builder module.
 * JS compilation main defaults to "noCall"
 */

plugins {
	id("com.polyforest.acornui.basic")
}

kotlin {
	sourceSets {
		commonMain {
			dependencies {
				implementation(acornui("build-tasks-metadata"))
				implementation(acornui("texture-packer-metadata"))
				implementation(acornui("utils-metadata"))
				implementation(acornui("core-metadata"))
				implementation(acornui("lwjgl-backend-metadata"))
			}
		}
		named("jvmMain") {
			dependencies {
				implementation(kotlin("compiler"))
				// Provided for kotlin.js extraction during acornui asset building
				runtimeOnly(kotlin("stdlib-js"))

				implementation(acornui("build-tasks-jvm"))
				implementation(acornui("texture-packer-jvm"))
				implementation(acornui("utils-jvm"))
				implementation(acornui("core-jvm"))
				implementation(acornui("lwjgl-backend-jvm"))
			}
		}
	}
}
