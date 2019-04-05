import com.polyforest.acornui.build.acornui

plugins {
	id("com.polyforest.acornui.app")
	`maven-publish`
}

group = "$group.app"

repositories {
	mavenLocal()
}

kotlin {
	sourceSets {
		commonMain {
			dependencies {
				implementation(acornui("game-metadata"))
			}
		}
		named("jvmMain") {
			dependencies {
				implementation(acornui("game-jvm"))
			}
		}
		named("jsMain") {
			dependencies {
				implementation(acornui("game-js"))
			}
		}
	}
}
