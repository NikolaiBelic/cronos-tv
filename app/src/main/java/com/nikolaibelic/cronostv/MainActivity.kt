package com.nikolaibelic.cronostv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nikolaibelic.cronostv.adapter.CanalAdapter
import com.nikolaibelic.cronostv.model.Canal
import com.nikolaibelic.cronostv.parser.M3UParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CanalAdapter
    private val canales = mutableListOf<Canal>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // External Engine desactivado en rama mod-detected.
        // Esta rama prueba exclusivamente el Engine 3.2.19.7 INPROC.
        // com.nikolaibelic.cronostv.engine.AceStreamExternalEngine
        //     .bind(applicationContext)

        // PRUEBA NUEVO ENGINE 3.2.19.7 INPROC
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val layout =
                    org.acestream.engine.inproc.EngineInstaller
                        .ensureUnpacked(this@MainActivity)

                android.util.Log.d(
                    "CronosTV/TestInproc",
                    "pythonHome=${layout.pythonHome}"
                )

                android.util.Log.d(
                    "CronosTV/TestInproc",
                    "engineDir=${layout.engineDir}"
                )

                val loaded =
                    org.acestream.engine.inproc.EngineNative
                        .ensureLoaded()

                android.util.Log.d(
                    "CronosTV/TestInproc",
                    "native loaded=$loaded"
                )

                if (!loaded) {
                    return@launch
                }

                val externalDir =
                    getExternalFilesDir(null) ?: filesDir

                val ldLibraryPath =
                    layout.pythonHome +
                            "/lib:" +
                            layout.pythonHome +
                            "/lib/python3.11/lib-dynload:" +
                            applicationInfo.nativeLibraryDir

                org.acestream.engine.inproc.EngineNative
                    .setupEngineEnv(
                        this@MainActivity,
                        externalDir.absolutePath,
                        ldLibraryPath
                    )

                val packageInfo =
                    packageManager.getPackageInfo(packageName, 0)

                val environment =
                    org.acestream.engine.inproc.EnvironmentSnapshot(
                        deviceId = android.provider.Settings.Secure.getString(
                            contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: "unknown",

                        legacyDeviceId = null,

                        aceStreamHome = externalDir.absolutePath,

                        appDir = filesDir.absolutePath,

                        appId = packageName,

                        appVersionCode =
                            if (android.os.Build.VERSION.SDK_INT >= 28) {
                                packageInfo.longVersionCode.toInt()
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo.versionCode
                            },

                        appVersionName =
                            packageInfo.versionName ?: "unknown",

                        arch =
                            android.os.Build.SUPPORTED_ABIS.firstOrNull()
                                ?: "unknown",

                        canSignRequests = false,

                        isAndroidTv = false,

                        hasBrowser = true,

                        hasWebView = true,

                        poTokenServerPort = -1,

                        uploadDirectory =
                            externalDir.absolutePath
                    )

                org.acestream.engine.inproc.EngineCallbacks
                    .init(environment)

                val result =
                    org.acestream.engine.inproc.EngineNative
                        .initializePython(
                            layout.pythonHome,
                            layout.engineDir
                        )

                android.util.Log.d(
                    "CronosTV/TestInproc",
                    "initializePython=$result"
                )

                if (result == 0) {
                    android.util.Log.d(
                        "CronosTV/TestInproc",
                        "pythonVersionFromLib=" +
                                org.acestream.engine.inproc.EngineNative
                                    .pythonVersionFromLib()
                    )

                    android.util.Log.d(
                        "CronosTV/TestInproc",
                        "pythonSysVersion=" +
                                org.acestream.engine.inproc.EngineNative
                                    .pythonSysVersion()
                    )

                    android.util.Log.d(
                        "CronosTV/TestInproc",
                        "tryImport acestreamengine=" +
                                org.acestream.engine.inproc.EngineNative
                                    .tryImport("acestreamengine")
                    )

                    val mainScript =
                        java.io.File(
                            layout.engineDir,
                            "main.py"
                        )

                    android.util.Log.d(
                        "CronosTV/TestInproc",
                        "main.py exists=${mainScript.exists()} path=${mainScript.absolutePath}"
                    )

                    val engineArgs = arrayOf(
                        "--api-port",
                        "62062",
                        "--http-port",
                        "6878",
                        "--log-file",
                        java.io.File(
                            externalDir,
                            "acestream.log"
                        ).absolutePath
                    )

                    android.util.Log.d(
                        "CronosTV/TestInproc",
                        "Ejecutando main.py args=${engineArgs.contentToString()}"
                    )

                    val mainResult =
                        org.acestream.engine.inproc.EngineNative
                            .runMain(
                                mainScript.absolutePath,
                                engineArgs
                            )

                    android.util.Log.d(
                        "CronosTV/TestInproc",
                        "runMain terminado rc=$mainResult"
                    )
                }

            } catch (e: Throwable) {
                android.util.Log.e(
                    "CronosTV/TestInproc",
                    "Error prueba INPROC",
                    e
                )
            }
        }

        recyclerView = findViewById(R.id.rvCanales)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = CanalAdapter(canales) { canal ->
            startActivity(PlayerActivity.intent(this, canal.url))
        }

        recyclerView.adapter = adapter

        cargarLista("http://212.227.134.237:8080/mi_lista_filtrada.m3u")
    }

    private fun cargarLista(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("CronosTV", "📡 Descargando lista desde: $url")
                val contenido = URL(url).readText()
                android.util.Log.d("CronosTV", "✅ Lista descargada: ${contenido.length} caracteres")
                val canalesParseados = M3UParser.parsear(contenido)
                android.util.Log.d("CronosTV", "📺 Canales parseados: ${canalesParseados.size}")
                withContext(Dispatchers.Main) {
                    canales.clear()
                    canales.addAll(canalesParseados)
                    adapter.notifyDataSetChanged()
                    android.util.Log.d("CronosTV", "✅ Adapter notificado, canales en lista: ${canales.size}")
                }
            } catch (e: Exception) {
                android.util.Log.e("CronosTV", "❌ Error al cargar la lista: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}