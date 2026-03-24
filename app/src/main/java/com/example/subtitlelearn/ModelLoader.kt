package com.example.subtitlelearn

import android.content.Context
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream

object ModelLoader {

    fun loadModel(context: Context): Model {
        val assetManager = context.assets
        val outDir = File(context.filesDir, "model")

        if (!outDir.exists()) {
            copyAssets(assetManager, "model", outDir)
        }

        return Model(outDir.absolutePath)
    }

    private fun copyAssets(
        assetManager: android.content.res.AssetManager,
        path: String,
        outDir: File
    ) {
        val files = assetManager.list(path) ?: return
        outDir.mkdirs()

        for (file in files) {
            val assetPath = "$path/$file"
            val outFile = File(outDir, file)

            val subFiles = assetManager.list(assetPath)
            if (!subFiles.isNullOrEmpty()) {
                copyAssets(assetManager, assetPath, outFile)
            } else {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
