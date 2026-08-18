package com.nikolaibelic.cronostv.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log

object AceStreamExternalEngine {

    private const val TAG = "CronosTV/ExternalEngine"

    private const val ACESTREAM_PACKAGE =
        "org.acestream.node.web"

    private const val ACTION_BIND_AIDL =
        "org.acestream.engine.service.v0.bind_aidl"

    private const val AIDL_DESCRIPTOR =
        "org.acestream.engine.service.v0.IAceStreamEngine"

    private const val TRANSACTION_START_ENGINE = 3
    private const val TRANSACTION_GET_ENGINE_API_PORT = 6
    private const val TRANSACTION_GET_HTTP_API_PORT = 7

    @Volatile
    private var bound = false

    @Volatile
    private var binding = false

    @Volatile
    private var ready = false

    @Volatile
    private var engineApiPort = 0

    @Volatile
    private var httpApiPort = 0

    private var serviceBinder: IBinder? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder
        ) {
            Log.d(
                TAG,
                "✅ onServiceConnected: $name"
            )

            bound = true
            binding = false
            serviceBinder = service

            try {
                Log.d(
                    TAG,
                    "Binder descriptor=${service.interfaceDescriptor}"
                )

                Log.d(
                    TAG,
                    "Binder class=${service.javaClass.name}"
                )
            } catch (e: Throwable) {
                Log.w(
                    TAG,
                    "No se pudo leer información del Binder",
                    e
                )
            }

            try {
                startEngine(service)

                Log.d(
                    TAG,
                    "✅ startEngine() enviado correctamente"
                )

                waitUntilReady(service)

            } catch (e: Throwable) {
                Log.e(
                    TAG,
                    "❌ Error arrancando AceStream Engine",
                    e
                )

                ready = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(
                TAG,
                "⚠️ onServiceDisconnected: $name"
            )

            resetState()
        }

        override fun onBindingDied(name: ComponentName) {
            Log.e(
                TAG,
                "❌ onBindingDied: $name"
            )

            resetState()
        }

        override fun onNullBinding(name: ComponentName) {
            Log.e(
                TAG,
                "❌ onNullBinding: $name"
            )

            resetState()
        }
    }

    fun bind(context: Context) {

        if (bound) {
            Log.d(
                TAG,
                "Ya estamos enlazados al engine"
            )
            return
        }

        if (binding) {
            Log.d(
                TAG,
                "Binding ya en curso"
            )
            return
        }

        val intent = Intent(
            ACTION_BIND_AIDL
        ).apply {
            setPackage(ACESTREAM_PACKAGE)
        }

        try {
            binding = true

            Log.d(
                TAG,
                "Intentando bind: action=$ACTION_BIND_AIDL package=$ACESTREAM_PACKAGE"
            )

            val result =
                context.applicationContext.bindService(
                    intent,
                    connection,
                    Context.BIND_AUTO_CREATE
                )

            Log.d(
                TAG,
                "bindService()=$result"
            )

            if (!result) {
                binding = false
            }

        } catch (e: Throwable) {

            binding = false

            Log.e(
                TAG,
                "❌ Error haciendo bindService",
                e
            )
        }
    }

    fun unbind(context: Context) {

        if (!bound && !binding) {
            return
        }

        try {
            context.applicationContext
                .unbindService(connection)

        } catch (e: Throwable) {

            Log.w(
                TAG,
                "Error en unbindService",
                e
            )
        }

        resetState()
    }

    fun isBound(): Boolean =
        bound

    fun isReady(): Boolean =
        ready

    fun getHttpPort(): Int =
        httpApiPort

    fun getEnginePort(): Int =
        engineApiPort

    fun getStreamUrl(
        contentId: String
    ): String? {

        val port = httpApiPort

        if (!ready || port <= 0) {
            return null
        }

        return "http://127.0.0.1:$port/ace/getstream?id=$contentId"
    }

    private fun startEngine(
        service: IBinder
    ) {

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(
                AIDL_DESCRIPTOR
            )

            service.transact(
                TRANSACTION_START_ENGINE,
                data,
                reply,
                0
            )

            reply.readException()

        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun waitUntilReady(
        service: IBinder
    ) {

        Thread {

            repeat(40) {

                try {
                    val enginePort =
                        getIntFromBinder(
                            service,
                            TRANSACTION_GET_ENGINE_API_PORT
                        )

                    val httpPort =
                        getIntFromBinder(
                            service,
                            TRANSACTION_GET_HTTP_API_PORT
                        )

                    Log.d(
                        TAG,
                        "Esperando engine: enginePort=$enginePort httpPort=$httpPort"
                    )

                    if (httpPort > 0) {

                        engineApiPort =
                            enginePort

                        httpApiPort =
                            httpPort

                        ready = true

                        Log.d(
                            TAG,
                            "✅ Engine preparado: engineApiPort=$engineApiPort httpApiPort=$httpApiPort"
                        )

                        return@Thread
                    }

                } catch (e: Throwable) {

                    Log.d(
                        TAG,
                        "Engine todavía no preparado: ${e.message}"
                    )
                }

                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }

            ready = false

            Log.e(
                TAG,
                "❌ Timeout esperando a AceStream Engine"
            )

        }.start()
    }

    private fun getIntFromBinder(
        service: IBinder,
        transaction: Int
    ): Int {

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        return try {

            data.writeInterfaceToken(
                AIDL_DESCRIPTOR
            )

            service.transact(
                transaction,
                data,
                reply,
                0
            )

            reply.readException()

            reply.readInt()

        } finally {

            reply.recycle()
            data.recycle()
        }
    }

    private fun resetState() {

        bound = false
        binding = false
        ready = false

        engineApiPort = 0
        httpApiPort = 0

        serviceBinder = null
    }
}