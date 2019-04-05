package com.polyforest.acornui.build

import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import java.io.File

/**
 * Gets existing or registers new 'clean' task using configuration avoidance api.
 *
 * Provides enriched description and default group.
 * @param action
 */
fun Project.maybeCreateCleanTask(action: (Delete.() -> Unit)? = null) {
	run {
		/**
		 * By waiting till after the project is evaluated to find or register the clean task, this plugin is compatible
		 * with almost all plugins whether applied before or after it.
		 *
		 * (could have complications with plugins that do things in afterEvaluate like this)
		 */
		afterEvaluate {
			val clean = tasks.withType(Delete::class).tryNamed(BasePlugin.CLEAN_TASK_NAME)
				?: tasks.register<Delete>(BasePlugin.CLEAN_TASK_NAME)

			clean {
				action?.let { it() }

				if (group.isNullOrBlank())
					group = "build"
				description = """
            Deletes:
            ${delete.joinToString("\n") {
					if (it is File)
						it.relativeToOrSelf(projectDir).path
					else
						it.toString()
				}}

        (all files relative to project directory unless absolute)
        """.trimIndent()
			}
		}
	}
}
