package com.acornui.plugins.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

open class AssembleWebProdTask : DefaultTask() {

    lateinit var destination: File

    @TaskAction
    fun executeTask() {
        project.sync {
            val assembleWeb = project.tasks.named("assembleWeb").get() as AssembleWebTask
            from(assembleWeb.destination)
            exclude("**/*.js.map")
            into(destination)
        }
    }
}