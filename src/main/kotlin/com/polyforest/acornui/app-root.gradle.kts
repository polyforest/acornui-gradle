package com.polyforest.acornui

import com.polyforest.acornui.build.document
import org.gradle.api.DefaultTask
import org.gradle.kotlin.dsl.existing
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.registering
import java.io.File

/**
 * Plugin:  com.polyforest.acornui.app-root
 * Provide standard configuration for root build scripts of Acorn UI app projects
 */

tasks {
	// Setup IDE composite support tasks if developer has opted in by setting the system property
	if (System.getProperty("composite.intellij")?.toBoolean() == true) {

		val convertCompositeDependencies by registering(DefaultTask::class) {
			document("Converts included builds' IDE dependencies from libraries (.jar) to modules.", "composite")

			doLast {
				val ideMetadataDir = project.layout.projectDirectory.dir(".idea").asFile
				require(ideMetadataDir.exists() && ideMetadataDir.isDirectory)

				// Targeted dependency conversions (map/data value contents:  <regex-string> to <replace-string>)
				val dependencyConversionRules = mapOf(
					"Common Module Library" to ("""(<orderEntry type)="library" (name)="Gradle: (com.polyforest):(acornui-.*)-metadata:.*" level="project".*(/>)""" to "\$1=\"module\" module-\$2=\"\$3.\$4.commonMain\" \$5"),
					"Common Module Library (Test)" to ("""(<orderEntry type)="library" (scope="TEST") (name)="Gradle: (com.polyforest):(acornui.*)-metadata:.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.commonTest\" \$2 \$6"),
					"Common Module Library (Runtime)" to ("""(<orderEntry type)="library" (scope="RUNTIME") (name)="Gradle: (com.polyforest):(acornui.*)-metadata:.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.commonMain\" \$2 \$6"),
					"JVM & JS Module Library" to ("""(<orderEntry type)="library" (name)="Gradle: (com.polyforest):(acornui-.*)-(jvm|js):.*" level="project".*(/>)""" to "\$1=\"module\" module-\$2=\"\$3.\$4.\$5Main\" \$6"),
					"JVM & JS Module Library (Test)" to ("""(<orderEntry type)="library" (scope="TEST") (name)="Gradle: (com.polyforest):(acornui.*)-(jvm|js):.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.\$6Test\" \$2 \$7"),
					"JVM & JS Module Library (Runtime)" to ("""(<orderEntry type)="library" (scope="RUNTIME") (name)="Gradle: (com.polyforest):(acornui.*)-metadata:.*".*(/>)""" to "\$1=\"module\" module-\$3=\"\$4.\$5.commonMain\" \$2 \$6")
				).map { ruleEntry ->
					object {
						val shortname = ruleEntry.key
						val name = "'${shortname}' Dependency"
						val longname = "$name + => Reciprocal IDE Module Dependency"
						val pattern = Regex(ruleEntry.value.first)
						val replacement = ruleEntry.value.second

						private val noMatchDefault = "no match"
						fun formatMatchResult(result: String = noMatchDefault): String {
							return "\t$shortname [${ if (!result.isBlank())
								result.toUpperCase()
							else
								noMatchDefault }]"
						}
					}
				}

				logger.quiet("Converting ${project.name}'s IDE library dependencies (via a composite build setup) to " +
									 "IDE module dependencies.")

				val imlFiles = ideMetadataDir.walkTopDown().filter { child: File ->
					child.exists() && child.isFile && child.extension == "iml"
				}

				imlFiles.forEach { file: File ->
					val fileContents = file.readText()

					if (!fileContents.isNullOrBlank()) {
						logger.lifecycle("${file.name}...")
						var newFileContents = fileContents

						dependencyConversionRules.forEach { rule ->
							if (rule.pattern.containsMatchIn(newFileContents)) {
								logger.lifecycle(rule.formatMatchResult("converted"))
								newFileContents = rule.pattern.replace(newFileContents, rule.replacement)
							} else
								logger.lifecycle(rule.formatMatchResult())
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
				logger.quiet("""
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
				""".trimIndent())
			}
		}

		val aboutIdeCompositeBuilds by registering(DefaultTask::class) {
			description = "Provides context for working with composite builds created/configured in Intellij."
			group = "help"
			doLast {
				logger.quiet("""
					Composite builds allow developers to work on projects side by side in a seamless way.  Intellij creates and manages composite builds created within the IDE differently than those created via other methods.

					Primarily, the build is concerned with the fact that dependencies between provider and consumer builds (e.g. framework project and app project respectively) end up being represented as Gradle produced jars since they are setup as libraries.  This has a few negative knock on effects.

						1. Changes in provider code must be built and published to the local maven repository to take effect in the consuming project.
						2. Using the IDE to jump to provider source when using it in the consumer project will not take the user to the appropriate provider source file or even the source jar file (due to an Intellij bug) as desired.
						3. Gradle compilation is significantly slower for iteration, because it does not support partial compilation like JPS does.  It is, however, more reliable.

					To address these issues, a Gradle task has been provided to automate manipulating IDE metadata to convert Acorn UI dependencies from library dependencies into module dependencies.

					This will only affect building with the IDE and does not affect CLI compilation in a dev environment nor CI building in any capacity.

					What's next?

					Running the ${gettingStartedWithIdeCompositeBuilds.name} task will provide you instructions on getting started from ground zero after cloning an existing app project repo.
				""".trimIndent())
			}
		}

		val compositeConfiguredStatus by registering(DefaultTask::class) {
			description = "Default task that shows short message to orient new composite build users."

			doLast {
				logger.quiet("""
						IDE Composite Build configuration flag detected.  In the help task group...
							for general context, run the ${aboutIdeCompositeBuilds.name} task
							for setup instructions, run the ${gettingStartedWithIdeCompositeBuilds.name} task
					""".trimIndent())
			}
		}

		// Setup compositeConfiguredStatus to show by default when the IDE imports or syncs the project
		val wrapper by existing
		wrapper.orNull?.dependsOn(compositeConfiguredStatus)
	}
}
