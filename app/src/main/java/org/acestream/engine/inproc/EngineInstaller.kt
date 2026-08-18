package org.acestream.engine.inproc

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object EngineInstaller {

    private const val TAG = "CronosTV/EngineInstaller"

    data class Layout(
        val pythonHome: String,
        val engineDir: String
    )

    fun ensureUnpacked(context: Context): Layout {
        val pythonHome = File(context.filesDir, "python")
        val engineDir = File(context.filesDir, "engine")

        unpackPythonStdlib(context, pythonHome)
        unpackEngine(context, engineDir)

        return Layout(
            pythonHome = pythonHome.absolutePath,
            engineDir = engineDir.absolutePath
        )
    }

    private fun unpackPythonStdlib(
        context: Context,
        pythonHome: File
    ) {
        val target = File(
            pythonHome,
            "lib/python3.11"
        )

        val marker = File(
            pythonHome,
            "_stdlib-v4"
        )

        if (marker.exists()) {
            Log.d(TAG, "Python stdlib ya preparada")
            return
        }

        if (target.exists()) {
            target.deleteRecursively()
        }

        target.mkdirs()

        unzipAsset(
            context,
            "python-stdlib.zip",
            target
        )

        marker.parentFile?.mkdirs()
        marker.writeText("v4\n")

        Log.d(
            TAG,
            "Python stdlib descomprimida"
        )
    }

    private fun unpackEngine(
        context: Context,
        engineDir: File
    ) {
        val marker = File(
            engineDir,
            "_engine-v152"
        )

        if (marker.exists()) {
            Log.d(TAG, "Engine ya preparado")
            return
        }

        if (engineDir.exists()) {
            engineDir.deleteRecursively()
        }

        engineDir.mkdirs()

        copyAssetTree(
            context,
            "engine",
            engineDir
        )

        unzipAsset(
            context,
            "engine-bundle.zip",
            engineDir
        )

        marker.writeText("v152\n")

        Log.d(
            TAG,
            "Engine 3.2.19.7 descomprimido"
        )
    }

    private fun unzipAsset(
        context: Context,
        assetName: String,
        destination: File
    ) {
        context.assets.open(assetName).use { input ->
            ZipInputStream(input).use { zip ->

                var entry = zip.nextEntry

                while (entry != null) {

                    val output = File(
                        destination,
                        entry.name
                    )

                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()

                        FileOutputStream(output).use { file ->
                            zip.copyTo(file)
                        }
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun copyAssetTree(
        context: Context,
        assetPath: String,
        destination: File
    ) {
        val children =
            context.assets.list(assetPath)
                ?: return

        if (children.isEmpty()) {

            destination.parentFile?.mkdirs()

            context.assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }

            return
        }

        destination.mkdirs()

        for (child in children) {
            copyAssetTree(
                context,
                "$assetPath/$child",
                File(destination, child)
            )
        }
    }
}