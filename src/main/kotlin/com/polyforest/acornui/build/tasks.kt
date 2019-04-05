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
