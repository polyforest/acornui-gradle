package com.acornui.plugins.utils

import org.gradle.api.artifacts.ExternalModuleDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

fun KotlinDependencyHandler.acorn(id: String): ExternalModuleDependency {

    return implementation("com.acornui:acornui-$id") {
        isChanging = true
//        version =
    }
}