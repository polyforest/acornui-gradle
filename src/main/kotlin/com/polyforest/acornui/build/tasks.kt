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

package com.polyforest.acornui.build

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.closureOf
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationWithResources
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import java.io.File

/**
 * Manually 'generated' kotlin accessor to [configure] a Kotlin Multiplatform Plugin (MPP) extension.
 */
private fun Project.kotlin(configure: KotlinMultiplatformExtension.() -> Unit): Unit =
	(this as ExtensionAware).extensions.configure("kotlin", configure)

/**
 * Easily document and [group] tasks that are created by Acorn UI plugins.
 *
 * @param description short description of what the task does (appears in IDE Gradle Tool window as task tooltips and
 * on the command line in the task list.
 * @param group helps organize tasks to make it easier for users.  By Gradle convention, tasks users aren't likely to
 * run directly are stored in the default "other" [group]
 * @param groupPrefix
 */
fun <T : Task> T.document(description: String? = null, group: String? = null, groupPrefix: String? = null) {
	val task = this
	val defaultGroup = "other"

	task.group = when {
		group == null && groupPrefix == null -> AUI.AP_TGROUP_PREFIX + defaultGroup
		group == null && groupPrefix != null -> groupPrefix + defaultGroup
		group != null && groupPrefix == null -> AUI.AP_TGROUP_PREFIX + group
		else                                 -> groupPrefix + group
	}

	description?.let { task.description = it }
}

/**
 * Adds `basic` starter Acorn UI resource directory as a resource directory if the consuming build has opted into using
 * them.
 *
 * Configuration is added to all compilations that have resource processing.
 * Configuration will apply to all targets and compilations added in the future.
 * `ACORNUI_HOME` must be defined (e.g. passed in as env var)
 *
 * Note: When `ACORNUI_HOME` is not provided via `gradle.properties`, importing the gradle project in the IDE will not
 * mark the directory as a resource in IDE project metadata.
 */
fun maybeAddBasicResourcesAsResourceDir(project: Project) {
	if (hasRequestedBasicAssets(project)) {
		if (isAcornUiHomeProvided(project)) {
			// Pseudo-Code:TODO | Undo this once stable
			// @Suppress("SpellCheckingInspection", "LocalVariableName")
			val ACORNUI_HOME = AUI(project).defaults[AUI.AUI_HOME_PROP_NAME]
			val basicSkinDir = File(ACORNUI_HOME, "skins/basic/resources")
			// Use closures so it is applied to all future targets and compilations added
			// TODO | Test whether inner (nested) closure is necessary
			val compilationClosure = project.closureOf<KotlinCompilation<KotlinCommonOptions>> {
				if (this is KotlinCompilationWithResources) {
					defaultSourceSet.resources.let {
						it.setSrcDirs(listOf(basicSkinDir, it.srcDirs))
					}
				}
			}
			val addBasicSkinResourcesAsResourceDir = project.closureOf<KotlinTarget> {
				compilations.all(compilationClosure)
			}
			if (hasRequestedBasicAssets(project) && basicSkinDir.exists()) {
				project.kotlin { targets.all(addBasicSkinResourcesAsResourceDir) }
			}
		} else
			project.logger.warn(
				"Until ${AUI.AUI_HOME_PROP_NAME} is added as a property or passed as an env var, basic starter assets " +
						"will not be used"
			)
	}
}

/**
 * Determine if the consuming build requested to use `basic` starter Acorn UI resource assets.
 *
 * This can be [opted into using Gradle properties](https://github.com/polyforest/acornui/wiki/FAQ#what-are-starter-resources).
 */
private fun hasRequestedBasicAssets(project: Project): Boolean {
	val USE_BASIC_ASSETS by AUI(project).defaults
	return USE_BASIC_ASSETS.toBoolean()
}

/**
 * Determine if the consuming build provided a value for the local Acorn UI repository directory property.
 */
private fun isAcornUiHomeProvided(project: Project): Boolean {
	val AUI_PROP = AUI.AUI_HOME_PROP_NAME
	return if (!project.hasProperty(AUI_PROP))
		false
	else {
		val acornUiHome = project.property(AUI_PROP) as String
		acornUiHome.isBlank().not()
	}
}

/**
 * Determine if the consuming build provided Acorn UI home location exists.
 *
 * Validation is shallow and does not check directory contents.
 */
private fun isProvidedAcornUiHomeValid(project: Project): Boolean {
	return if (isAcornUiHomeProvided(project)) {
		val AUI_PROP = AUI.AUI_HOME_PROP_NAME
		val acornUiHome = project.property(AUI_PROP) as String
		File(acornUiHome).exists()
	} else false
}

/**
 * Guarantee local Acorn UI home directory is valid if the consuming build has opted in to use `basic` starter Acorn UI
 * resource assets.
 *
 * Check is run just prior to execution of the receiver task, [T].
 */
fun <T : Task> T.validateAcornUiHomeToUseBasicAssets() {
	doFirst {
		if (hasRequestedBasicAssets(project) && !isProvidedAcornUiHomeValid(project)) {
			val AUI_PROP = AUI.AUI_HOME_PROP_NAME
			throw InvalidUserDataException(
				"""
					Starter assets were requested, but a valid $AUI_PROP could not be found.
					Please set $AUI_PROP in one of the following locations:

					-pass it on the command line as an environment variable => `ORG_GRADLE_PROJECT_ACORNUI_HOME=<absolute_path>`
					-set it in your `${project.gradle.gradleUserHomeDir}/gradle.properties` file => `ACORNUI_HOME=<absolute_path>`

					For more details, see: https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties
				""".trimIndent()
			)
		}
	}
}
