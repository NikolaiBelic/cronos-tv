package com.nikolaibelic.cronostv.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nikolaibelic.cronostv.R
import com.nikolaibelic.cronostv.model.Canal

class CanalAdapter(
    private val canales: List<Canal>,
    private val onCanalClick: (Canal) -> Unit
) : RecyclerView.Adapter<CanalAdapter.CanalViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_canal, parent, false)

        return CanalViewHolder(view)
    }

    override fun onBindViewHolder(holder: CanalViewHolder, position: Int) {
        val canal = canales[position]

        holder.bind(canal)
        holder.itemView.setOnClickListener {
            onCanalClick(canal)
        }
    }

    override fun getItemCount(): Int = canales.size

    class CanalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvNombre: TextView =
            itemView.findViewById(R.id.tvNombreCanal)

        private val tvCodigo: TextView =
            itemView.findViewById(R.id.tvCodigoCanal)

        private val tvGrupo: TextView =
            itemView.findViewById(R.id.tvGrupoCanal)

        private val ivLogo: ImageView =
            itemView.findViewById(R.id.ivLogoCanal)

        fun bind(canal: Canal) {

            tvNombre.text = canal.nombre
            tvGrupo.text = canal.grupo

            tvCodigo.text =
                obtenerContentId(canal.url)

            // De momento ocultamos el logo si no existe.
            // Luego lo cargamos con Coil/Glide.
            if (canal.logo.isNullOrBlank()) {
                ivLogo.visibility = View.GONE
            } else {
                ivLogo.visibility = View.VISIBLE
            }
        }

        private fun obtenerContentId(url: String): String {

            if (url.startsWith("acestream://")) {
                return url.removePrefix("acestream://")
            }

            return try {

                val uri = Uri.parse(url)

                uri.getQueryParameter("id")
                    ?: uri.getQueryParameter("content_id")
                    ?: url

            } catch (_: Exception) {
                url
            }
        }
    }
}