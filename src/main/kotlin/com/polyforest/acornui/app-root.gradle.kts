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

package com.polyforest.acornui

import com.polyforest.acornui.build.AUI
import org.gradle.api.DefaultTask
// import org.gradle.kotlin.dsl.provideDelegate
import java.io.File
import com.polyforest.acornui.build.document

/**
 * Plugin:  com.polyforest.acornui.app-root
 * Provide standard configuration for root build scripts of Acorn UI app projects
 */

plugins {
	id("com.polyforest.acornui.root")
}

tasks {
	// Setup IDE composite support tasks if developer has opted in by setting the system property
	if (System.getProperty("composite.intellij")?.toBoolean() == true) {

		val convertCompositeDependencies by registering(DefaultTask::class) {
			document("Converts included builds' IDE dependencies from libraries (.jar) to modules.", "composite")

			doLast {
				val ideSettingsDir = project.layout.projectDirectory.dir(".idea").asFile
				require(ideSettingsDir.exists() && ideSettingsDir.isDirectory)

				// Setup dependency conversion rules
				val moduleGroup ="com.polyforest"
				val sharedModuleBase = "acornui"
				val modulePrefix = "$sharedModuleBase-"
				val modules = "$modulePrefix[^-]+"
				val platforms = "jvm|js"
				val runtimeScope = "RUNTIME"
				val testScope = "TEST"
				val metadataSuffix = "metadata"
				// Rules (map/data value contents:  <regex-string> to <replace-string>)
				val dependencyConversionRules = mapOf(
					"Common Module Library" to ("""(<orderEntry type)="library" (name)="Gradle: ($moduleGroup):($modules)-$metadataSuffix:.*" level="project".*(/>)""" to "\$1=\"module\" module-\$2=\"\$3.\$4.commonMain\" \$5"),
					"Common Module Library (Test)" to ("""(<orderEntry type)="library" (scope="$testScope") (name)="Gradle: ($moduleGroup):($modules)-$metadataSuffix:.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.commonTest\" \$2 \$6"),
					"Common Module Library (Runtime)" to ("""(<orderEntry type)="library" (scope="$runtimeScope") (name)="Gradle: ($moduleGroup):($modules)-$metadataSuffix:.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.commonMain\" \$2 \$6"),
					"JVM & JS Module Library" to ("""(<orderEntry type)="library" (name)="Gradle: ($moduleGroup):($modules)-($platforms):[0-9\.]+" level="project" (/>)""" to "\$1=\"module\" module-\$2=\"\$3.\$4.\$5Main\" \$6"),
					"JVM & JS Module Library (Test)" to ("""(<orderEntry type)="library" (scope="$testScope") (name)="Gradle: ($moduleGroup):($modules)-($platforms):.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.\$6Test\" \$2 \$7"),
					"JVM & JS Module Library (Runtime)" to ("""(<orderEntry type)="library" (scope="$runtimeScope") (name)="Gradle: ($moduleGroup):($modules)-($platforms):.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.commonMain\" \$2 \$6")
				).mapKeys { it.key + " dependency conversion" }.map { Rule(it) }


				val slash = (File.separator).let { if (it == "\\") it.repeat(2) else it }
				val pathSeparator = File.pathSeparator
				val groupPathPart =
					moduleGroup.toString().takeIf { it.isNotBlank() }?.let { "$slash${it.replace(".", slash)}" } ?: ""

				/**
				 * Get a regex pattern module name string for any modules that use [moduleNameBase]
				 *
				 * Supports the following module name styles given a [moduleNameBase] `lib`
				 * - `lib`
				 * - `lib-module` & `lib-module-name`
				 * - `lib-metadata`, `lib-module-metadata`, & `lib-module-name-metadata`
				 * - _(same as above with passed [platforms])_
				 *
				 * It is not recommended to use this outside of a classpath context with IDE config files.
				 * @param moduleNameBase to match (e.g. `lib`)
				 * @param platforms to match (e.g. `jvm|js`)
				 **/
				fun classpathModuleName(moduleNameBase: String, platforms: String) =
					"""$moduleNameBase(?:-[^-${File.pathSeparator}$slash]+)*?(?:-(?:$metadataSuffix|$platforms))?"""

				val gradleCache = """\.gradle${slash}caches"""
				val localMaven = """\.m2${slash}repository"""
				fun artifactFilenamePattern(moduleNameBase: String, platforms: String) =
					"""${classpathModuleName(moduleNameBase, platforms)}-[\d\.]+\.jar"""

				fun artifactPath(moduleNameBase: String, platforms: String) =
					"""$pathSeparator?[^$pathSeparator]+(?=(?:$gradleCache|$localMaven)[^$pathSeparator]+?$slash${artifactFilenamePattern(
						moduleNameBase,
						platforms
					)})[^$pathSeparator]+"""

				/**
				 * Get a display friendly description of the classpath cleaning rule.
				 *
				 * @param moduleGroup takes the form of "com.example"
				 * @param moduleNamePrefix takes the form of "something-" where the full module name is
				 * "something-core", "something-core-utils", or "something"
				 */
				fun getCleanClasspathRuleName(moduleGroup: Any, moduleNamePrefix: String) =
					"Clean classpath - $moduleGroup:$moduleNamePrefix.* $metadataSuffix|$platforms jars"

				// Rules (map/data value contents:  <regex-string> to <replace-string>)
				val classpathCleanupRules = mapOf(
					getCleanClasspathRuleName(moduleGroup, modulePrefix) to (artifactPath(
						sharedModuleBase,
						"jvm"
					) to "")
				).map { Rule(it) }

				val imlFiles = ideSettingsDir.walkTopDown().filter { child: File ->
					child.exists() && child.isFile && child.extension == "iml"
				}

				imlFiles.forEach { file: File ->
					val fileContents = file.readText()

					if (!fileContents.isNullOrBlank()) {
						logger.lifecycle("${file.name}...")
						var newFileContents = fileContents

						logger.quiet(
							"Converting ${project.name}'s IDE library dependencies (via a composite build setup) " +
									"to IDE module dependencies."
						)
						dependencyConversionRules.forEach {
							it.process(newFileContents)?.let { newFileContents = it }
						}

						logger.quiet("Cleaning up library jars from classpath...")
						// Extract classpath for more efficient and easier to maintain regex
						val classpathValueRegex = Regex("""(?<=<option name="classpath" value=")[^"]+""")
						val classpath = classpathValueRegex.find(newFileContents)?.value
						val newClasspath = classpath?.let {
							var tempNewCP = it
							classpathCleanupRules.forEach { rule ->
								tempNewCP = rule.process(tempNewCP) ?: tempNewCP
							}
							tempNewCP
						}
						newClasspath?.let {
							newFileContents = newFileContents.replace(classpathValueRegex, it.replace("$", "\\$"))
						}

						file.writeText(newFileContents)
					}
				}
			}
		}


		val gettingStartedWithIdeCompositeBuilds by registering(DefaultTask::class) {
			description = "Instructions for setting up IDE managed Composite Builds."
			group = "help"

			doLast {
				logger.quiet(
					"""
					Throughout these instructions, Acorn UI is the 'provider' and an App is the 'consumer'

					If you're an old pro and have already configured your App IDE project as a composite build, skip straight to step 4 below.

					## Initial Setup ##
					...assuming a working Gradle dev environment for both Acorn UI and the app project per https://github.com/polyforest/acornui/wiki/Gradle-Migration-Getting-Started

					1. Attach the provider Gradle project (acornui) to the IDE project.
					    i.  Click the + button at the top of the Gradle Tool window.
						ii. Browse to and select the provider's root Gradle build script:  `acornui/build.gradle.kts`
					The Acorn UI IDE project should appear in the IDE's Project window and the Acorn UI Gradle project
					should appear next to the App Gradle project in the Gradle Tool window.
					2. Configure the consumer Gradle project (app) to be a composite build.
						i.  Right click the consumer Gradle project in the Gradle Tool window and select `Composite Build Configuration`
						ii. Check the appropriate provider Gradle project it is dependent on (acornui)
					3. For safety, close/re-open the consumer project to make sure the IDE gets through any actions related to composite build setup.
					4. Run the ${convertCompositeDependencies.name} task to convert the dependencies!

					Now!  All Acorn UI library dependencies should be converted to module dependencies in Project Structure.

					Currently, the original library definitions in Project Structure > Libraries are not removed.  If the IDE complains about unused libraries, it provides mechanisms to easily get rid of them.

					## IMPORTANT ##
					It's possible for different actions to trigger Gradle's dependency data to be re-imported, reverting composite build dependencies back to library dependencies in the IDE.  To correct the problem, simply run the conversion task again.

					As of yet, invalidating IDE caches is the only known action that *inadvertently* triggers dependency reversion.

					Obviously, other actions might also trigger it, but these are more obvious and expected, such as
					refreshing Gradle dependencies from the Gradle Tool window.

					If you experience any others, please let us know by filing an issue (https://github.com/polyforest/acornui/issues), or better yet, submit a PR with changes to documentation.
				""".trimIndent()
				)
			}
		}

		val aboutIdeCompositeBuilds by registering(DefaultTask::class) {
			description = "Provides context for working with composite builds created/configured in Intellij."
			group = "help"
			doLast {
				logger.quiet(
					"""
					Composite builds allow developers to work on projects side by side in a seamless way.  Intellij creates and manages composite builds created within the IDE differently than those created via other methods.

					Primarily, the build is concerned with the fact that dependencies between provider and consumer builds (e.g. framework project and app project respectively) end up being represented as Gradle produced jars since they are setup as libraries.  This has a few negative knock on effects.

						1. Changes in provider code must be built and published to the local maven repository to take effect in the consuming project.
						2. Using the IDE to jump to provider source when using it in the consumer project will not take the user to the appropriate provider source file or even the source jar file (due to an Intellij bug) as desired.
						3. Gradle compilation is significantly slower for iteration, because it does not support partial compilation like JPS does.  It is, however, more reliable.

					To address these issues, a Gradle task has been provided to automate manipulating IDE metadata to convert Acorn UI dependencies from library dependencies into module dependencies.

					This will only affect building with the IDE and does not affect CLI compilation in a dev environment nor CI building in any capacity.

					What's next?

					Running the ${gettingStartedWithIdeCompositeBuilds.name} task will provide you instructions on getting started from ground zero after cloning an existing app project repo.
				""".trimIndent()
				)
			}
		}

		val compositeConfiguredStatus by registering(DefaultTask::class) {
			description = "Default task that shows short message to orient new composite build users."

			doLast {
				logger.quiet(
					"""
						IDE Composite Build configuration flag detected.  In the help task group...
							for general context, run the ${aboutIdeCompositeBuilds.name} task
							for setup instructions, run the ${gettingStartedWithIdeCompositeBuilds.name} task
					""".trimIndent()
				)
			}
		}

		// Setup compositeConfiguredStatus to show by default when the IDE imports or syncs the project
		val wrapper by existing
		wrapper.orNull?.dependsOn(compositeConfiguredStatus)
	}
}

open class Rule(ruleEntry: Map.Entry<String, Pair<String, String>>) {
	private val shortname = ruleEntry.key
	private val pattern = Regex(ruleEntry.value.first)
	private val replacement = ruleEntry.value.second

	private fun renderMatchResult(result: String = "") = "\t$shortname [${result.toUpperCase()}]"

	fun process(contents: String?): String? {
		return contents?.let {
			if (pattern.containsMatchIn(it)) {
				logger.lifecycle(renderMatchResult(POSITIVE_RESULT_KEYPHRASE))
				pattern.replace(it, replacement)
			} else {
				logger.lifecycle(renderMatchResult(NO_MATCH_RESULT_KEYPHRASE))
				null
			}
		}
	}

	companion object {
		const val POSITIVE_RESULT_KEYPHRASE = "processed"
		const val NO_MATCH_RESULT_KEYPHRASE = "no match"
	}
}
