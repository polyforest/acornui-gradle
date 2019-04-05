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

import com.polyforest.acornui.build.*

/**
 * Plugin:  com.polyforest.acornui.builder
 * 
 * Used in Acorn UI consumer projects for the builder module.
 * JS compilation main defaults to "noCall"
 */

plugins {
	id("com.polyforest.acornui.app-basic")
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
