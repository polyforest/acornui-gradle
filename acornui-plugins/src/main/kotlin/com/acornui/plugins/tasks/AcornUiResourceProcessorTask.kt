@file:Suppress("UnstableApiUsage")

package com.acornui.plugins.tasks

import com.acornui.plugins.util.packAssets
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileType
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

open class AcornUiResourceProcessorTask @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val inputs = objects.fileCollection()

    /**
     * Folders ending in this suffix will be packed.
     * Set to null for no packing.
     */
    @get:Input
    var unpackedSuffix = "_unpacked"

    @get:OutputDirectory
    val outputDir = objects.directoryProperty()

    private val packedExtensions = arrayOf("json", "png")

    @TaskAction
    fun execute(inputChanges: InputChanges) {

        val unpackedSuffix = unpackedSuffix
        val directoriesToPack = mutableSetOf<Pair<File, File>>() // List of source folder to pack destination.

        inputChanges.getFileChanges(inputs).forEach { change ->
            val relPath = change.normalizedPath
            if (relPath.isEmpty()) return@forEach
            val sourceFile = change.file
            val targetFile = outputDir.file(relPath).get().asFile
            if (change.changeType == ChangeType.REMOVED) {
                if (targetFile.exists())
                    if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()

                if (sourceFile.isDirectory && sourceFile.name.endsWith(unpackedSuffix)) {
                    val name = sourceFile.name.removeSuffix(unpackedSuffix)
                    targetFile.parentFile.listFiles()?.forEach {
                        if (it.name.startsWith(name) && packedExtensions.contains(it.extension.toLowerCase()))
                            it.delete()
                    }
                }
            } else {
                if (sourceFile.parentFile.name.endsWith(unpackedSuffix)) {
                    directoriesToPack.add(sourceFile.parentFile to targetFile.parentFile.parentFile)
                } else {
                    if (change.fileType != FileType.DIRECTORY) {
                        sourceFile.parentFile.mkdirs()
                        sourceFile.copyTo(targetFile, overwrite = true)
                    }
                }
            }
        }

        directoriesToPack.forEach { (srcDir, destDir) ->
            logger.lifecycle("Packing assets: " + srcDir.path)
            packAssets(srcDir, destDir, unpackedSuffix)
        }
    }
}