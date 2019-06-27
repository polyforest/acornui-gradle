/*
 * Copyright 2019 Poly Forest, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.polyforest.acornui

import com.polyforest.acornui.build.*
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinJsDce
import java.awt.Desktop
import java.net.URI

/**
 * Plugin:  com.polyforest.acornui.app
 * Used in Acorn UI consumer projects for *app*-kotlin multiplatform modules.
 * JS compilation main defaults to "call"
 */

plugins {
	id("com.polyforest.acornui.app-basic")
	id("kotlin-dce-js")
}

val acorn = AUI(project)

kotlin {
	js {
		compilations.all {
			kotlinOptions {
				main = "call"
			}
		}
	}

	sourceSets {
		commonMain {
			dependencies {
				implementation(acornui("core-metadata"))
				implementation(acornui("lwjgl-backend-metadata"))
				implementation(acornui("webgl-backend-metadata"))
				implementation(acornui("utils-metadata"))
			}
		}
		named("jvmMain") {
			dependencies {
				implementation(acornui("core-jvm"))
				implementation(acornui("lwjgl-backend-jvm"))
				implementation(acornui("webgl-backend-jvm"))
				implementation(acornui("utils-jvm"))

				val LWJGL_VERSION by acorn.defaults
				val lwjglGroup = "org.lwjgl"
				val lwjglName = "lwjgl"
				val natives = arrayOf("windows", "macos", "linux")
				val extensions = arrayOf("glfw", "jemalloc", "opengl", "openal", "stb", "nfd", "tinyfd")

				implementation("$lwjglGroup:$lwjglName:$LWJGL_VERSION")
				extensions.forEach { implementation("$lwjglGroup:$lwjglName-$it:$LWJGL_VERSION") }

				for (native in natives) {
					runtimeOnly("$lwjglGroup:$lwjglName:$LWJGL_VERSION:natives-$native")
					extensions.forEach { runtimeOnly("$lwjglGroup:$lwjglName-$it:$LWJGL_VERSION:natives-$native") }
				}
			}
		}
		named("jsMain") {
			dependencies {
				implementation(acornui("core-js"))
				implementation(acornui("lwjgl-backend-js"))
				implementation(acornui("webgl-backend-js"))
				implementation(acornui("utils-js"))
			}
		}
	}
}

/**
 * Get the root destination directory provider of all Acorn resource & source (JS) processing for the given [target]
 *
 * @param target required platform disambiguator (e.g. "jvm").
 * @param subdirectory optional subdirectory within the destination root (e.g. File("a/relative/path"))
 * @return a [Provider] that will return the [target] [Directory] when queried.  When [subdirectory] is not null, the
 * [subdirectory] of the [target] [Directory] will be returned when queried.
 */
fun Project.getAllOutDir(target: String, subdirectory: File? = null): Provider<Directory> {
	val relativeBase = File("processedResources/$target/allMain")
	val outSubPath = subdirectory?.let { relativeBase.resolve(it) } ?: relativeBase
	return layout.buildDirectory.dir(outSubPath.path)
}

val aggregatedUnprocessedRelDir = File("unprocessed")
val allOutUnprocessedJsDir =
		getAllOutDir(AppTargetFacade.DEFAULT_JS_TARGET_APP_TARGET_NAME, aggregatedUnprocessedRelDir)
val allOutUnprocessedJvmDir =
		getAllOutDir(AppTargetFacade.DEFAULT_JVM_TARGET_APP_TARGET_NAME, aggregatedUnprocessedRelDir)
val jsLibsRelDir = File("lib")
val assetsRelDir = File("assets")
// Destination root for js build variations hosted by the IDE's built-in web server
val webFolderRoot = projectDir
val webFolderStem = "www"
val webFolder = webFolderRoot.resolve(webFolderStem)
val webDistFolderStem = "wwwDist"
val webDistFolder = webFolderRoot.resolve(webDistFolderStem)
val ghDocsFolder = file("docs")
val targetCompilations = { target: String -> kotlin.targets[target].compilations }

// Setup basic skin directory as resource directory...
maybeAddBasicResourcesAsResourceDir(project)

tasks {

	// Helpers
	// URI Helpers
	// Handles null, blank, empty, and formatting
	fun fmt(part: Any? = null, prefix: String = ""): String = part?.let {
		fmt(prefix) + part.toString().trim()
	} ?: ""

	fun getIntellijBuiltInServerUri(project: Project) =
			URI(
					"http",
					null,
					"localhost",
					63342,
					"/",
					null,
					null
			).resolve("${project.rootDir.name}/")

	fun getLocalAppServerUri(project: Project, type: String, path: File? = null, query: String? = null): URI {
		val webRoot = if (type == "dev") webFolder else webDistFolder
		val defaultQuery = "debug=true"
		val fullQuery =
				when {
					query != null && type == "dev" -> "$query&$defaultQuery"
					query == null && type == "dev" -> defaultQuery
					else -> query
				}

		val entryPoint = path?.let { webRoot.resolve(it) } ?: webRoot.resolve("index.html")

		val relativeURI = URI(
				null,
				null,
				entryPoint.relativeTo(project.rootDir).path,
				fullQuery,
				null
		)

		return getIntellijBuiltInServerUri(project).resolve(relativeURI)
	}

	val devURI by lazy { getLocalAppServerUri(project = project, type = "dev") }
	val prodURI by lazy { getLocalAppServerUri(project = project, type = "prod") }

	val auiBuildTasksTool by lazy {
		BuildTool(
				project,
				"auiBuildTasksTool",
				"com.acornui.build.gradle.BuildUtilKt",
				listOf(acornuiDependencyNotation("build-tasks-jvm"))
		)
	}


	// Task Configuration Helpers
	fun Copy.configureCommonStageTargetOutputForProcessingTask(
			target: String,
			type: String,
			destRelSubDirectory: File = File(type)
	) {
		document("Stage commonly processed $target output for $type specific processing")
		into(getAllOutDir(target, destRelSubDirectory))
	}

	fun JavaExec.configureCommonPackAssetsTask(targetName: String, sourceDir: File) {
		document("Pack assets for $targetName")
		dependsOn(auiBuildTasksTool.configuration)

		inputs.dir(sourceDir).withPropertyName("src")
		outputs.dir(sourceDir).withPropertyName("outputFiles")

		args = listOf(
				"-target=assets",
				"-src=${sourceDir.canonicalPath}"
		)
		main = auiBuildTasksTool.main
		classpath = auiBuildTasksTool.configuration
	}

	fun JavaExec.configureCommonWriteResourceManifestFileTask(targetName: String, resourcesRootDir: File) {
		document("Generate assets/file.json manifest of $targetName target's resources")

		val srcDir = resourcesRootDir.resolve(assetsRelDir)
		with(inputs) {
			// Directory path from which relative paths to resources are generated.
			property("root", resourcesRootDir)
			dir(srcDir).withPropertyName("src")
		}
		outputs.file("$srcDir/files.json")

		args = listOf(
				"-target=asset-manifest",
				"-src=${srcDir.canonicalPath}",
				"-root=${resourcesRootDir.canonicalPath}"
		)
		this.main = auiBuildTasksTool.main
		classpath = auiBuildTasksTool.configuration
	}

	fun JavaExec.configureCommonCreateJsFileManifestTask(srcRoot: File, destinationRoot: File = srcRoot) {
		val dest = destinationRoot.resolve(jsLibsRelDir)

		document("Generate a file manifest of js libs for file handling at runtime.")
		dependsOn(auiBuildTasksTool.configuration)

		with(inputs) {
			// Directory path containing resources to be targeted by the manifest.
			dir(srcRoot).withPropertyName("src")
			// Directory path from which relative paths to resources are generated.
			property("root", destinationRoot)
			property("dest", dest)
		}
		outputs.dir(destinationRoot).withPropertyName("outputFiles")

		args = listOf(
				"-target=lib-manifest",
				"-src=${srcRoot.canonicalPath}",
				"-dest=${dest.canonicalPath}",
				"-root=${destinationRoot.canonicalPath}"
		)
		main = auiBuildTasksTool.main
		classpath = auiBuildTasksTool.configuration
	}

	fun SourceTask.configureCommonBustHtmlSourceScriptCacheTask() {
		document("Modify ${project.name} html sources to bust browser cache.")

		include { fileTreeElement ->
			!fileTreeElement.isDirectory && ScriptCacheBuster.extensions.any { extension ->
				fileTreeElement.name.endsWith(extension)
			}
		}

		doLast {
			val manipulator = SourceFileManipulator()
			manipulator.addProcessor(
					ScriptCacheBuster::replaceVersionWithModTime,
					*ScriptCacheBuster.extensions.toTypedArray()
			)
			source.forEach { manipulator.process(it) }
		}
	}

	/**
	 * Locally deploy js runtime files ([type]) to IDE's built-in web server [type] root
	 *
	 * @param type the type of build being targeted, e.g. "dev" or "prod"
	 */
	fun Copy.configureCommonPopulateWebDirectoryTask(type: String) {
		document(
				"Locally deploy js runtime files ($type) to IDE's built-in web server $type root",
				"build"
		)
	}

	fun DefaultTask.configureCommonRunJsTask(type: String, uri: URI) {
		document("Run js $type app in system's default browser.", ApplicationPlugin.APPLICATION_GROUP)

		doLast {
			val desktop = Desktop.getDesktop()
			if (desktop.isSupported(Desktop.Action.BROWSE)) {
				desktop.browse(uri)
			} else {
				logger.warn(
						"""
					Cannot open browser to: ${uri.toASCIIString()}
					The build environment OS does not support the Java Desktop API.
				""".trimIndent()
				)
			}
		}
	}


	withType<KotlinJsDce> {
		// Disable JS DCE for all test compilations
		if (name.contains("TestKotlin")) {
			dceOptions.devMode = true
		}
	}

	// Task declarations
	// Js - Shared Tasks (dev+prod)
	val jsMainClasses by existing
	val assembleJsOutput by registering(Copy::class) {
		document("Gather js source and resources cross module.")
		dependsOn(jsMainClasses)
		validateAcornUiHomeToUseBasicAssets()

		val targetName = AppTargetFacade.DEFAULT_JS_TARGET_APP_TARGET_NAME
		val target = AppTargetFacade(project, kotlin, targetName)

		from(target.mpXResources)
		into(allOutUnprocessedJsDir)
		exclude("**/*.meta.js", "**/*.kjsm")
		includeEmptyDirs = false

		into(jsLibsRelDir) {
			from(target.mpXSources)
			include("**/*.js", "**/*.js.map")
		}
	}

	fun <T : Task> T.onlyIfJsOutputChanged() {
		onlyIf {
			assembleJsOutput.get().didWork
		}
	}

	val runDceJsKotlin by getting(KotlinJsDce::class)
	fun <T : Task> T.onlyIfProdJsOutputChanged() {
		onlyIfJsOutputChanged()
		onlyIf {
			runDceJsKotlin.didWork
		}
	}

	// TODO: Refactor so stageJsOutputForProcessing ("dev") and stageJvmOutputForProcessing integrate their
	//       assembleJsOutput and assembleJvmOutput respectively
	// -requires checking that inputs are up to date as normal
	// -requires checking that there have been no changes in terminating task (populateWebFolder & populateJvmResources)
	//  outputs when checking if staging task outputs are up-to-date, so as to detect additions or deletions in the
	//  target directory.
	//    or
	//  -ignore checking outputs
	val stageJsOutputForProcessing by registering(Copy::class) {
		from(assembleJsOutput)
		configureCommonStageTargetOutputForProcessingTask(target = "js", type = "dev")
		onlyIfJsOutputChanged()
	}

	fun getJsOutputStagingDir() = stageJsOutputForProcessing.get().destinationDir

	val packJsAssets by registering(JavaExec::class) {
		dependsOn(stageJsOutputForProcessing)
		onlyIfJsOutputChanged()

		configureCommonPackAssetsTask("js", getJsOutputStagingDir())
	}

	val writeJsResourceManifestFile by registering(JavaExec::class) {
		dependsOn(packJsAssets)
		onlyIfJsOutputChanged()

		configureCommonWriteResourceManifestFileTask("js", getJsOutputStagingDir())
	}

	// Js - Dev Tasks
	val createJsFileManifest by registering(JavaExec::class) {
		dependsOn(writeJsResourceManifestFile)
		onlyIfJsOutputChanged()

		configureCommonCreateJsFileManifestTask(getJsOutputStagingDir())
	}

	val bustHtmlSourceScriptCache by registering(SourceTask::class) {
		dependsOn(stageJsOutputForProcessing, createJsFileManifest)
		onlyIfJsOutputChanged()

		source(getJsOutputStagingDir())
		configureCommonBustHtmlSourceScriptCacheTask()
	}

	val populateWebDirectory by registering(Copy::class) {
		dependsOn(bustHtmlSourceScriptCache)

		from(getJsOutputStagingDir())
		into(webFolder)
		configureCommonPopulateWebDirectoryTask("dev")
	}

	val runJs by registering(DefaultTask::class) {
		dependsOn(populateWebDirectory)

		val type = "dev"
		configureCommonRunJsTask(type, getLocalAppServerUri(project = project, type = type))
	}

	// Js - Integrate into existing pipeline
	val jsJar by existing {
		dependsOn(populateWebDirectory)
	}

	val clean by existing(Delete::class) {
		description = "Deletes build folder (\"build/\"), JS dev output (\"$webFolderStem/\"), " +
				"& JS prod output (\"$webDistFolderStem\")."
		delete(webFolder, webDistFolder)
	}

	val build by getting
	build.dependsOn.removeIf { it is KotlinJsDce }

	// Js - Prod tasks
	val stageJsOutputForProcessingProd by registering(Copy::class) {
		dependsOn(writeJsResourceManifestFile)

		from(getJsOutputStagingDir())
		exclude("**/*.js.map")
		configureCommonStageTargetOutputForProcessingTask(target = "js", type = "prod")

		into(jsLibsRelDir) {
			from(runDceJsKotlin)
		}
	}

	fun getJsProdOutputStagingDir() = stageJsOutputForProcessingProd.get().destinationDir

	val optimizeJs by registering(SourceTask::class) {
		document("Monkeypatch js code with optimizations.")
		source(stageJsOutputForProcessingProd)
		include("**/*.js")

		doLast {
			val manipulator = SourceFileManipulator()
			manipulator.addProcessor(KotlinMonkeyPatcher::optimizeProductionCode, "js")
			source.forEach { manipulator.process(it) }
		}
	}

	val minifyJs by registering(SourceTask::class) {
		document("Minify js.")
		dependsOn(stageJsOutputForProcessingProd)

		enabled = false

		source(getJsProdOutputStagingDir())
		include("**/*.js")

		doLast {
			// TODO: Finish processing portion - minify (check out uglify in acornbuild - _mergeAssets
		}
	}

	val createProdJsFileManifest by registering(JavaExec::class) {
		dependsOn(optimizeJs)
		configureCommonCreateJsFileManifestTask(getJsProdOutputStagingDir())
	}

	val bustProdHtmlSourceScriptCache by registering(SourceTask::class) {
		dependsOn(stageJsOutputForProcessingProd, createProdJsFileManifest)

		source(getJsProdOutputStagingDir())
		configureCommonBustHtmlSourceScriptCacheTask()
	}

	val populateProdWebDirectory by registering(Copy::class) {
		dependsOn(bustProdHtmlSourceScriptCache)

		configureCommonPopulateWebDirectoryTask("prod")
		from(getJsProdOutputStagingDir())
		into(webDistFolder)
	}

	val runJsProd by registering(DefaultTask::class) {
		dependsOn(populateProdWebDirectory)

		val type = "prod"
		configureCommonRunJsTask(type, getLocalAppServerUri(project = project, type = type))
	}

	// Jvm - Shared Tasks (dev+prod)
	val jvmMainClasses by existing
	val assembleJvmOutput by registering(Copy::class) {
		document("Gather jvm resources cross module.")
		validateAcornUiHomeToUseBasicAssets()

		val target = AppTargetFacade(project, kotlin, AppTargetFacade.DEFAULT_JVM_TARGET_APP_TARGET_NAME)

		from(target.mpXResources)
		into(allOutUnprocessedJvmDir)
		includeEmptyDirs = false
	}

	fun <T : Task> T.onlyIfJvmOutputChanged() {
		onlyIf {
			assembleJvmOutput.get().didWork
		}
	}

	val stageJvmOutputForProcessing by registering(Copy::class) {
		from(assembleJvmOutput)
		configureCommonStageTargetOutputForProcessingTask(target = "jvm", type = "dev")
		onlyIfJvmOutputChanged()
	}

	fun getJvmOutputStagingDir() = stageJvmOutputForProcessing.get().destinationDir

	val packJvmAssets by registering(JavaExec::class) {
		dependsOn(stageJvmOutputForProcessing)
		onlyIfJvmOutputChanged()

		configureCommonPackAssetsTask("jvm", getJvmOutputStagingDir())
	}

	val writeJvmResourceManifestFile by registering(JavaExec::class) {
		dependsOn(packJvmAssets)
		onlyIfJvmOutputChanged()

		configureCommonWriteResourceManifestFileTask("js", getJvmOutputStagingDir())
	}

	val populateJvmResources by registering {
		document(
				"Gather and process jvm resources to application working directory.  Does not do any work " +
						"itself and simply calls the necessary tasks.",
				"build"
		)
		dependsOn(writeJvmResourceManifestFile)
	}

	// Jvm - Integrate into existing pipeline
	val jvmJar by existing {
		dependsOn(populateJvmResources)
	}

	val jvmRuntimeCpConfigName = "jvm${JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME.capitalize()}"
	val runJvm by registering(JavaExec::class) {
		document("Run jvm app.", ApplicationPlugin.APPLICATION_GROUP)
		dependsOn(jvmMainClasses, populateJvmResources)

		val JVM_MAIN by acorn.defaults
		main = JVM_MAIN
		classpath(
				kotlin.targets["jvm"].compilations["main"].output.allOutputs.files,
				configurations[jvmRuntimeCpConfigName]
		)
		workingDir = getJvmOutputStagingDir().absoluteFile
		jvmArgs = listOf("-ea", "-Ddebug=true").let { defaultArgs: List<String> ->
			@Suppress("INACCESSIBLE_TYPE")
			if (OperatingSystem.current() == OperatingSystem.MAC_OS)
				defaultArgs + "-XstartOnFirstThread"
			else
				defaultArgs
		}
	}
}


