package org.acestream.engine.inproc

import android.content.Context
import android.util.Log

object EngineNative {

    private const val TAG = "CronosTV/InprocNative"

    @Volatile
    private var loaded = false

    @JvmStatic
    external fun initializePython(
        pythonHome: String,
        engineDir: String
    ): Int

    @JvmStatic
    external fun pythonSysVersion(): String?

    @JvmStatic
    external fun pythonVersionFromLib(): String?

    @JvmStatic
    external fun runMain(
        scriptPath: String,
        args: Array<String>
    ): Int

    @JvmStatic
    external fun setupEngineEnv(
        context: Context,
        externalPath: String?,
        ldLibraryPath: String?
    )

    @JvmStatic
    external fun tryImport(name: String): Boolean

    @JvmStatic
    external fun runProbe(
        label: String,
        expr: String
    )

    @JvmStatic
    external fun crashNative()

    fun ensureLoaded(): Boolean {
        if (loaded) {
            return true
        }

        return try {
            System.loadLibrary("python3.11")
            Log.d(TAG, "libpython3.11.so cargada")

            System.loadLibrary("engine_inproc")
            Log.d(TAG, "libengine_inproc.so cargada")

            loaded = true

            Log.d(
                TAG,
                "Runtime nativo AceStream INPROC cargado"
            )

            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(
                TAG,
                "Error cargando runtime INPROC",
                e
            )
            false
        }
    }
}