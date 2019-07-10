package com.acornui.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class AppPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.pluginManager.apply("com.acornui.plugins.kotlin-mpp")

		target.extensions.configure<KotlinMultiplatformExtension> {
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
	}
}