package com.polyforest.acornui.build

import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider

fun <T : Task> TaskCollection<T>.tryNamed(name: String): TaskProvider<T>? {
	return try {
		named(name)
	} catch (e: UnknownTaskException) {
		null
	}
}