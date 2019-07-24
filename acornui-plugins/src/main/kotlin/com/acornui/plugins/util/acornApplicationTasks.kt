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

            val projectDependencies: List<Project> by lazy {
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
                projects
            }


            val unprocessedResourcesAllMain = project.buildDir.resolve("unprocessedResources/$platform/all$compilationNameCapitalized")
            val processedResourcesAllMain = project.buildDir.resolve("processedResources/$platform/all$compilationNameCapitalized")

            val combineAcornResources =
                project.tasks.register<Sync>("${platform}CombineAcornResources") {
                    into(unprocessedResourcesAllMain)
                    projectDependencies.forEach { dependency ->
                        val unconfiguredDepError = "Target platform \"$platform\" was not found for ${dependency.displayName}. Ensure that this dependency applies the plugin \"com.acornui.module\"."
                        val targetPlatform = dependency.kotlinExt.targets.named(platform).orNull ?: error(unconfiguredDepError)
                        val compilation = (targetPlatform.compilations.named(compilationName).orNull ?: error(unconfiguredDepError)) as AbstractKotlinCompilationToRunnableFiles<*>
                        compilation.allKotlinSourceSets.forEach {
                            from(it.resources.srcDirs)
                        }
                        compilation.runtimeDependencyFiles.forEach { file ->
                            from(project.zipTree(file).matching {
                                include("assets/**")
                            })
                        }
                    }
                }

            val processAcornResources =
                project.tasks.register<AcornUiResourceProcessorTask>("${platform}ProcessAcornResources") {
                    dependsOn(combineAcornResources)
                    from(unprocessedResourcesAllMain)
                    into(processedResourcesAllMain)

                    finalizedBy("${platform}WriteResourcesManifest")
                }

            val writeManifest = tasks.register("${platform}WriteResourcesManifest") {
                dependsOn(processAcornResources)
                onlyIfDidWork(processAcornResources)
                doLast {
                    val assetsDir = processedResourcesAllMain.resolve("assets")
                    if (assetsDir.exists()) {
                        val manifest = ManifestUtil.createManifest(assetsDir, processedResourcesAllMain)
                        assetsDir.resolve("files.json").writeText(
                            toJson(
                                manifest,
                                FilesManifestSerializer
                            )
                        )
                    }
                }
            }

            val processResources = tasks.named<ProcessResources>("$platform${nameOrEmpty}ProcessResources") {
                dependsOn(processAcornResources, writeManifest)
                exclude("*")
            }

            val assemblePlatform = tasks.register("${platform}Assemble") {
                group = "build"
                dependsOn(processResources, "compileKotlin$platformCapitalized")
            }

            tasks.named("assemble") {
                dependsOn(assemblePlatform)
            }
        }
    }
}

private fun Sync.addCombinedJsResources(project: Project) {
    val combinedResourcesDir = project.acornui.appResources.resolve("js/allMain")
    from(combinedResourcesDir) {
        filesMatching(replaceVersionStrPatterns) {
            filter { line ->
                replaceVersionWithModTime(line, combinedResourcesDir)
            }
        }
    }
}

fun Project.appAssetsWebTasks() {

    val assembleJs = tasks.named("jsAssemble")

    // Register the assembleWeb task that builds the www directory.

    val webAssemble = tasks.register<Sync>("webAssemble") {
        dependsOn(assembleJs)
        group = "build"

        into(acornui.www)

        from(kotlinMppRuntimeDependencies(project, "js")) {
            include("*.js", "*.js.map")
            into(acornui.jsLibPath)
        }

        addCombinedJsResources(project)

        doLast {
            File(acornui.www.resolve(acornui.jsLibPath), "files.js").writeText(
                "var manifest = " + File(
                    acornui.www,
                    "assets/files.json"
                ).readText()
            )
        }
    }

    val webProdAssemble = tasks.register<Sync>("webProdAssemble") {
        dependsOn(assembleJs)
        group = "build"

        into(acornui.wwwProd)
        addCombinedJsResources(project)

        finalizedBy("jsDce", "jsOptimize")

        doLast {
            File(
                acornui.wwwProd.resolve(acornui.jsLibPath),
                "files.js"
            ).writeText("var manifest = " + File(acornui.wwwProd, "assets/files.json").readText())
        }
    }

    tasks.named("assemble") {
        dependsOn(webAssemble, webProdAssemble)
    }

    val jsDce = tasks.register<DceTask>("jsDce") {
        val prodLibDir = acornui.wwwProd.resolve(acornui.jsLibPath)
        source.from(kotlinMppRuntimeDependencies(project, "js"))
        outputDir.set(prodLibDir)

        doLast {
            project.delete(fileTree(prodLibDir).include("*.map"))
        }
    }

    tasks.register<KotlinJsMonkeyPatcherTask>("jsOptimize") {
        shouldRunAfter(jsDce)
        sourceDir.set(acornui.wwwProd.resolve(acornui.jsLibPath))
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
fun kotlinMppRuntimeDependencies(project: Project, platform: String, compilationName: String = "main") =
    project.files().apply {
        builtBy("$platform${compilationName.capitalize()}Classes")
        val main =
            project.kotlinExt.targets[platform].compilations[compilationName] as AbstractKotlinCompilationToRunnableFiles<*>
        main.output.classesDirs.forEach { folder ->
            from(project.fileTree(folder))
        }
        main.runtimeDependencyFiles.forEach { file ->
            from(project.zipTree(file))
        }
    }