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

import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import java.io.File

/**
 * Provides enriched description and default group for the [Project]'s `clean` task.
 *
 * Optionally, perform configuration [action] immediately if provided.
 *
 * If the `clean` task doesn't exist, it is registered.
 *
 * @see register
 * @param action configuration to be performed immediately, optional.
 */
fun Project.maybeCreateCleanTask(action: (Delete.() -> Unit)? = null) {
	run {
		/**
		 * By waiting till after the project is evaluated to find or register the clean task, this is compatible
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
