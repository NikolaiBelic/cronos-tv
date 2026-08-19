package org.acestream.engine.inproc

import android.os.Build
import android.util.Log

object EngineCallbacks {

    private const val TAG = "CronosTV/InprocCallbacks"

    @Volatile
    private var impl: EngineCallbacksImpl? = null

    @Volatile
    private var environment: EnvironmentSnapshot? = null

    fun init(environment: EnvironmentSnapshot) {
        this.environment = environment
        impl = EngineCallbacksImpl(environment)

        Log.d(
            TAG,
            "EngineCallbacks inicializado: appId=${environment.appId}"
        )
    }

    @JvmStatic
    fun dispatch(
        name: String,
        args: Array<Any?>
    ): Any? {

        Log.d(
            TAG,
            "dispatch: name=$name args=${args.contentToString()}"
        )

        val callbacks = impl

        return try {
            when (name) {

                "getAppInfo" -> {
                    val env = environment
                        ?: throw IllegalStateException("EngineCallbacks no inicializado")

                    val json = org.json.JSONObject().apply {
                        put("packageName", env.appId)
                        put("versionCode", env.appVersionCode)
                        put("versionName", env.appVersionName)
                        put("arch", env.arch)
                        put("locale", java.util.Locale.getDefault().toLanguageTag())
                        put("isAndroidTv", env.isAndroidTv)
                        put("hasBrowser", env.hasBrowser)
                        put("hasWebView", env.hasWebView)
                        put("sdk", Build.VERSION.SDK_INT)
                        put("manufacturer", Build.MANUFACTURER)
                        put("model", Build.MODEL)
                        put("deviceId", env.deviceId)

                        put("license", org.json.JSONObject.NULL)
                        put("attestationMode", org.json.JSONObject.NULL)
                        put("playIntegritySupported", false)
                        put("lastError", org.json.JSONObject.NULL)
                    }.toString()

                    Log.d(TAG, "getAppInfo -> $json")
                    json
                }

                "log" -> {
                    if (args.size >= 2) {
                        Log.d(
                            "CronosTV/Python",
                            "${args[0]}: ${args[1]}"
                        )
                    }
                    Unit
                }

                "getDeviceABI" ->
                    Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

                "getApiLevel" ->
                    Build.VERSION.SDK_INT.toLong()

                "getDeviceManufacturer" ->
                    Build.MANUFACTURER

                "getDeviceModel" ->
                    Build.MODEL

                "getDeviceName" ->
                    Build.DEVICE

                "getDeviceProductName" ->
                    Build.PRODUCT

                "getDeviceId" ->
                    callbacks?.getDeviceId()

                "getLegacyDeviceId" ->
                    callbacks?.getLegacyDeviceId()

                "getAceStreamHome" ->
                    callbacks?.getAceStreamHome()

                "getAppDir" ->
                    callbacks?.getAppDir()

                "getAppId" ->
                    callbacks?.getAppId()

                "getAppVersionCode" ->
                    callbacks?.getAppVersionCode()?.toLong() ?: 0L

                "getAppVersionName" ->
                    callbacks?.getAppVersionName()

                "getArch" ->
                    callbacks?.getArch()

                "canSignRequests" ->
                    callbacks?.canSignRequests() ?: false

                "isAndroidTv" ->
                    callbacks?.isAndroidTv() ?: false

                "hasBrowser" ->
                    callbacks?.hasBrowser() ?: false

                "hasWebView" ->
                    callbacks?.hasWebView() ?: false

                "getUploadDirectory" ->
                    callbacks?.getUploadDirectory()

                "getAvailableBytes" ->
                    callbacks?.getAvailableBytes(args[0] as String) ?: 0L

                "getFreeBytes" ->
                    callbacks?.getFreeBytes(args[0] as String) ?: 0L

                "getTotalBytes" ->
                    callbacks?.getTotalBytes(args[0] as String) ?: 0L

                "getFreeBlocks" ->
                    callbacks?.getFreeBlocks(args[0] as String) ?: 0L

                "getTotalMemory" ->
                    callbacks?.getTotalMemory() ?: 0L

                "signRequest" ->
                    callbacks?.signRequest(args[0] as String)

                "getEngineVersionCode" ->
                    callbacks?.getEngineVersionCode()?.toLong() ?: 0L

                "getLocale" ->
                    callbacks?.getLocale()

                "getDisplayLanguage" ->
                    callbacks?.getDisplayLanguage()

                "isMobileNetwork" ->
                    callbacks?.isMobileNetwork() ?: false

                "getAceCastStatus" ->
                    callbacks?.getAceCastStatus()

                "getAppVersionCodeById" ->
                    callbacks
                        ?.getAppVersionCodeById(args[0] as String)
                        ?.toLong() ?: 0L

                "getExternalStoragePublicDirectory" ->
                    callbacks?.getExternalStoragePublicDirectory(
                        args[0] as String
                    )

                "getMaxMemory" ->
                    Runtime.getRuntime().maxMemory()

                else -> {
                    Log.w(
                        TAG,
                        "Método no implementado todavía: $name"
                    )
                    null
                }
            }
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "Error en dispatch($name)",
                e
            )
            null
        }
    }
}