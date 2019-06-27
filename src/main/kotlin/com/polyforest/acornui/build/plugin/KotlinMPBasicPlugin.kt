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

package com.polyforest.acornui.build.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Provides a basic configuration for a multi-platform (JS & JVM) Kotlin module.
 *
 * JS compilation main defaults to "noCall"
 *
 * Plugin:   Kotlin Multi-Platform Basic Plugin
 * ID:  	 com.polyforest.kotlin.mp-basic
 * Applies:	 None
 */
class KotlinMPBasicPlugin: Plugin<Project> {
	/**
	 * Apply this plugin to the given [target] object.
	 */
	override fun apply(target: Project) {
		TODO("not implemented")
	}
}