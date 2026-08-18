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

        // Prueba temporal del Engine nativo
        try {
            val abi = org.acestream.engine.python.PyEmbedded.getCompiledABI()
            android.util.Log.d("CronosTV", "ABI Engine: $abi")
        } catch (e: Throwable) {
            android.util.Log.e("CronosTV", "Error probando Engine nativo", e)
        }

        CoroutineScope(Dispatchers.IO).launch {

            val ok =
                com.nikolaibelic.cronostv.engine.EngineBootstrap
                    .ensureUnpacked(this@MainActivity)

            android.util.Log.d(
                "CronosTV",
                "Engine runtime preparado: $ok"
            )

            if (ok) {
                com.nikolaibelic.cronostv.engine.EngineManager.start(
                    applicationContext
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