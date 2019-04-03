
plugins {
	`kotlin-dsl`
	`maven-publish`
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
		tasks = listOf(":normal-library-module:clean")
	}

	consumer {
		dependsOn(plugin)
	}
}

val GRADLE_VERSION: String by project
tasks.withType<Wrapper> {
	gradleVersion = GRADLE_VERSION
	distributionType = Wrapper.DistributionType.ALL
}

afterEvaluate {
	val clean = tasks.withType(Delete::class).tryNamed(BasePlugin.CLEAN_TASK_NAME) ?: tasks.register<Delete>(BasePlugin.CLEAN_TASK_NAME)
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
