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

import com.polyforest.acornui.build.utils.maybeExtra
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import java.io.File

typealias PropertyName = String
typealias GroupPackagePart = String
typealias MainClassPackagePart = String
typealias AppNamePackagePart = String

internal const val NO_PROP_FOUND_MSG = "No Gradle property provided by the consuming project or plugin defaults."

/**
 * Acorn UI constants and properties.
 *
 * @property project instance provided for generating various name conventions
 */
class AUI constructor(val project: Project) {

	/**
	 * Default property values that can be superseded by Gradle properties
	 */
	private val _defaults = mapOf(
		// Build dependencies & properties
		"GRADLE_VERSION" to "5.3",
		"NEBULA_PUBLISHING_PLUGIN" to "11.1.1",
		"NEBULA_RELEASE_PLUGIN" to "10.1.1",
		"NEBULA_BINTRAY_PLUGIN" to "7.2.7",
		"GRADLE_DEPENDENCY_LOCK_PLUGIN" to "7.3.4",
		"GRADLE_LINT_PLUGIN" to "11.5.0",
		"LIFERAY_NODE" to "4.6.15",
		"PRODUCT_GROUP" to "",
		"PRODUCT_VERSION" to "1.0.0-SNAPSHOT",
		// Library and application dependencies (and settings)
		"KOTLIN_VERSION" to "1.3.40",
		"KOTLIN_LANGUAGE_VERSION" to "1.3",
		"KOTLIN_JVM_TARGET" to "1.8",
		"ACORNUI_VERSION" to "1.0.0-SNAPSHOT",
		"LWJGL_VERSION" to "3.2.2",
		"USE_BASIC_ASSETS" to "true",
		"JS_MODULE_KIND" to "amd"
	)

	/**
	 * Unified data structure for plugin constants and static/dynamic defaults.
	 *
	 * Given usage of `withDefault`, must get properties by delegation (e.g. `val ACORNUI_HOME by acorn.defaults`
	 */
	val defaults = emptyMap<PropertyName, String>().withDefault { key ->
		try {
			(project.maybeExtra[key] as? String) ?: if (key == "JVM_MAIN") getJvmMain() else _defaults.getValue(key)
		} catch (e: NoSuchElementException) {
			val property = e.message?.substringAfter("Key ")?.substringBefore(" is missing")
			throw Throwable("$property >> $NO_PROP_FOUND_MSG", e)
		}
	}

	/**
	 * JVM main class package location conventions for Acorn UI apps.
	 *
	 * Order matters.  Earlier elements are prioritized over later elements of the list.
	 */
	private fun getCommonJvmMainLocs(
		group: GroupPackagePart,
		mainClass: MainClassPackagePart,
		appName: AppNamePackagePart
	): List<String> {
		return listOf(
			"$group.$mainClass",
			"$group.$appName.$mainClass",
			"$appName.$mainClass"
		)
	}

	/**
	 * Validate the prospective package [path] to main or fallback to common package schemes.
	 *
	 * Paths are validated by resolving whether an equivalent file is located in any valid source set.
	 * Path resolution handles jvm only and multiplatform projects that use the MPP plugin.
	 * If all paths fail to resolve, an empty string is returned to reduce boilerplate in DSL call sites.
	 *
	 * @param path package path to JVM main (e.g. com.
	 * @return validated path or empty string should all prospective paths fail to resolve
	 */
	fun getJvmMain(path: String? = null): String {
		// Ensure the file exists.
		fun jvmMainFile(path: String): String? {
			val srcDir = project.projectDir.resolve("src")
			val singleSource = srcDir.resolve("kotlin")
			val sourceRoots =
				if (singleSource.isDirectory)
					listOf(singleSource)
				else
					srcDir.listFiles().map { it.resolve("kotlin") }
			val pathSeparator = File.pathSeparator
			val packagePath = path
				.replace(".", pathSeparator)
				.removeSuffix("Kt").plus(".kt")

			return sourceRoots.firstOrNull {
				if (it.isDirectory) it.resolve(packagePath).isFile else false
			}?.let { path }
		}

		fun camelCasedProjectName() = project.name.split('-').joinToString { it.capitalize() }

		val PRODUCT_GROUP by defaults
		val mainClass = "${camelCasedProjectName()}JvmKt"
		val appPackageName = project.name.toLowerCase().replace("-", "")
		val commonJvmMainLocs = getCommonJvmMainLocs(PRODUCT_GROUP, mainClass, appPackageName)
		val noValidPathsFallback = ""
		// Prefer provided path over fallbacks
		val mainPathProspects = if (path != null) {
			listOf(path) + commonJvmMainLocs
		} else {
			commonJvmMainLocs
		}

		return mainPathProspects.firstOrNull { jvmMainFile(it) != null }?.toString() ?: noValidPathsFallback
	}

	companion object {
		/**
		 * Single parameter "constructor" singleton instance.
		 */
		private var instance: AUI? = null

		/**
		 * A single parameter "constructor" singleton for [AUI].
		 *
		 * @see [AUI] for more details.
		 */
		operator fun invoke(): AUI {
			return instance ?: AUI().also { instance = it }
		}

		/**
		 * Property name for the local Acorn UI repository directory.
		 */
		const val AUI_HOME_PROP_NAME = "ACORNUI_HOME"

		/**
		 * Default prefix used for tasks that Acorn UI plugins create.
		 */
		const val AP_TGROUP_PREFIX = ".ap."

		private const val ACORNUI_PROPERTIES_EXT_NAME = "acorn"

		private fun exposeOnRootProjectExtraProperties(project: Project) {
			val rootProject = project.rootProject
			ACORNUI_PROPERTIES_EXT_NAME.let {
				rootProject.maybeExtra[it] ?: project.maybeExtra[it] ?: rootProject.extra.set(it, instance)
			}
		}
	}
}

fun Project.getJvmMain(path: String? = null): String = AUI().getJvmMain()
