package com.acornui.plugins.util

import com.acornui.async.launch
import com.acornui.core.asset.AssetManager
import com.acornui.core.di.inject
import com.acornui.core.io.file.Files
import com.acornui.jvm.JvmHeadlessApplication
import com.acornui.serialization.json
import com.acornui.texturepacker.AcornTexturePacker
import com.acornui.texturepacker.jvm.writer.JvmTextureAtlasWriter
import java.io.File

fun packAssets(srcDir: File, destDir: File, unpackedSuffix: String) {
    JvmHeadlessApplication(srcDir.path).start {
        val files = inject(Files)
        val assets = inject(AssetManager)

        val writer = JvmTextureAtlasWriter()
        val dirEntry = files.getDir(srcDir.path)!!

        val atlasName = srcDir.name.removeSuffix(unpackedSuffix)
        launch {
            val packedData = AcornTexturePacker(assets, json).pack(dirEntry, quiet = true)
            writer.writeAtlas("$atlasName.json", "$atlasName{0}", packedData, destDir)
        }
    }
}