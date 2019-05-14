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

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import java.net.URL

plugins {
	`kotlin-dsl`
	`maven-publish`
	id("org.jetbrains.dokka")
}

kotlinDslPluginOptions {
	// Suppress the experimental warning from kotlin-dsl
	experimentalWarning.set(false)
}

val PRODUCT_GROUP: String by extra
val PRODUCT_VERSION: String by extra
group = PRODUCT_GROUP
version = PRODUCT_VERSION

repositories {
	jcenter()
}

val KOTLIN_VERSION: String by extra
dependencies {
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$KOTLIN_VERSION")
}

kotlin {
	this.sourceSets["main"].kotlin.sourceDirectories
}

publishing {
	repositories {
		maven(url = "build/repository")
	}
}

val KOTLIN_LANGUAGE_VERSION: String by extra
val kotlinApiVersionPropName = "KOTLIN_API_VERSION"
if (!extra.has(kotlinApiVersionPropName)) extra[kotlinApiVersionPropName] = KOTLIN_LANGUAGE_VERSION
val KOTLIN_API_VERSION: String by extra
val KOTLIN_JVM_TARGET: String by extra
val liveKotlinJvmCompileTasks = tasks.withType<KotlinCompile<KotlinJvmOptions>>()
liveKotlinJvmCompileTasks.configureEach {
	kotlinOptions {
		languageVersion = KOTLIN_LANGUAGE_VERSION
		apiVersion = KOTLIN_API_VERSION
		jvmTarget = KOTLIN_JVM_TARGET
		verbose = true
	}
}

// Incomplete sample for rudimentary testing.
tasks {
	val plugin by registering(GradleBuild::class) {
		group = "sample"
		dir = file(".")
		tasks = listOf("publish")
	}

	val consumer by registering(GradleBuild::class) {
		group = "sample"
		dir = file("consumer")
		tasks = listOf(
			":normal-module:tasks",
			"normal-app-module:tasks",
			":builder-app-module:tasks",
			":app-app-module:tasks"
		)
	}

	consumer {
		dependsOn(plugin)
	}
}

val GRADLE_VERSION: String by extra
tasks.withType<Wrapper> {
	gradleVersion = GRADLE_VERSION
	distributionType = Wrapper.DistributionType.ALL
}

afterEvaluate {
	val clean = tasks.withType(Delete::class).tryNamed(BasePlugin.CLEAN_TASK_NAME)
		?: tasks.register<Delete>(BasePlugin.CLEAN_TASK_NAME)
	clean {
		group = "build"
		description = """
            Deletes:
            ${delete.joinToString("\n") {
			if (it is File)
				it.relativeToOrSelf(projectDir).path
			else
				it.toString()
		}}

        (all files relative to project directory unless absolute)
        """.trimIndent()

		delete(file("out/"))
	}
}

fun <T : Task> TaskCollection<T>.tryNamed(name: String): TaskProvider<T>? {
	return try {
		named(name)
	} catch (e: UnknownTaskException) {
		null
	}
}

// Documentation
val latestJdkVersion = 11
val minimumSupportedJdkVersion = 6
val maximumSupportedJdkVersion = latestJdkVersion
val javaSourceDirs = sourceSets.main.map { it.java.sourceDirectories }
val kotlinSourceDirs = kotlin.sourceSets.main.map { it.kotlin.sourceDirectories }
val JDK_DOCUMENTATION_LINK_VERSION: String by extra
val jdkDocLinkVersion = JDK_DOCUMENTATION_LINK_VERSION.toIntOrNull()
	?.takeIf { it in minimumSupportedJdkVersion..maximumSupportedJdkVersion }
val baseDokkaConfiguration: DokkaTask.() -> Unit = {
	impliedPlatforms = mutableListOf("JVM")
	includes = listOf("Module.md")
	apiVersion = KOTLIN_API_VERSION
	languageVersion = KOTLIN_LANGUAGE_VERSION
	jdkVersion = jdkDocLinkVersion ?: maximumSupportedJdkVersion

	noJdkLink = when (jdkVersion) {
		in 6..10 -> false
		else     -> {
			val jdkRootLink = URL("https://docs.oracle.com/en/java/javase/$jdkVersion/docs/api/")
			externalDocumentationLink {
				url = jdkRootLink
				packageListUrl = URL(jdkRootLink, "elements-list")
			}

			true
		}
	}

	val defaultSamplesLocation = "src/samples"
	if (file(defaultSamplesLocation).listFiles()?.isNotEmpty() == true)
		samples = listOf(defaultSamplesLocation)

	linkMapping {
		// Equivalent to `src/main/kotlin`
		dir = "./"
		url = "https://github.com/polyforest/acornui-gradle-plugin/blob/master"
		suffix = "#L"
	}
}

tasks.dokka(baseDokkaConfiguration)

val dokkaJavaDoc by tasks.registering(DokkaTask::class) {
	baseDokkaConfiguration(this)
	group = documentationGroup
	sourceDirs = javaSourceDirs.get() + kotlinSourceDirs.get()
	outputDirectory = buildDir.resolve(JavaPlugin.JAVADOC_TASK_NAME).absolutePath
}

val javadoc by tasks.existing {
	enabled = false
	dependsOn(dokkaJavaDoc)
}

val documentationGroup = JavaBasePlugin.DOCUMENTATION_GROUP
val dokkaJar by tasks.registering(Jar::class) {
	group = documentationGroup
	description = "Assembles Kotlin docs artifact with Dokka (produces default `-javadoc.jar`)."
	archiveClassifier.set("javadoc")
	from(tasks.dokka)
}

val dokkaJavaJar by tasks.registering(Jar::class) {
	group = documentationGroup
	description = "Assembles Kotlin as Java docs artifact with Dokka (produces alternative `-javadoc.jar`)"
	from(dokkaJavaDoc)
}

val sourcesJar by tasks.registering(Jar::class) {
	group = documentationGroup
	description = "Assembles Kotlin sources artifact with Dokka (produces `-sources.jar`)."
	archiveClassifier.set("sources")
	from(kotlinSourceDirs)
}
