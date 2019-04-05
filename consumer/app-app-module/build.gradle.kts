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
