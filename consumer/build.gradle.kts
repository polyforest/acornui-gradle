allprojects {
	val GRADLE_VERSION: String by extra
	tasks.withType<Wrapper> {
		gradleVersion = GRADLE_VERSION
		distributionType = Wrapper.DistributionType.ALL
	}
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

		delete("out/", "build/")
	}
}

fun <T : Task> TaskCollection<T>.tryNamed(name: String): TaskProvider<T>? {
	return try {
		named(name)
	} catch (e: UnknownTaskException) {
		null
	}
}
