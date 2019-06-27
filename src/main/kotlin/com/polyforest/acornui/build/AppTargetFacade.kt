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

@file:Suppress("UnstableApiUsage")

package com.polyforest.acornui.build

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationToRunnableFiles
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

class AppTargetFacade(
		private val project: Project,
		mppExtension: KotlinMultiplatformExtension,
		name: String,

		/**
		 * Archival extensions (without the . prefix) used to recognize archives.
		 */
		private val archiveExtensions: List<String> = listOf("war", "jar", "zip")
) {
	private val target = mppExtension.targets.named(name)
	private val allProjects
		get() = try {
			project.rootProject.subprojects - project.project(":$AP_BUILD_MODULE_NAME")
		} catch (e: Exception) {
			project.rootProject.subprojects
		}
	private val mainCompilation
		get() = target.map {
			it.compilations.named<KotlinCompilationToRunnableFiles<KotlinCommonOptions>>(DEFAULT_MAIN_COMPILATION_NAME)
					.get()
		}

	/**
	 * All sources for a given multi-platform target's module
	 *
	 * e.g.
	 * given:
	 * a target named "js" created via preset,
	 * composed of multi-platform sources commonSourceSetA and jsSourceSetA...
	 *
	 * when:
	 * jsSourceSetA declares a dependency on jsSourceSetB,
	 * where all source sets are defined in the same multi-platform module...
	 *
	 * then:
	 * [mpSources] = commonSourceSetA + jsSourceSetA + jsSourceSetB
	 */
	val mpSources = mainCompilation.map { it.output.classesDirs }

	/**
	 * All resources for a given multi-platform target's module
	 *
	 * Same as [mpSources], but for resources
	 * @see [mpSources]
	 */
	val mpResources = mainCompilation.map {
		it.allKotlinSourceSets.fold(project.files()) { fc: FileCollection, sourceSet: KotlinSourceSet ->
			fc + sourceSet.resources.sourceDirectories
		}
	}

	/**
	 * All source files found in the target's internal/external modules dependencies
	 * Excludes archival and source metadata if applicable.
	 *
	 * NOTE: Given this method relies on exploded archives that make up the runtime classpath, it's not guaranteed
	 * that the returned file collection provider only has source files.
	 *
	 */
	private val mpXDependencySources = mainCompilation.map {
		extractArchives(it.runtimeDependencyFiles).matching {
			exclude("META-INF/")
		}
	}

	private fun extractArchives(files: FileCollection): FileTree {
		val emptyFileTree = project.files().asFileTree

		return filterToArchives(files).fold(emptyFileTree) { fileTreeAccumulator: FileTree, archive: File ->
			fileTreeAccumulator + project.zipTree(archive)
		}
	}

	private fun filterToArchives(files: FileCollection) =
			files.filter { file -> file.extension in archiveExtensions }

	/**
	 * All sources for a given multi-platform target's module and its cross module multi-platform dependencies
	 * Includes internal, external, and transitive dependency sources.
	 *
	 * Same as [mpSources], but also cross-module
	 * @see [mpSources]
	 */
	val mpXSources = project.provider {
		mpXDependencySources.get() + mpSources.get()
	}

	/**
	 * All resources for a given multi-platform target's module and its cross module multi-platform dependencies
	 * Includes all resources for all modules in the root Gradle project sans unrelated modules (e.g. build module)
	 *
	 * Same as [mpResources], but also cross all modules in the Gradle build
	 * @see [mpResources]
	 */
	val mpXResources = allProjects.fold(project.files()) { fc: FileCollection, p: Project ->
		// TODO: Make this leverage dependencies so it is more accurate and less brittle
		val mainCompilation =
				mppExtension.targets[target.name].compilations[DEFAULT_MAIN_COMPILATION_NAME]

		fc + mainCompilation.allKotlinSourceSets.fold(fc) { innerFC: FileCollection, sourceSet: KotlinSourceSet ->
			innerFC + sourceSet.resources.sourceDirectories
		}
	}

	companion object {
		private const val AP_BUILD_MODULE_NAME = "builder"
		private const val APPLICATION_GROUP = ApplicationPlugin.APPLICATION_GROUP
		const val DEFAULT_JS_TARGET_APP_TARGET_NAME = "js"
		const val DEFAULT_JVM_TARGET_APP_TARGET_NAME = "jvm"
		private const val DEFAULT_MAIN_COMPILATION_NAME = KotlinCompilation.MAIN_COMPILATION_NAME
	}
}