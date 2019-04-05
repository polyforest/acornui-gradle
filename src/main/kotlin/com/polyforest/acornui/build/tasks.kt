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

package com.polyforest.acornui.build

import org.gradle.api.Task
import org.gradle.kotlin.dsl.extra
// Required for `project.extra` though IDE flags it as un-used
import org.gradle.kotlin.dsl.provideDelegate

fun <T : Task> T.document(description: String? = null, group: String? = null, groupPrefix: String? = null) {
	val task = this
	val defaultGroup = "other"
	// TODO | Refactor this to pull from global plugins defaults
	val AP_TGROUP_PREFIX: String by project.extra

	task.group = when {
		group == null && groupPrefix == null -> AP_TGROUP_PREFIX + defaultGroup
		group == null && groupPrefix != null -> groupPrefix + defaultGroup
		group != null && groupPrefix == null -> AP_TGROUP_PREFIX + group
		else -> groupPrefix + group
	}

	description?.let { task.description = it }
}
