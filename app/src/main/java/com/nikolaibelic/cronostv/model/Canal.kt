package com.nikolaibelic.cronostv.model

data class Canal(
    val nombre: String,
    val url: String,
    val logo: String? = null,
    val grupo: String = "Sin grupo"
)