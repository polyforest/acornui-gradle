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

package com.polyforest.acornui

import com.polyforest.acornui.build.AUI
import com.polyforest.acornui.build.maybeCreateCleanTask

/**
 * Plugin:  com.polyforest.acornui.root
 * Provide standard configuration for root build scripts
 */

val acorn = AUI(project)
val GRADLE_VERSION by acorn.defaults

allprojects {
	tasks.withType<Wrapper> {
		gradleVersion = GRADLE_VERSION
		distributionType = Wrapper.DistributionType.ALL
	}
}

// Delete the IDE default build directory upon `clean`
// Also, for some reason a 'build' directory at the root project (with empty contents) gets created
maybeCreateCleanTask {
	delete("out/", "build/")
}
