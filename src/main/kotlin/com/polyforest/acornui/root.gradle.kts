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
