/*
 * Copyright 2019 Poly Forest
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

plugins {
	`kotlin-dsl`
	`maven-publish`
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

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.21")
}

publishing {
	repositories {
		maven(url = "build/repository")
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
		tasks = listOf(":normal-module:tasks", "normal-app-module:tasks", ":builder-app-module:tasks", ":app-app-module:tasks")
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
