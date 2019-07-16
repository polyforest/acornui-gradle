package com.acornui.plugins

import com.acornui.core.toCamelCase
import com.acornui.io.file.FilesManifestSerializer
import com.acornui.io.file.ManifestUtil
import com.acornui.plugins.tasks.*
import com.acornui.plugins.util.combineResources
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
        target.extensions.create<AcornUiApplicationExtension>("acornui").apply {
            appResources = target.buildDir.resolve("appResources")
            www = target.buildDir.resolve("www")
            wwwProd = target.buildDir.resolve("wwwProd")
        }
        target.extensions.configure(multiPlatformConfig(target))

        target.runJvmTask()
        target.appAssetCommonTasks()
        target.appAssetsWebTasks()

        target.tasks.named<Delete>("clean") {
            doLast {
                val assembleWeb = project.tasks.named("assembleWeb").get() as AssembleWebTask
                delete(assembleWeb.destination)
                val assembleWebProd = project.tasks.named("assembleWebProd").get() as AssembleWebProdTask
                delete(assembleWebProd.destination)
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

    private fun Project.appAssetCommonTasks() {
        val platforms = listOf("js", "jvm")
        platforms.forEach { platform ->
            val platformCapitalized = platform.capitalize()

            val processResources = tasks.named("${platform}ProcessResources")

            val processAppResources = tasks.register("${platform}ProcessAppResources") {
                dependsOn(processResources)
                onlyIf {
                    processResources.get().didWork
                }
                doLast {
                    val destination = acornui.appResources.resolve(platform)
                    combineResources(platform, destination)

                    // Pack the assets in all directories in the dest folder with a name ending in "_unpacked"
                    TexturePackerUtil.packAssets(destination, File("."), quiet = true)

                    val assetsDir = File(destination, "assets")
                    val manifest = ManifestUtil.createManifest(assetsDir, destination)
                    File(assetsDir, "files.json").writeText(json.write(manifest, FilesManifestSerializer))
                }
            }

            tasks.named("${platform}ProcessResources") {
                finalizedBy(processAppResources)
            }

            val assemblePlatform = tasks.register("assemble$platformCapitalized") {
                group = "build"
                dependsOn("${platform}ProcessResources", "compileKotlin$platformCapitalized")
            }

            tasks.named("assemble") {
                dependsOn(assemblePlatform)
            }
        }
    }

    private fun Project.appAssetsWebTasks() {

        val assembleJs = tasks.named("assembleJs")

        // Register the assembleWeb task that builds the www directory.
        val assembleWeb = tasks.register<AssembleWebTask>("assembleWeb") {
            dependsOn(assembleJs)

            combinedResourcesDir = acornui.appResources.resolve("js")
            destination = acornui.www
        }
        tasks.named("assemble") {
            dependsOn(assembleWeb)
        }

        val assembleWebProd = tasks.register<AssembleWebProdTask>("assembleWebProd") {
            dependsOn(assembleWeb)
            group = "build"
            destination = acornui.wwwProd
            finalizedBy("prodDce")
            finalizedBy("kotlinJsMonkeyPatch")
        }
        tasks.named("assemble") {
            dependsOn(assembleWebProd)
        }
        tasks.register<DceTask>("prodDce")
        tasks.register<KotlinJsMonkeyPatcherTask>("kotlinJsMonkeyPatch") {
            source(assembleWebProd.get().destination)
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
            workingDir = acornui.appResources.resolve("jvm")
            main =
                "${rootProject.group}.${rootProject.name}.jvm.${rootProject.name.toCamelCase().capitalize()}JvmKt"

            @Suppress("INACCESSIBLE_TYPE")
            this.jvmArgs = (jvmArgs?.split(" ") ?: listOf(
                "-ea",
                "-Ddebug=true"
            )) + if (OperatingSystem.current() == OperatingSystem.MAC_OS) listOf("-XstartOnFirstThread") else emptyList()
        }
    }
}

open class AcornUiApplicationExtension {
    lateinit var appResources: File
    lateinit var www: File
    lateinit var wwwProd: File
}

fun Project.acornui(init: AcornUiApplicationExtension.() -> Unit) {
    the<AcornUiApplicationExtension>().apply(init)
}

val Project.acornui
    get() : AcornUiApplicationExtension {
        return the()
    }