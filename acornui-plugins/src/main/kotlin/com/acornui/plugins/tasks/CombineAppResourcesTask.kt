package com.acornui.plugins.tasks

import com.acornui.plugins.util.kotlinExt
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get
import java.io.File

/**
 * Copies all resource folders for dependent projects.
 */
open class CombineAppResourcesTask : DefaultTask() {

    lateinit var target: String // js, jvm
    lateinit var destination: File

    var compilationName: String = "main" // main, test

    @TaskAction
    fun executeTask() {
        val configNames = listOf("${target}MainImplementation", "commonMainImplementation")
        val configs = configNames.map { project.configurations[it] }
        project.sync {
            into(destination)

            // Get all dependent projects, including this one. (This allows for the potential of having multiple app
            // projects per build.
            val projects = mutableListOf(project)
            configs.forEach {
                it.allDependencies.filterIsInstance<ProjectDependency>().forEach { p ->
                    println("Found dep: ${p.dependencyProject}")
                    projects.add(p.dependencyProject)
                }
            }
            projects.forEach { dependency ->
                val mainCompilation = dependency.kotlinExt.targets.named(target).get().compilations.named(compilationName).get()
                mainCompilation.allKotlinSourceSets.forEach {
                    from(it.resources.sourceDirectories.files)
                }
            }
        }
    }
}