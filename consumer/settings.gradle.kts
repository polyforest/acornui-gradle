pluginManagement {
	repositories {
		maven { url = uri("../build/repository") }
		gradlePluginPortal()
	}
	resolutionStrategy {
		eachPlugin {
			if (requested.id.id.startsWith(ACORNUI_PLUGIN_MARKER_PREFIX))
				useVersion(ACORNUI_PLUGIN_VERSION)
		}
	}
}

val ACORNUI_PLUGIN_MARKER_PREFIX: String by settings
val ACORNUI_PLUGIN_VERSION: String by settings

include("normal-module", "normal-app-module", "builder-app-module", "app-app-module" )
