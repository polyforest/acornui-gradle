package com.acornui.plugins.tasks

import com.acornui.plugins.util.BasicMessageCollector
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
        val assembleWeb = (project.tasks.named("assembleWeb").get() as AssembleWebTask)
        val assembleWebProd = (project.tasks.named("assembleWebProd").get() as AssembleWebProdTask)
        val destination = assembleWebProd.destination

        val assembleWebDir = assembleWeb.destination
        val folder = File(destination, assembleWeb.libPath)
        val inputFiles: Sequence<File> = folder.listFiles()!!.asSequence()
            .filter { !it.isDirectory }
            .filter { it.name.endsWith(".js") }
            .filter { assembleWebDir.resolve(it.toRelativeString(destination) + ".map").exists() }


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