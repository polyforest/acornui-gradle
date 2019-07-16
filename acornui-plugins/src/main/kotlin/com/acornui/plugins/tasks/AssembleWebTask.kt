package com.acornui.plugins.tasks

import com.acornui.plugins.util.kotlinExt
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import java.io.File

// https://kotlinlang.slack.com/archives/C3PQML5NU/p1548431040452500?thread_ts=1548348039.408400&cid=C3PQML5NU
// https://kotlinlang.org/docs/tutorials/javascript/getting-started-gradle/getting-started-with-gradle.html

open class AssembleWebTask : DefaultTask() {

    lateinit var combinedResourcesDir: File
    lateinit var destination: File
    var libPath = "lib"

    /**
     * The following file patterns will be scanned for file.ext?version=%VERSION% matches, substituting %VERSION% with
     * the relative file's modified timestamp.
     */
    var replaceVersionStrPatterns = listOf("asp", "aspx", "cshtml", "cfm", "go", "jsp", "jspx", "php", "php3", "php4", "phtml", "html", "htm", "rhtml", "css").map { "*.$it" }

    private val versionMatch = Regex("""([\w./\\]+)(\?[\w=&]*)(%VERSION%)""")

    @TaskAction
    fun executeTask() {
        val jsMain = project.kotlinExt.targets["js"].compilations["main"] as KotlinJsCompilation

        project.sync {
            from(combinedResourcesDir)
            into(destination)

            filesMatching(replaceVersionStrPatterns) {
                filter {
                    line ->
                    replaceVersionWithModTime(line, combinedResourcesDir)
                }
            }
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
}