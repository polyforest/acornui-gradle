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

pluginManagement {
	repositories {
		maven { url = uri("../build/repository") }
		gradlePluginPortal()
	}
	resolutionStrategy {
		eachPlugin {
			if (requested.id.id.startsWith(ACORNUI_PLUGIN_MARKER_PREFIX))
				useVersion(ACORNUI_PLUGIN_VERSION)
		}
	}
}

val ACORNUI_PLUGIN_MARKER_PREFIX: String by settings
val ACORNUI_PLUGIN_VERSION: String by settings

include("normal-module", "normal-app-module", "builder-app-module", "app-app-module" )
