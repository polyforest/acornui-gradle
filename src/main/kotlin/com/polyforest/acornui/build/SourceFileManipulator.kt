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

package com.polyforest.acornui.build

import java.io.File

/**
 * A utility class for applying a list of [FileProcessor] methods to a set of files.
 */
class SourceFileManipulator {

	private val fileTypeProcessorMap = hashMapOf<String, ArrayList<FileProcessor>>()

	fun addProcessor(processor: FileProcessor, vararg fileExtension: String) {
		for (extension in fileExtension) {
			val extensionLower = extension.toLowerCase()
			if (!fileTypeProcessorMap.containsKey(extension)) fileTypeProcessorMap[extensionLower] = ArrayList()
			fileTypeProcessorMap[extensionLower]!!.add(processor)
		}
	}

	/**
	 * Applies added processors that match the [file]'s extension and its children recursively if [file] is a directory.
	 *
	 * @see    File.walkTopDown for traversal details
	 */
	fun process(file: File) {
		for (i in file.walkTopDown()) {
			if (i.isFile) {
				val processors = fileTypeProcessorMap[i.extension.toLowerCase()] ?: continue
				var src = i.readText()
				for (processor in processors) {
					src = processor(src, i)
				}
				i.writeText(src)
			}
		}
	}
}

typealias FileProcessor = (src: String, file: File) -> String