package com.acornui.plugins

import com.acornui.core.asset.AssetManager
import com.acornui.core.di.inject
import com.acornui.core.io.file.Files
import com.acornui.jvm.JvmHeadlessApplication
import com.acornui.texturepacker.jvm.TexturePackerUtil
import org.gradle.api.Plugin
import org.gradle.api.Project
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
		target.extensions.configure(multiPlatformConfig(target))

		configureResourceProcessing(target)
		configureRunJvmTask(target)
	}

	private val Project.kotlin: KotlinMultiplatformExtension
		get() = extensions.getByType()

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
				JvmHeadlessApplication(destinationDir.path).start {
					// Pack the assets in all directories in the dest folder with a name ending in "_unpacked"
					TexturePackerUtil(inject(Files), inject(AssetManager)).packAssets(destinationDir, File("."))
				}
			}
		}
	}

	private fun configureRunJvmTask(target: Project) {
		with(target) {
			val mainJvmClassName: String by extra
			val jvmArgs: String? by extra
			tasks.register<JavaExec>("runJvm") {
				group = "application"
				val jvmTarget: KotlinTarget = kotlin.targets["jvm"]
				val compilation = jvmTarget.compilations["main"] as KotlinCompilationToRunnableFiles<KotlinCommonOptions>

				val classes = files(
						compilation.runtimeDependencyFiles,
						compilation.output.allOutputs
				)
				classpath = classes
				workingDir = compilation.output.resourcesDir
				main = mainJvmClassName

				@Suppress("INACCESSIBLE_TYPE")
				this.jvmArgs = (jvmArgs?.split(" ") ?: listOf("-ea", "-Ddebug=true")) + if (OperatingSystem.current() == OperatingSystem.MAC_OS) listOf("-XstartOnFirstThread") else emptyList()
			}
		}
	}
}