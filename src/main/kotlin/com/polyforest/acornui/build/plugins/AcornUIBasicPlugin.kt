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

package com.polyforest.acornui.build.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Provides a starter configuration for a basic (non-app) module that relies on Acorn UI.
 *
 * Aimed at modules that depend on Acorn UI modules.  Modules within an Acorn UI project that do not depend on Acorn UI
 * do not require this plugin.
 *
 * Plugin:   Acorn UI Basic Plugin
 * ID:       com.polyforest.acornui.basic
 * Applies:	 [KotlinMPBasicPlugin]
 */
class AcornUIBasicPlugin : Plugin<Project> {
	/**
	 * Apply this plugin to the given [target] object.
	 */
	override fun apply(target: Project) {
		TODO("not implemented")
	}
}