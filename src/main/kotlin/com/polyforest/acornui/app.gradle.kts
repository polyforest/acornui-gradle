package com.polyforest.acornui

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.*
import java.awt.Desktop
import java.net.URI
import com.polyforest.acornui.build.*

/**
 * Plugin:  com.polyforest.acornui.app
 * Used in Acorn UI consumer projects for *app*-kotlin multiplatform modules.
 * JS compilation main defaults to "call"
 */

plugins {
	id("com.polyforest.acornui.app-basic")
}

val defaults = mapOf(
	"LWJGL_VERSION" to "3.2.1",
	"AP_TGROUP_PREFIX" to ".ap."
)

fun extraOrDefault(name: String, default: String = defaults.getValue(name)) : String {
	return try {
		@Suppress("UNCHECKED_CAST")
		extra[name] as String
	} catch (e: Exception) {
		default
	}
}

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

				val LWJGL_VERSION = extraOrDefault("LWJGL_VERSION")
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
val allOutUnprocessedJsDir = getAllOutDir(AppTargetFacade.DEFAULT_JS_TARGET_APP_TARGET_NAME, aggregatedUnprocessedRelDir)
val allOutUnprocessedJvmDir = getAllOutDir(AppTargetFacade.DEFAULT_JVM_TARGET_APP_TARGET_NAME, aggregatedUnprocessedRelDir)
val jsLibsRelDir = File("lib")
val assetsRelDir = File("assets")
// Destination root for js build variations hosted by the IDE's built-in web server
val webFolderRoot = projectDir
val webFolder = webFolderRoot.resolve("www")
val webDistFolder = webFolderRoot.resolve("wwwDist")
val ghDocsFolder = file("docs")
val targetCompilations = { target: String -> kotlin.targets[target].compilations }
val useBasicAssetsPropName = "USE_BASIC_ASSETS"
val acornuiHomePropName = "ACORNUI_HOME"
typealias FileProcessor = (src: String, file: File) -> String

val hasRequestedBasicAssets by lazy { project.hasProperty(useBasicAssetsPropName) && project.property(useBasicAssetsPropName).toString().toBoolean() }
val hasValidAcornUiHome by lazy {
	if (!project.hasProperty(acornuiHomePropName)) false else {
		val acornUiHome = project.property(acornuiHomePropName) as String
		acornUiHome.isBlank().not()
	}
}
val hasValidAcornUiHomeThatExists by lazy {
	if (hasValidAcornUiHome) {
		val acornUiHome = project.property(acornuiHomePropName) as String
		File(acornUiHome).exists()
	} else false
}

// Helper objects
object SourceFileManipulator {

	/**
	 * Iterates through [files] and applies [processors] to their contents in place.
	 */
	fun <T : Iterable<File>> process(files: T, processors: List<FileProcessor>) {
		files.forEach {
			if (it.isFile) {
				var src = it.readText()
				for (processor in processors) {
					src = processor(src, it)
				}
				it.writeText(src)
			}
		}
	}

	/**
	 * Iterates through [files] and applies [processor] to their contents in place.
	 */
	fun <T : Iterable<File>> process(files: T, processor: FileProcessor) {
		process(files, listOf(processor))
	}
}

/**
 * Searches for script and css include elements and adds a cache buster query parameter to them.
 */
object ScriptCacheBuster {

	val extensions = listOf(
		"asp", "aspx", "cshtml", "cfm", "go", "jsp", "jspx", "php", "php3", "php4", "phtml", "html", "htm", "rhtml",
		"css"
	)

	private val regex = Regex("""([\w./\\]+)(\?[\w=&]*)(%VERSION%)""")

	/**
	 * Replaces %VERSION% tokens with the last modified timestamp.
	 * The source must match the format:
	 * foo/bar.ext?baz=%VERSION%
	 * foo/bar.ext must be a local file.
	 */
	fun replaceVersionWithModTime(src: String, file: File): String {
		return regex.replace(src) { match ->
			val path = match.groups[1]!!.value
			val relativeFile = File(file.parent, path)
			if (relativeFile.exists()) path + match.groups[2]!!.value + relativeFile.lastModified()
			else match.value
		}
	}
}

object KotlinMonkeyPatcher {

	/**
	 * Makes it all go weeeeee!
	 */
	fun optimizeProductionCode(src: String, file: File? = null): String {
		var result = src
		result = simplifyArrayListGet(result)
		result = stripCce(result)
		result = stripRangeCheck(result)
		result += "function alwaysTrue() { return true; }"
		return result
	}

	/**
	 * Strips type checking that only results in a class cast exception.
	 */
	private fun stripCce(src: String): String {
		val d = '$'
		return Regex("""Kotlin\.is(Type|Array|Char|CharSequence|Number)(\((.*?) \? tmp\$d :
			|(Kotlin\.)?throw(\S*)\(\))""".trimMargin()).replace(src, "alwaysTrue\$2")
	}

	private fun stripRangeCheck(src: String): String {
		return src.replace("this.rangeCheck_2lys7f${'$'}_0(index)", "index")
	}

	private fun simplifyArrayListGet(src: String): String {
		return Regex("""ArrayList\.prototype\.get_za3lpa\$[\s]*=[\s]*function[\s]*\(index\)[\s]*\{([^}]+)};""")
			.replace(src) {
				"""ArrayList.prototype.get_za3lpa$ = function(index) { return this.array_hd7ov6${'$'}_0[index] };"""
			}
	}

	private fun simplifyArrayListSet(src: String): String {
		return Regex("""ArrayList\.prototype\.set_wxm5ur\$[\s]*=[\s]*function[\s]*\(index, element\)[\s]*\{([^}]+)};""")
			.replace(src) {
				"""
						ArrayList.prototype.set_wxm5ur${'$'} = function (index, element) {
			  				var previous = this.array_hd7ov6${'$'}_0[index];
			  				this.array_hd7ov6${'$'}_0[index] = element;
			  				return previous;
						};
					""".trimIndent()
			}
	}
}

// Setup basic skin directory as resource directory...
maybeAddBasicResourcesAsResourceDir()

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
			63340,
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

	// Configuration Helpers
	fun <T : Task> T.document(description: String? = null, group: String? = null, groupPrefix: String? = null) {
		val task = this
		val defaultGroup = "other"
		val AP_TGROUP_PREFIX by defaults

		task.group = when {
			group == null && groupPrefix == null -> AP_TGROUP_PREFIX + defaultGroup
			group == null && groupPrefix != null -> groupPrefix + defaultGroup
			group != null && groupPrefix == null -> AP_TGROUP_PREFIX + group
			else -> groupPrefix + group
		}

		description?.let { task.description = it }
	}

	fun <T : Task> T.validateAcornUiHomeToUseBasicAssets() {
		doFirst {
			if (hasRequestedBasicAssets && !hasValidAcornUiHomeThatExists)
				throw InvalidUserDataException("""
					Starter assets were requested, but a valid $acornuiHomePropName could not be found.
					Please set $acornuiHomePropName in one of the following locations:

					-pass it on the command line as an environment variable => `ORG_GRADLE_PROJECT_ACORNUI_HOME=<absolute_path>`
					-set it in your `${project.gradle.gradleUserHomeDir}/gradle.properties` file => `ACORNUI_HOME=<absolute_path>`

					For more details, see: https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties
				""".trimIndent())
		}
	}

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
			SourceFileManipulator.process(source, ScriptCacheBuster::replaceVersionWithModTime)
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
				logger.warn("""
					Cannot open browser to: ${uri.toASCIIString()}
					The build environment OS does not support the Java Desktop API.
				""".trimIndent())
			}
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
		val target = AppTargetFacade(project, targetName)

		from(target.mpXResources)
		into(allOutUnprocessedJsDir)
		exclude("**/*.meta.js", "**/*.kjsm")
		includeEmptyDirs = false

		into(jsLibsRelDir) {
			from(target.mpXSources)
			include("**/*.js", "**/*.js.map")
		}
	}

	fun <S : Task, T : Task> T.onlyIfJsOutputChanged() {
		onlyIf {
			assembleJsOutput.get().didWork
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
		delete(webFolder, webDistFolder)
	}

	// Js - Prod tasks
	val stageJsOutputForProcessingProd by registering(Copy::class) {
		dependsOn(writeJsResourceManifestFile)
		from(getJsOutputStagingDir()).exclude("**/*.js.map")
		configureCommonStageTargetOutputForProcessingTask(target = "js", type = "prod")
	}
	fun getJsProdOutputStagingDir() = stageJsOutputForProcessingProd.get().destinationDir

	val optimizeJs by registering(SourceTask::class) {
		document("Monkeypatch js code with optimizations.")
		source(stageJsOutputForProcessingProd)
		include("**/*.js")

		doLast {
			SourceFileManipulator.process(source, KotlinMonkeyPatcher::optimizeProductionCode)
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

		val targetName = AppTargetFacade.DEFAULT_JVM_TARGET_APP_TARGET_NAME
		val target = AppTargetFacade(project, targetName)

		from(target.mpXResources)
		into(allOutUnprocessedJvmDir)
		includeEmptyDirs = false
	}

	fun <S : Task, T : Task> T.onlyIfJvmOutputChanged() {
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

		main = "com.matrixprecise.jvm.CatariJvmKt"
		classpath(
			kotlin.targets["jvm"].compilations["main"].output.allOutputs.files,
			configurations[jvmRuntimeCpConfigName]
		)
		workingDir = getJvmOutputStagingDir().absoluteFile
		jvmArgs = listOf("-ea", "-Ddebug=true").let { defaultArgs: List<String> ->
			if (OperatingSystem.current() == OperatingSystem.MAC_OS)
				defaultArgs + "-XstartOnFirstThread"
			else
				defaultArgs
		}
	}
}

class AppTargetFacade(
	val project: Project,
	name: String,
	/**
	 * Archival extensions (without the . prefix) used to recognize archives.
	 */
	val archiveExtensions: List<String> = listOf("war", "jar", "zip")
) {
	private val target = project.kotlin.targets.named(name)
	private val allProjects
		get() = try {
			rootProject.subprojects - project(":$AP_BUILD_MODULE_NAME")
		} catch (e: Exception) {
			rootProject.subprojects
		}
	private val mainCompilation = target.map {
		it.compilations.named<KotlinCompilationToRunnableFiles<KotlinCommonOptions>>(DEFAULT_MAIN_COMPILATION_NAME).get()
	}

	/**
	 * All sources for a given multi-platform target's module
	 *
	 * e.g.
	 * given:
	 * a target named "js" created via preset,
	 * composed of multi-platform sources commonSourceSetA and jsSourceSetA...
	 *
	 * when:
	 * jsSourceSetA declares a dependency on jsSourceSetB,
	 * where all source sets are defined in the same multi-platform module...
	 *
	 * then:
	 * [mpSources] = commonSourceSetA + jsSourceSetA + jsSourceSetB
	 */
	val mpSources = mainCompilation.map { it.output.classesDirs }

	/**
	 * All resources for a given multi-platform target's module
	 *
	 * Same as [mpSources], but for resources
	 * @see [mpSources]
	 */
	val mpResources = mainCompilation.map {
		it.allKotlinSourceSets.fold(project.files()) { fc: FileCollection, sourceSet: KotlinSourceSet ->
			fc + sourceSet.resources.sourceDirectories
		}
	}

	/**
	 * All source files found in the target's internal/external modules dependencies
	 * Excludes archival and source metadata if applicable.
	 *
	 * NOTE: Given this method relies on exploded archives that make up the runtime classpath, it's not guaranteed
	 * that the returned file collection provider only has source files.
	 *
	 */
	private val mpXDependencySources = mainCompilation.map {
		extractArchives(it.runtimeDependencyFiles).matching {
			exclude("META-INF/")
		}
	}

	private fun extractArchives(files: FileCollection): FileTree {
		val emptyFileTree = project.files().asFileTree

		return filterToArchives(files).fold(emptyFileTree) { fileTreeAccumulator: FileTree, archive: File ->
			fileTreeAccumulator + zipTree(archive)
		}
	}

	private fun filterToArchives(files: FileCollection) =
		files.filter { file -> file.extension in archiveExtensions }

	/**
	 * All sources for a given multi-platform target's module and its cross module multi-platform dependencies
	 * Includes internal, external, and transitive dependency sources.
	 *
	 * Same as [mpSources], but also cross-module
	 * @see [mpSources]
	 */
	val mpXSources =
		project.provider(Callable {
			mpXDependencySources.get() + mpSources.get()
		})

	/**
	 * All resources for a given multi-platform target's module and its cross module multi-platform dependencies
	 * Includes all resources for all modules in the root Gradle project sans unrelated modules (e.g. build module)
	 *
	 * Same as [mpResources], but also cross all modules in the Gradle build
	 * @see [mpResources]
	 */
	val mpXResources = allProjects.fold(project.files()) { fc: FileCollection, p: Project ->
		// TODO: Make this leverage dependencies so it is more accurate and less brittle
		val mainCompilation =
			p.kotlin.targets[target.name].compilations[DEFAULT_MAIN_COMPILATION_NAME]
		// Pseudo-Code:TODO | Remove
		// println("CHECK IT:  " + rootProject.subprojects)

		fc + mainCompilation.allKotlinSourceSets.fold(fc) { innerFC: FileCollection, sourceSet: KotlinSourceSet ->
			innerFC + sourceSet.resources.sourceDirectories
		}
	}

	companion object {
		private const val AP_BUILD_MODULE_NAME = "builder"
		private const val APPLICATION_GROUP = ApplicationPlugin.APPLICATION_GROUP
		const val DEFAULT_JS_TARGET_APP_TARGET_NAME = "js"
		const val DEFAULT_JVM_TARGET_APP_TARGET_NAME = "jvm"
		private const val DEFAULT_MAIN_COMPILATION_NAME = KotlinCompilation.MAIN_COMPILATION_NAME
	}
}

class BuildTool(val project: Project, configurationName: String, val main: String, dependencies: List<String>? = null) {
	val configuration = project.configurations.maybeCreate(configurationName)

	init {
		dependencies?.let { toolDependencies ->
			project.dependencies {
				toolDependencies.forEach {
					configuration(it)
				}
			}
		}
	}
}

/**
 * Adds `basic` starter resources as a resource directory if specified by `USE_BASIC_ASSETS` property
 *
 * Configuration is added to all compilations that have resource processing.
 * Configuration will apply to all targets and compilations added in the future.
 * `ACORNUI_HOME` must be defined (e.g. passed in as env var)
 *
 * Note: When `ACORNUI_HOME` is not provided via `gradle.properties`, importing the gradle project in the IDE will not
 * mark the directory as a resource in IDE project metadata.
 */
fun maybeAddBasicResourcesAsResourceDir() {
	// Do not check for existence of the Acorn Ui home dir so project can be imported without it set.
	if (hasRequestedBasicAssets) {
		if (hasValidAcornUiHome) {
			val USE_BASIC_ASSETS: String by project.extra
			val ACORNUI_HOME: String by project.extra
			val basicSkinDir = File(ACORNUI_HOME, "skins/basic/resources")
			// Use closures so it is applied to all future targets and compilations added
			// Using nested closure, though untested whether it is necessary
			val compilationClosure = closureOf<KotlinCompilation<KotlinCommonOptions>> {
				if (this is KotlinCompilationWithResources) {
					defaultSourceSet.resources.let {
						it.setSrcDirs(listOf(basicSkinDir, it.srcDirs))
					}
				}
			}
			val addBasicSkinResourcesAsResourceDir = closureOf<KotlinTarget> {
				compilations.all(compilationClosure)
			}
			if (USE_BASIC_ASSETS.toBoolean() && basicSkinDir.exists()) {
				project.kotlin.targets.all(addBasicSkinResourcesAsResourceDir)
			}
		} else
			logger.warn("Until $acornuiHomePropName is added as a property or passed as an env var, basic starter " +
								"assets will not be used")
	}
}
