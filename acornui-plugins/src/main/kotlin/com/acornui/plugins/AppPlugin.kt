package com.acornui.plugins

import com.acornui.core.toCamelCase
import com.acornui.io.file.FilesManifestSerializer
import com.acornui.io.file.ManifestUtil
import com.acornui.plugins.tasks.AssembleWebProdTask
import com.acornui.plugins.tasks.AssembleWebTask
import com.acornui.plugins.tasks.DceTask
import com.acornui.plugins.tasks.KotlinJsMonkeyPatcherTask
import com.acornui.plugins.util.kotlinExt
import com.acornui.serialization.json
import com.acornui.serialization.write
import com.acornui.texturepacker.jvm.TexturePackerUtil
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationToRunnableFiles
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import java.io.File

@Suppress("unused")
class AppPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.pluginManager.apply("com.acornui.plugins.kotlin-mpp")
		target.pluginManager.apply("org.gradle.idea")
//		target.pluginManager.apply("kotlin-dce-js")
		target.extensions.configure(multiPlatformConfig(target))

		configureResourceProcessing(target)
		configureRunJvmTask(target)

		// Register the assembleWeb task that builds the www directory.
		target.tasks.register<AssembleWebTask>("assembleWeb") {
			group = "build"
		}

		target.tasks.register<AssembleWebProdTask>("assembleWebProd") {
			dependsOn("assemble")
			group = "build"
			finalizedBy("prodDce")
			finalizedBy("kotlinJsMonkeyPatch")
		}

		target.tasks.register<DceTask>("prodDce")
		target.tasks.register<KotlinJsMonkeyPatcherTask>("kotlinJsMonkeyPatch") {
			val assembleWebProd = project.tasks.named("assembleWebProd").get() as AssembleWebProdTask
			source(assembleWebProd.destination)
		}

		target.tasks.named<Delete>("clean") {
			doLast {
				val assembleWeb = project.tasks.named("assembleWeb").get() as AssembleWebTask
				delete(assembleWeb.destination)
				val assembleWebProd = project.tasks.named("assembleWebProd").get() as AssembleWebProdTask
				delete(assembleWebProd.destination)
			}
		}

		target.tasks["assemble"].dependsOn("assembleWeb")
	}

	private fun multiPlatformConfig(target: Project): KotlinMultiplatformExtension.() -> Unit = {
		js {
			compilations.all {
				kotlinOptions {
					main = "call"
				}
			}
		}

		sourceSets {
			@Suppress("UNUSED_VARIABLE")
			val commonMain by getting {
				dependencies {
					implementation("com.acornui:acornui-core")
					implementation("com.acornui:acornui-utils")
				}
			}

			jvm().compilations["main"].defaultSourceSet {
				dependencies {
					implementation("com.acornui:acornui-lwjgl-backend")

					val lwjglVersion: String by target.extra
					val lwjglGroup = "org.lwjgl"
					val lwjglName = "lwjgl"

					@Suppress("INACCESSIBLE_TYPE")
					val os = when (OperatingSystem.current()) {
						OperatingSystem.LINUX -> "linux"
						OperatingSystem.MAC_OS -> "macos"
						OperatingSystem.WINDOWS -> "windows"
						else -> throw Exception("Unsupported operating system: ${OperatingSystem.current()}")
					}
					val extensions = arrayOf("glfw", "jemalloc", "opengl", "openal", "stb", "nfd", "tinyfd")
					runtimeOnly("$lwjglGroup:$lwjglName:$lwjglVersion:natives-$os")
					extensions.forEach {
						implementation("$lwjglGroup:$lwjglName-$it:$lwjglVersion")
						runtimeOnly("$lwjglGroup:$lwjglName-$it:$lwjglVersion:natives-$os")
					}
				}
			}
			js().compilations["main"].defaultSourceSet {
				dependencies {
					implementation("com.acornui:acornui-webgl-backend")
				}
			}
		}
	}

	private fun configureResourceProcessing(target: Project) {
		target.tasks.withType<ProcessResources> {
			doLast {
				logger.lifecycle("Packing assets")
				logger.info("Assets root: ${destinationDir.absolutePath}")

				// Pack the assets in all directories in the dest folder with a name ending in "_unpacked"
				TexturePackerUtil.packAssets(destinationDir, File("."), quiet = true)

				logger.lifecycle("Writing manifest")
				val assetsDir = File(destinationDir, "assets")
				val manifest = ManifestUtil.createManifest(assetsDir, destinationDir)
				File(assetsDir, "files.json").writeText(json.write(manifest, FilesManifestSerializer))
			}
		}
	}

	private fun configureRunJvmTask(target: Project) {
		with(target) {
			val jvmArgs: String? by extra
			tasks.register<JavaExec>("runJvm") {
				group = "application"
				val jvmTarget: KotlinTarget = kotlinExt.targets["jvm"]
				val compilation = jvmTarget.compilations["main"] as KotlinCompilationToRunnableFiles<KotlinCommonOptions>

				val classes = files(
						compilation.runtimeDependencyFiles,
						compilation.output.allOutputs
				)
				classpath = classes
				workingDir = compilation.output.resourcesDir
				main = "${rootProject.group}.${rootProject.name}.jvm.${rootProject.name.toCamelCase().capitalize()}JvmKt"

				@Suppress("INACCESSIBLE_TYPE")
				this.jvmArgs = (jvmArgs?.split(" ") ?: listOf("-ea", "-Ddebug=true")) + if (OperatingSystem.current() == OperatingSystem.MAC_OS) listOf("-XstartOnFirstThread") else emptyList()
			}
		}
	}
}