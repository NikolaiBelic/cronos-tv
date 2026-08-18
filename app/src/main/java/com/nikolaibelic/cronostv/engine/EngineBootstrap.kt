package com.nikolaibelic.cronostv.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

object EngineBootstrap {

    private const val TAG = "CronosTV/Engine"
    private const val VERSION_MARKER = ".engine_3_1_80_ready"

    @Synchronized
    fun ensureUnpacked(context: Context): Boolean {
        val filesDir = context.filesDir
        val externalDir = context.getExternalFilesDir(null)
        val marker = File(filesDir, VERSION_MARKER)

        if (marker.exists()) {
            patchAppBridge(context)
            Log.d(TAG, "Runtime del Engine ya desempaquetado")
            return true
        }

        return try {
            Log.d(TAG, "Desempaquetando runtime AceStream...")

            unzipAsset(
                context,
                "engine/arm64-v8a_private_py.zip",
                filesDir
            )

            unzipAsset(
                context,
                "engine/arm64-v8a_private_res.zip",
                filesDir
            )

            if (externalDir != null) {
                unzipAsset(
                    context,
                    "engine/public_res.zip",
                    externalDir
                )
            }

            // AHORA app_bridge.py ya existe
            patchAppBridge(context)

            val pythonDir = File(filesDir, "python")
            val mainPy = File(filesDir, "main.py")
            val maintainPy = File(filesDir, "maintain/maintain.py")

            if (!pythonDir.exists()) {
                throw IllegalStateException("No existe filesDir/python")
            }

            if (!mainPy.exists()) {
                throw IllegalStateException("No existe filesDir/main.py")
            }

            if (!maintainPy.exists()) {
                throw IllegalStateException(
                    "No existe filesDir/maintain/maintain.py"
                )
            }

            marker.writeText("3.1.80.0")

            Log.d(TAG, "✅ Runtime del Engine preparado")
            Log.d(TAG, "filesDir=${filesDir.absolutePath}")
            Log.d(TAG, "main.py=${mainPy.absolutePath}")

            true

        } catch (e: Throwable) {
            Log.e(TAG, "❌ Error desempaquetando Engine", e)
            false
        }
    }

    private fun unzipAsset(
        context: Context,
        assetPath: String,
        destination: File
    ) {
        Log.d(TAG, "Extrayendo $assetPath")

        context.assets.open(assetPath).use { input ->
            ZipInputStream(input.buffered()).use { zip ->

                var entry = zip.nextEntry

                while (entry != null) {

                    val outputFile = File(destination, entry.name)

                    // Protección contra Zip Slip
                    val destinationPath =
                        destination.canonicalFile.toPath()

                    val outputPath =
                        outputFile.canonicalFile.toPath()

                    if (!outputPath.startsWith(destinationPath)) {
                        throw SecurityException(
                            "Entrada ZIP inválida: ${entry.name}"
                        )
                    }

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()

                        outputFile.outputStream().buffered().use { output ->
                            zip.copyTo(output)
                        }
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun patchAppBridge(context: Context) {
        val bridge = File(context.filesDir, "app_bridge.py")

        if (!bridge.exists()) {
            Log.w(TAG, "No existe app_bridge.py para parchear")
            return
        }

        var text = bridge.readText()

        // Adaptar ACESTREAM_HOME
        text = text.replace(
            """return "/sdcard/org.acestream.engine"""",
            """return os.environ.get("ACESTREAM_HOME", "/sdcard/org.acestream.engine")"""
        )

        // RPC básicos: solo añadirlos si todavía no existen
        if (!text.contains("""elif method == "getAppId":""")) {

            val marker =
                "    elif method == \"onSettingsUpdated\":\n" +
                        "      return None"

            val replacement =
                "    elif method == \"getAvailableBlocks\":\n" +
                        "      try:\n" +
                        "        path = args[0] if len(args) > 0 else \"/sdcard\"\n" +
                        "        stat = os.statvfs(path)\n" +
                        "        return stat.f_bavail\n" +
                        "      except:\n" +
                        "        return 1048576\n" +
                        "    elif method == \"getMaxMemory\":\n" +
                        "      return 536870912\n" +
                        "    elif method == \"getDeviceModel\":\n" +
                        "      return \"Android\"\n" +
                        "    elif method == \"getAppId\":\n" +
                        "      return \"com.nikolaibelic.cronostv\"\n" +
                        "    elif method == \"startInternalActivity\":\n" +
                        "      return None\n" +
                        "    elif method == \"onEvent\":\n" +
                        "      return None\n" +
                        "    elif method == \"onSettingsUpdated\":\n" +
                        "      return None"

            if (text.contains(marker)) {
                text = text.replace(marker, replacement)
                Log.d(TAG, "✅ RPC Android básicos añadidos")
            } else {
                Log.e(TAG, "❌ No se encontró marcador para RPC básicos")
            }
        }

        // Versiones de la app: añadir aparte para no duplicar lo anterior
        if (!text.contains("""elif method == "getAppVersionCode":""")) {

            val marker =
                "    elif method == \"startInternalActivity\":\n" +
                        "      return None"

            val replacement =
                "    elif method == \"getAppVersionCode\":\n" +
                        "      return 1\n" +
                        "    elif method == \"getAppVersionName\":\n" +
                        "      return \"1.0\"\n" +
                        "    elif method == \"startInternalActivity\":\n" +
                        "      return None"

            if (text.contains(marker)) {
                text = text.replace(marker, replacement)
                Log.d(TAG, "✅ RPC de versión añadidos")
            } else {
                Log.e(TAG, "❌ No se encontró marcador para RPC de versión")
            }
        }

        // Arquitectura del Engine
        if (!text.contains("""elif method == "getArch":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"getArch\":\n" +
                        "      return \"arm64-v8a\"\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC getArch añadido")
            }
        }

// ABI del dispositivo/Engine
        if (!text.contains("""elif method == "getDeviceABI":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"getDeviceABI\":\n" +
                        "      return \"arm64-v8a\"\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC getDeviceABI añadido")
            }
        }

// Locale
        if (!text.contains("""elif method == "getLocale":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"getLocale\":\n" +
                        "      return \"es_ES\"\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC getLocale añadido")
            }
        }

        // Detectar si estamos en Android TV.
// Ahora estamos probando en móvil, así que usamos false.
        if (!text.contains("""elif method == "isAndroidTv":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"isAndroidTv\":\n" +
                        "      return False\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC isAndroidTv añadido")
            } else {
                Log.e(TAG, "❌ No se encontró el else final para isAndroidTv")
            }
        }

// Tamaño de bloque del almacenamiento
        if (!text.contains("""elif method == "getBlockSize":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"getBlockSize\":\n" +
                        "      try:\n" +
                        "        path = args[0] if len(args) > 0 else \"/sdcard\"\n" +
                        "        stat = os.statvfs(path)\n" +
                        "        return stat.f_frsize if stat.f_frsize > 0 else stat.f_bsize\n" +
                        "      except:\n" +
                        "        return 4096\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC getBlockSize añadido")
            } else {
                Log.e(TAG, "❌ No se encontró el else final para getBlockSize")
            }
        }

        if (!text.contains("""elif method == "getBlockCount":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"getBlockCount\":\n" +
                        "      try:\n" +
                        "        path = args[0] if len(args) > 0 else \"/sdcard\"\n" +
                        "        stat = os.statvfs(path)\n" +
                        "        return stat.f_blocks\n" +
                        "      except:\n" +
                        "        return 1\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC getBlockCount añadido")
            } else {
                Log.e(TAG, "❌ No se encontró el else final para getBlockCount")
            }
        }

        if (!text.contains("""elif method == "hasBrowser":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"hasBrowser\":\n" +
                        "      return True\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC hasBrowser añadido")
            } else {
                Log.e(TAG, "❌ No se encontró el else final para hasBrowser")
            }
        }

        if (!text.contains("""elif method == "hasWebView":""")) {

            val finalElse =
                "    else:\n" +
                        "      raise Exception(\"Unknown method: %s\" % (method,))"

            val extraRpc =
                "    elif method == \"hasWebView\":\n" +
                        "      return True\n"

            if (text.contains(finalElse)) {
                text = text.replace(
                    finalElse,
                    extraRpc + finalElse
                )

                Log.d(TAG, "✅ RPC hasWebView añadido")
            } else {
                Log.e(TAG, "❌ No se encontró el else final para hasWebView")
            }
        }

        bridge.writeText(text)

        Log.d(TAG, "✅ app_bridge.py preparado")
    }
}