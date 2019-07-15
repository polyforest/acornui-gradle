package com.acornui.plugins.tasks

import com.acornui.plugins.util.kotlinExt
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import java.io.File

// https://kotlinlang.slack.com/archives/C3PQML5NU/p1548431040452500?thread_ts=1548348039.408400&cid=C3PQML5NU
// https://kotlinlang.org/docs/tutorials/javascript/getting-started-gradle/getting-started-with-gradle.html

open class AssembleWebTask : DefaultTask() {

    var destination: File = project.buildDir.resolve("www")
    var libPath = "lib"

    @TaskAction
    fun executeTask() {
        val jsMain = project.kotlinExt.targets["js"].compilations["main"] as KotlinJsCompilation
        project.sync {
            from(jsMain.output.resourcesDir)
            into(destination)
        }

        jsMain.output.classesDirs.forEach { folder ->
            project.copy {
                from(folder)
                include("*.js", "*.js.map")
                exclude("**/*.meta.js")
                into(File(destination, "lib"))
            }
        }

        jsMain.runtimeDependencyFiles.forEach { file ->
            project.copy {
                from(project.zipTree(file.absolutePath)) {
                    includeEmptyDirs = false
                    include { fileTreeElement ->
                        val path = fileTreeElement.path
                        (path.endsWith(".js") || path.endsWith(".js.map")) && !path.endsWith(".meta.js") &&
                                (path.startsWith("META-INF/resources/") || !path.startsWith("META-INF/"))
                    }
                }
                into(File(destination, libPath))
            }
        }

        File(destination, "lib/files.js").writeText("var manifest = " + File(destination, "assets/files.json").readText())
    }
}