/*
 * Copyright 2019 Poly Forest
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

package com.polyforest.acornui

/**
 * Plugin:  com.polyforest.acornui.app-basic
 * Provide standard configuration for basic modules (not `app` or `builder` or root) of Acorn UI app projects
 *
 * Basic Kotlin multiplatform module
 * JS compilation main defaults to "noCall"
 * Use same ACORNUI_VERSION for all Acorn UI project dependencies
 */

plugins {
	id("com.polyforest.acornui.basic")
}

val defaults = mapOf(
	"ACORNUI_VERSION" to "1.0.0-SNAPSHOT"
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

// Enforce all Acorn UI dependencies use the same version
configurations.configureEach {
	resolutionStrategy.eachDependency {
		if (requested.group == "com.polyforest" && requested.name.startsWith("acornui-")) {
			useVersion(ACORNUI_VERSION)
			because("Adhering to the default target version for Acorn UI")
		}
	}
}
