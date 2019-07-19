package com.acornui.plugins

import com.acornui.core.toCamelCase
import com.acornui.plugins.util.appAssetsWebTasks
import com.acornui.plugins.util.applicationResourceTasks
import com.acornui.plugins.util.kotlinExt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationToRunnableFiles
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import java.io.File

@Suppress("unused")
open class AcornUiApplicationPlugin : Plugin<Project> {

    internal val platforms = listOf("js", "jvm")

    override fun apply(project: Project) {
        project.pluginManager.apply("org.gradle.idea")
        project.pluginManager.apply("com.acornui.plugins.kotlin-mpp")

        project.extensions.create<AcornUiApplicationExtension>("acornui").apply {
            appResources = project.buildDir.resolve("processedResources")
            www = project.buildDir.resolve("www")
            wwwProd = project.buildDir.resolve("wwwProd")
        }
        project.extensions.configure(multiPlatformConfig(project))

        project.applicationResourceTasks(platforms, listOf("main"))
        project.appAssetsWebTasks()
        project.runJvmTask()

        project.tasks.named<Delete>("clean") {
            doLast {
                delete(project.acornui.www)
                delete(project.acornui.wwwProd)
            }
        }
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
            all {
                languageSettings.progressiveMode = true
            }

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

private fun Project.runJvmTask() {
    val jvmArgs: String? by extra
    tasks.register<JavaExec>("runJvm") {
        dependsOn("assembleJvm")
        group = "application"
        val jvmTarget: KotlinTarget = kotlinExt.targets["jvm"]
        val compilation =
            jvmTarget.compilations["main"] as KotlinCompilationToRunnableFiles<KotlinCommonOptions>

        val classes = files(
            compilation.runtimeDependencyFiles,
            compilation.output.allOutputs
        )
        classpath = classes
        workingDir = acornui.appResources.resolve("jvm/allMain")
        main =
            "${rootProject.group}.${rootProject.name}.jvm.${rootProject.name.toCamelCase().capitalize()}JvmKt"

        @Suppress("INACCESSIBLE_TYPE")
        this.jvmArgs = (jvmArgs?.split(" ") ?: listOf(
            "-ea",
            "-Ddebug=true"
        )) + if (OperatingSystem.current() == OperatingSystem.MAC_OS) listOf("-XstartOnFirstThread") else emptyList()
    }
}

open class AcornUiApplicationExtension {

    lateinit var appResources: File
    lateinit var www: File
    lateinit var wwwProd: File

    /**
     * The directory to place the .js files.
     * Relative to the [www] directory
     */
    var jsLibDir = "lib"
}

fun Project.acornui(init: AcornUiApplicationExtension.() -> Unit) {
    the<AcornUiApplicationExtension>().apply(init)
}

val Project.acornui
    get() : AcornUiApplicationExtension {
        return the()
    }