package com.nikolaibelic.cronostv.parser

import com.nikolaibelic.cronostv.model.Canal

object M3UParser {
    fun parsear(contenido: String): List<Canal> {
        val canales = mutableListOf<Canal>()
        val lineas = contenido.split("\n")
        var i = 0

        while (i < lineas.size) {
            val linea = lineas[i].trim()
            if (linea.startsWith("#EXTINF:")) {
                // Extraer grupo y nombre
                val grupo = extractGroup(linea)
                val nombre = extractName(linea)

                // Buscar la URL (siguiente línea no vacía)
                i++
                while (i < lineas.size && lineas[i].trim().isEmpty()) {
                    i++
                }
                if (i < lineas.size) {
                    val url = lineas[i].trim()
                    canales.add(Canal(nombre, url, null, grupo))
                }
            }
            i++
        }
        return canales
    }

    private fun extractGroup(linea: String): String {
        val regex = Regex("""group-title="([^"]*)"""")
        return regex.find(linea)?.groupValues?.get(1) ?: "Sin grupo"
    }

    private fun extractName(linea: String): String {
        val partes = linea.split(",")
        return if (partes.size > 1) partes.last().trim() else "Canal sin nombre"
    }
}