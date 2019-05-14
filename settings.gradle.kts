pluginManagement {
	resolutionStrategy.eachPlugin {
		if (requested.id.id == "org.jetbrains.dokka")
			useVersion(DOKKA_VERSION)
	}
}

val DOKKA_VERSION: String by settings
rootProject.name = "acornui-gradle-plugin"
