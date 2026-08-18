package org.acestream.engine.inproc

data class EnvironmentSnapshot(
    val deviceId: String,
    val legacyDeviceId: String?,
    val aceStreamHome: String,
    val appDir: String,
    val appId: String,
    val appVersionCode: Int,
    val appVersionName: String,
    val arch: String,
    val canSignRequests: Boolean,
    val isAndroidTv: Boolean,
    val hasBrowser: Boolean,
    val hasWebView: Boolean,
    val poTokenServerPort: Int,
    val uploadDirectory: String?
)