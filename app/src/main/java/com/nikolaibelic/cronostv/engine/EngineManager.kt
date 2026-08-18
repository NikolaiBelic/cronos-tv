package com.nikolaibelic.cronostv.engine

import android.content.Context
import android.util.Log
import org.acestream.engine.python.PyEmbedded
import java.util.concurrent.atomic.AtomicBoolean

object EngineManager {

    private const val TAG = "CronosTV/EngineManager"

    private val starting = AtomicBoolean(false)

    @Volatile
    private var engine: PyEmbedded? = null

    fun start(context: Context) {

        if (engine?.isAlive == true) {
            Log.d(TAG, "Engine ya está ejecutándose")
            return
        }

        if (!starting.compareAndSet(false, true)) {
            Log.d(TAG, "Arranque del Engine ya en curso")
            return
        }

        try {
            val newEngine = PyEmbedded(context.applicationContext)

            newEngine.start()

            engine = newEngine

            Log.d(TAG, "✅ Única instancia del Engine arrancada")

        } catch (e: Throwable) {
            Log.e(TAG, "❌ Error arrancando Engine", e)
        } finally {
            starting.set(false)
        }
    }

    fun isAlive(): Boolean {
        return engine?.isAlive == true
    }
}