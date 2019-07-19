package com.acornui.plugins.tasks

import com.acornui.plugins.acornui
import com.acornui.plugins.logging.BasicMessageCollector
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.cli.js.dce.K2JSDce
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.gradle.tasks.throwGradleExceptionIfError
import java.io.File

open class DceTask : DefaultTask() {

    val keep: MutableList<String> = ArrayList()

    fun keep(vararg fqn: String) {
        keep.addAll(fqn)
    }

    @TaskAction
    fun executeTask() {
        val destination = project.acornui.wwwProd

        val assembleWebDir = project.acornui.www
        val folder = File(destination, project.acornui.jsLibDir)
        val inputFiles: Sequence<File> = folder.listFiles()!!.asSequence()
            .filter { !it.isDirectory }
            .filter { it.name.endsWith(".js") }
            .filter { assembleWebDir.resolve(it.toRelativeString(destination) + ".map").exists() }

        logger.info("Dead Code Elimination on files: " + inputFiles.map { it.absolutePath }.toList())
        val dce = K2JSDce()
        val args = dce.createArguments().apply {
            declarationsToKeep = keep.toTypedArray()
            outputDirectory = folder.absolutePath
            freeArgs = inputFiles.map { it.absolutePath }.toList()
            devMode = false
            printReachabilityInfo = false
        }
        val exitCode = dce.exec(BasicMessageCollector(logger), Services.EMPTY, args)
        throwGradleExceptionIfError(exitCode)
    }
}