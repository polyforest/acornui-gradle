package com.acornui.plugins

import com.acornui.plugins.logging.LoggerAdapter
import com.acornui.plugins.util.preventSnapshotDependencyCaching
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.repositories

@Suppress("unused")
class RootPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.apply("org.gradle.idea")
        // Configure the acorn logger to log to Gradle.
        LoggerAdapter.configure(target.logger)

        with(target) {
            val acornUiHome: String? by extra
            val acornVersion: String by extra
            val isComposite = acornUiHome != null && file(acornUiHome!!).exists()
            val r = this
            logger.lifecycle("isComposite: $isComposite")

            preventSnapshotDependencyCaching()

            allprojects {
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
                                if (findProject(id) != null) {
                                    r.project(id) {
                                        group = "com.acornui"
                                        version = acornVersion
                                    }
                                    substitute(module("com.acornui:acornui-$it")).with(project(":acornui:acornui-$it"))
                                }
                            }
                            listOf("lwjgl", "webgl").forEach {
                                val id = ":acornui:backends:acornui-$it-backend"
                                if (findProject(id) != null) {
                                    r.project(id) {
                                        group = "com.acornui"
                                        version = acornVersion
                                    }
                                    substitute(module("com.acornui:acornui-$it-backend")).with(project(id))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}