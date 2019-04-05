plugins {
	id("com.polyforest.acornui.root")
}

allprojects {
	configurations.all {
		// check for updates every build
		resolutionStrategy.cacheChangingModulesFor(0, "seconds")
	}
}
