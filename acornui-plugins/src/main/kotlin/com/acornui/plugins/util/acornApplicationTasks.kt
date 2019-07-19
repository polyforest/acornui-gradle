package com.acornui.plugins.util

import com.acornui.io.file.FilesManifestSerializer
import com.acornui.io.file.ManifestUtil
import com.acornui.plugins.acornui
import com.acornui.plugins.tasks.AcornUiResourceProcessorTask
import com.acornui.plugins.tasks.DceTask
import com.acornui.plugins.tasks.KotlinJsMonkeyPatcherTask
import com.acornui.serialization.toJson
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinCompilationToRunnableFiles
import java.io.File

fun Project.applicationResourceTasks(platforms: Iterable<String>, compilations: Iterable<String>) {
    platforms.forEach { platform ->
        val platformCapitalized = platform.capitalize()
        compilations.forEach { compilationName ->
            val compilationNameCapitalized = compilationName.capitalize()
            val nameOrEmpty = if (compilationName == "main") "" else compilationNameCapitalized

            val configNames = listOf(
                "$platform${compilationNameCapitalized}Implementation",
                "common${compilationNameCapitalized}Implementation"
            )

            // Get all dependent projects, including this one. (This allows for the potential of having multiple app
            // projects per build.
            val configs = configNames.map { project.configurations[it] }
            val projects = mutableListOf(project)
            configs.forEach {
                it.allDependencies.filterIsInstance<ProjectDependency>().forEach { p ->
                    projects.add(p.dependencyProject)
                }
            }

            val allMainDir = project.buildDir.resolve("processedResources/$platform/all$compilationNameCapitalized")

            val processAcornResources = project.tasks.register<AcornUiResourceProcessorTask>("${platform}ProcessAcornResources") {
                outputDir.set(allMainDir)

                projects.forEach { dependency ->
                    val unconfiguredDepError = "Target platform \"$platform\" was not found for ${dependency.displayName}. Ensure that this dependency applies the plugin \"com.acornui.module\"."
                    val targetPlatform = dependency.kotlinExt.targets.named(platform).orNull ?: error(unconfiguredDepError)
                    val compilation = targetPlatform.compilations.named(compilationName).orNull ?: error(unconfiguredDepError)
                    compilation.allKotlinSourceSets.forEach {
                        inputs.from(it.resources.srcDirs)
                    }
                }
                finalizedBy("${platform}WriteResourcesManifest")
            }

            val writeManifest = tasks.register("${platform}WriteResourcesManifest") {
                dependsOn(processAcornResources)
                onlyIf {
                    processAcornResources.get().didWork
                }
                doLast {
                    val assetsDir = allMainDir.resolve("assets")
                    val manifest = ManifestUtil.createManifest(assetsDir, allMainDir)
                    assetsDir.resolve("files.json").writeText(
                        toJson(
                            manifest,
                            FilesManifestSerializer
                        )
                    )
                }
            }

            val processResources = tasks.named<ProcessResources>("$platform${nameOrEmpty}ProcessResources") {
                dependsOn(processAcornResources, writeManifest)
                exclude("*")
            }

            val assemblePlatform = tasks.register("assemble$platformCapitalized") {
                group = "build"
                dependsOn(processResources, "compileKotlin$platformCapitalized")
            }

            tasks.named("assemble") {
                dependsOn(assemblePlatform)
            }
        }
    }
}

fun Project.appAssetsWebTasks() {

    val assembleJs = tasks.named("assembleJs")

    // Register the assembleWeb task that builds the www directory.

    val assembleWeb = tasks.register<Sync>("assembleWeb") {
        dependsOn(assembleJs)
        group = "build"

        into(acornui.www)

        from(kotlinMppRuntimeDependencies(project, "js")) {
            include("*.js", "*.js.map")
            exclude("**/*.meta.js")
            into(acornui.jsLibDir)
        }

        val combinedResourcesDir = acornui.appResources.resolve("js/allMain")
        from(combinedResourcesDir) {
            filesMatching(replaceVersionStrPatterns) {
                filter { line ->
                    replaceVersionWithModTime(line, combinedResourcesDir)
                }
            }
        }

        doLast {
            File(acornui.www.resolve(acornui.jsLibDir), "files.js").writeText("var manifest = " + File(acornui.www, "assets/files.json").readText())
        }
    }

    tasks.named("assemble") {
        dependsOn(assembleWeb)
    }

    val assembleWebProd = tasks.register<Sync>("assembleWebProd") {
        dependsOn(assembleWeb)
        group = "build"

        from(acornui.www)
        exclude("**/*.js")
        exclude("**/*.js.map")
        into(acornui.wwwProd)

        finalizedBy("prodDce")
        finalizedBy("kotlinJsMonkeyPatch")
    }

    tasks.named("assemble") {
        dependsOn(assembleWebProd)
        onlyIf {
            assembleWebProd.get().didWork
        }
    }

    tasks.register<DceTask>("prodDce") {
        dependsOn(assembleWebProd)
    }

    tasks.register<KotlinJsMonkeyPatcherTask>("kotlinJsMonkeyPatch") {
        inputs.from(fileTree(acornui.www) {
            include("**/*.js")
        })
        outputDir.set(acornui.wwwProd)
    }

}

// https://kotlinlang.slack.com/archives/C3PQML5NU/p1548431040452500?thread_ts=1548348039.408400&cid=C3PQML5NU
// https://kotlinlang.org/docs/tutorials/javascript/getting-started-gradle/getting-started-with-gradle.html

/**
 * The following file patterns will be scanned for file.ext?version=%VERSION% matches, substituting %VERSION% with
 * the relative file's modified timestamp.
 */
var replaceVersionStrPatterns = listOf(
    "asp",
    "aspx",
    "cshtml",
    "cfm",
    "go",
    "jsp",
    "jspx",
    "php",
    "php3",
    "php4",
    "phtml",
    "html",
    "htm",
    "rhtml",
    "css"
).map { "*.$it" }

private val versionMatch = Regex("""([\w./\\]+)(\?[\w=&]*)(%VERSION%)""")

/**
 * Replaces %VERSION% tokens with the last modified timestamp.
 * The source must match the format:
 * foo/bar.ext?baz=%VERSION%
 * foo/bar.ext must be a local file.
 */
fun replaceVersionWithModTime(src: String, root: File): String {
    return versionMatch.replace(src) { match ->
        val path = match.groups[1]!!.value
        val relativeFile = root.resolve(path)
        if (relativeFile.exists()) path + match.groups[2]!!.value + relativeFile.lastModified()
        else path + match.groups[2]!!.value + System.currentTimeMillis()
    }
}

/**
 * Returns a file collection of all runtime dependencies.
 */
fun kotlinMppRuntimeDependencies(project: Project, platform: String, compilationName: String = "main"): FileCollection = project.files().apply {
    val main = project.kotlinExt.targets[platform].compilations[compilationName] as AbstractKotlinCompilationToRunnableFiles<*>
    main.output.classesDirs.forEach { folder ->
        from(project.fileTree(folder))
    }
    main.runtimeDependencyFiles.forEach { file ->
        from(project.zipTree(file.absolutePath))
    }
}