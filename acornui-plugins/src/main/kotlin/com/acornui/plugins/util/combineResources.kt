package com.acornui.plugins.util

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.get
import java.io.File

/**
 * @param platform "js" or "jvm"
 * @param compilationName "main" or "test"
 */
fun Project.combineResources(platform: String, destination: File, compilationName: String = "main") {
    val configNames = listOf("${platform}MainImplementation", "commonMainImplementation")
    val configs = configNames.map { configurations[it] }
    sync {
        into(destination)

        // Get all dependent projects, including this one. (This allows for the potential of having multiple app
        // projects per build.
        val projects = mutableListOf(this@combineResources)
        configs.forEach {
            it.allDependencies.filterIsInstance<ProjectDependency>().forEach { p ->
                projects.add(p.dependencyProject)
            }
        }
        projects.forEach { dependency ->
            val mainCompilation = dependency.kotlinExt.targets.named(platform).get().compilations.named(compilationName).get()
            mainCompilation.allKotlinSourceSets.forEach {
                from(it.resources.sourceDirectories.files)
            }
        }
    }
}