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

package com.polyforest.acornui.build.utils

import com.polyforest.acornui.build.NO_PROP_FOUND_MSG
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.kotlin.dsl.extra

internal val ExtensionAware.maybeExtra: ExtraPropertiesExtension
	get() {
		val extraProps = extensions.extraProperties
		return object : ExtraPropertiesExtension by extraProps {
			override fun get(name: String): Any? {
				return if (has(name)) {
					if (extraProps.get(name) == null)
						throw Exception(
							"Use 'extra' to get nullable properties.  '${this@maybeExtra::maybeExtra.name}' returns " +
									"the null value to indicate a missing value for easy elvis operator chaining and " +
									"cannot accommodate properties with a null value."
						)
					extraProps[name]
				} else
					null
			}
		}
	}

internal fun ExtensionAware.extraOrDefault(name: String, default: String? = null) =
	extra[name]?.toString()?.takeIf { it.isNotBlank() } ?: default ?: throw NoSuchElementException(NO_PROP_FOUND_MSG)

internal fun ExtensionAware.maybeExtraOrDefault(name: String, default: String? = null) =
	maybeExtra[name]?.toString()?.takeIf { it.isNotBlank() } ?: default
