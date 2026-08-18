package com.nikolaibelic.cronostv.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        holder.itemView.setOnClickListener { onCanalClick(canal) }  // <- ¡Corregido!
    }

    override fun getItemCount(): Int = canales.size

    class CanalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombre: TextView = itemView.findViewById(R.id.tvNombreCanal)
        private val tvGrupo: TextView = itemView.findViewById(R.id.tvGrupoCanal)

        fun bind(canal: Canal) {
            tvNombre.text = canal.nombre
            tvGrupo.text = canal.grupo
        }
    }
}