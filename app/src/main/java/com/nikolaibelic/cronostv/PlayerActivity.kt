package com.nikolaibelic.cronostv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView

    private var player: ExoPlayer? = null
    private var url: String = ""

    // Datos de la sesión que devuelve el Engine.
    // De momento solo necesitamos playbackUrl para reproducir.
    private var playbackSessionId: String? = null
    private var commandUrl: String? = null
    private var statUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)

        url = intent.getStringExtra(EXTRA_URL) ?: run {
            Toast.makeText(
                this,
                "URL no válida",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        Log.d(
            "CronosTV/Player",
            "URL recibida del canal: $url"
        )

        when {
            url.startsWith("acestream://") -> {
                reproducirAceStream(url)
            }

            url.startsWith("http://127.0.0.1:6878/ace/getstream") -> {
                val contentId = android.net.Uri.parse(url)
                    .getQueryParameter("id")

                if (contentId.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        "Content ID AceStream no válido",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    reproducirAceStream("acestream://$contentId")
                }
            }

            else -> {
                reproducir(url)
            }
        }
    }

    private fun reproducirAceStream(acestreamUrl: String) {

        val contentId =
            acestreamUrl.removePrefix("acestream://")

        CoroutineScope(Dispatchers.IO).launch {

            val engineReady = esperarEngineHttp()

            if (!engineReady) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "El Engine no terminó de iniciar",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            val clientSessionId =
                nextClientSessionId.getAndIncrement()

            val sessionUrl =
                "http://127.0.0.1:6878/ace/getstream" +
                        "?format=json" +
                        "&sid=acestream-player" +
                        "&_idx=0" +
                        "&stream_id=0" +
                        "&content_id=$contentId" +
                        "&client_session_id=$clientSessionId" +
                        "&use_timeshift=1" +
                        "&manifest_p2p_wait_timeout=10" +
                        "&proxy_vast_response=1" +
                        "&force_ads=1" +
                        "&gdpr_consent=1" +
                        "&stop_prev_read_thread=1"

            Log.d(
                "CronosTV/Player",
                "🎬 Solicitando sesión AceStream: $sessionUrl"
            )

            val session =
                try {
                    obtenerSesionAceStream(sessionUrl)
                } catch (e: Exception) {

                    Log.e(
                        "CronosTV/Player",
                        "❌ Error obteniendo sesión AceStream",
                        e
                    )

                    null
                }

            if (session == null) {

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "No se pudo iniciar la sesión AceStream",
                        Toast.LENGTH_LONG
                    ).show()
                }

                return@launch
            }

            playbackSessionId =
                session.playbackSessionId

            commandUrl =
                session.commandUrl

            statUrl =
                session.statUrl

            Log.d(
                "CronosTV/Player",
                "✅ Sesión AceStream obtenida"
            )

            Log.d(
                "CronosTV/Player",
                "playback_url=${session.playbackUrl}"
            )

            Log.d(
                "CronosTV/Player",
                "playback_session_id=${session.playbackSessionId}"
            )

            Log.d(
                "CronosTV/Player",
                "command_url=${session.commandUrl}"
            )

            Log.d(
                "CronosTV/Player",
                "▶️ Reproduciendo directamente: ${session.playbackUrl}"
            )

            withContext(Dispatchers.Main) {
                reproducir(session.playbackUrl)
            }
        }
    }

    private fun obtenerSesionAceStream(
        sessionUrl: String
    ): AceStreamSession? {

        val connection =
            URL(sessionUrl).openConnection()
                    as HttpURLConnection

        return try {

            connection.requestMethod = "GET"

            connection.connectTimeout = 5000
            connection.readTimeout = 20000

            connection.setRequestProperty(
                "User-Agent",
                "curl/7.80.0"
            )

            connection.setRequestProperty(
                "Accept",
                "*/*"
            )

            connection.setRequestProperty(
                "Connection",
                "close"
            )

            Log.d(
                "CronosTV/Player",
                "Request headers=${connection.requestProperties}"
            )

            val responseCode =
                connection.responseCode

            Log.d(
                "CronosTV/Player",
                "Respuesta sesión HTTP: $responseCode"
            )

            val inputStream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                inputStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""

            Log.d(
                "CronosTV/Player",
                "Respuesta Engine: $responseText"
            )

            if (responseCode !in 200..299) {
                return null
            }

            val json =
                JSONObject(responseText)

            if (!json.isNull("error")) {

                Log.e(
                    "CronosTV/Player",
                    "Engine devolvió error: ${
                        json.optString("error")
                    }"
                )

                return null
            }

            val response =
                json.optJSONObject("response")
                    ?: return null

            val playbackUrl =
                response.optString(
                    "playback_url",
                    ""
                )

            if (playbackUrl.isBlank()) {

                Log.e(
                    "CronosTV/Player",
                    "El Engine no devolvió playback_url"
                )

                return null
            }

            AceStreamSession(
                playbackUrl = playbackUrl,

                playbackSessionId =
                    response.optString(
                        "playback_session_id",
                        null
                    ),

                commandUrl =
                    response.optString(
                        "command_url",
                        null
                    ),

                statUrl =
                    response.optString(
                        "stat_url",
                        null
                    )
            )

        } finally {

            connection.disconnect()
        }
    }

    private suspend fun esperarEngineHttp(): Boolean {

        repeat(100) {

            try {

                Socket().use { socket ->

                    socket.connect(
                        InetSocketAddress(
                            "127.0.0.1",
                            6878
                        ),
                        200
                    )
                }

                Log.d(
                    "CronosTV/Player",
                    "✅ Puerto 6878 listo"
                )

                return true

            } catch (_: Exception) {

                delay(100)
            }
        }

        return false
    }

    private fun reproducir(
        streamUrl: String
    ) {

        player?.release()

        player =
            ExoPlayer.Builder(this)
                .build()
                .also { exoPlayer ->

                    playerView.player =
                        exoPlayer

                    exoPlayer.addListener(
                        object : Player.Listener {

                            override fun onPlaybackStateChanged(
                                playbackState: Int
                            ) {

                                when (playbackState) {

                                    Player.STATE_BUFFERING -> {
                                        Log.d(
                                            "CronosTV/Player",
                                            "Buffering..."
                                        )
                                    }

                                    Player.STATE_READY -> {
                                        Log.d(
                                            "CronosTV/Player",
                                            "✅ Stream preparado"
                                        )
                                    }

                                    Player.STATE_ENDED -> {
                                        Log.d(
                                            "CronosTV/Player",
                                            "Stream finalizado"
                                        )
                                    }
                                }
                            }

                            override fun onPlayerError(
                                error:
                                androidx.media3.common.PlaybackException
                            ) {

                                Log.e(
                                    "CronosTV/Player",
                                    "❌ Error Media3",
                                    error
                                )
                            }
                        }
                    )

                    val mediaItem =
                        MediaItem.fromUri(
                            streamUrl
                        )

                    exoPlayer.setMediaItem(
                        mediaItem
                    )

                    exoPlayer.prepare()

                    exoPlayer.playWhenReady =
                        true
                }
    }

    override fun onDestroy() {

        playerView.player = null

        player?.release()
        player = null

        super.onDestroy()
    }

    private data class AceStreamSession(

        val playbackUrl: String,

        val playbackSessionId: String?,

        val commandUrl: String?,

        val statUrl: String?
    )

    companion object {

        private const val EXTRA_URL =
            "url"

        private val nextClientSessionId =
            AtomicInteger(1)

        fun intent(
            context: Context,
            url: String
        ): Intent {

            return Intent(
                context,
                PlayerActivity::class.java
            ).apply {

                putExtra(
                    EXTRA_URL,
                    url
                )
            }
        }
    }
}