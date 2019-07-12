package com.acornui.plugins

import com.acornui.plugins.util.LoggerAdapter
import com.acornui.plugins.util.preventSnapshotDependencyCaching
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.repositories

@Suppress("unused")
class RootPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        // Configure the acorn logger to log to Gradle.
        LoggerAdapter.configure(target.logger)

        with(target) {
            val acornUiHome: String? by extra
            val acornVersion: String by extra
            val isComposite = acornUiHome != null && file(acornUiHome!!).exists()
            val r = this
            logger.lifecycle("isComposite $isComposite")

            preventSnapshotDependencyCaching()

            allprojects {
                apply {
                    plugin("org.gradle.idea")
                }

                repositories {
                    mavenLocal()
                    jcenter()

                    maven {
                        url = uri("https://raw.githubusercontent.com/polyforest/acornui-gradle-plugin/repository")
                    }
                }


                configurations.all {
                    resolutionStrategy {
                        eachDependency {
                            when {
                                requested.group == "com.acornui" -> {
                                    useVersion(acornVersion)
                                }
                            }
                        }
                    }
                }

                if (isComposite) {
                    configurations.all {
                        resolutionStrategy.dependencySubstitution {
                            listOf("utils", "core", "game", "spine", "test-utils").forEach {
                                val id = ":acornui:acornui-$it"
                                r.project(id) {
                                    group = "com.acornui"
                                    version = acornVersion
                                }
                                if (findProject(id) != null) {
                                    substitute(module("com.acornui:acornui-$it")).with(project(":acornui:acornui-$it"))
                                }
                            }
                            listOf("lwjgl", "webgl").forEach {
                                val id = ":acornui:backends:acornui-$it-backend"
                                r.project(id) {
                                    group = "com.acornui"
                                    version = acornVersion
                                }
                                if (findProject(id) != null)
                                    substitute(module("com.acornui:acornui-$it-backend")).with(project(id))
                            }
                        }
                    }
                }
            }
        }
    }
}