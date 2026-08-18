package org.acestream.engine.inproc

import android.os.StatFs
import android.util.Log

class EngineCallbacksImpl(
    private val env: EnvironmentSnapshot
) {

    fun getDeviceId() = env.deviceId
    fun getLegacyDeviceId() = env.legacyDeviceId
    fun getAceStreamHome() = env.aceStreamHome
    fun getAppDir() = env.appDir
    fun getAppId() = env.appId
    fun getAppVersionCode() = env.appVersionCode
    fun getAppVersionName() = env.appVersionName
    fun getArch() = env.arch
    fun canSignRequests() = env.canSignRequests
    fun isAndroidTv() = env.isAndroidTv
    fun hasBrowser() = env.hasBrowser
    fun hasWebView() = env.hasWebView
    fun getUploadDirectory() = env.uploadDirectory

    fun getAvailableBytes(path: String) =
        StatFs(path).availableBytes

    fun getFreeBytes(path: String) =
        StatFs(path).freeBytes

    fun getTotalBytes(path: String) =
        StatFs(path).totalBytes

    fun getFreeBlocks(path: String) =
        StatFs(path).freeBlocksLong

    fun getTotalMemory() =
        Runtime.getRuntime().totalMemory()

    fun signRequest(value: String): String? {
        Log.w(
            "CronosTV/InprocCallbacks",
            "signRequest solicitado: $value"
        )

        return null
    }

    fun getEngineVersionCode(): Int = 302190713

    fun getLocale(): String =
        java.util.Locale.getDefault().toString()

    fun getDisplayLanguage(): String =
        java.util.Locale.getDefault().language

    fun isMobileNetwork(): Boolean = false

    fun getAceCastStatus(): String? = null

    fun getAppInfo(): String? = null

    fun getAppVersionCodeById(id: String): Int = 0

    fun getExternalStoragePublicDirectory(type: String): String? =
        android.os.Environment
            .getExternalStoragePublicDirectory(type)
            ?.absolutePath
}