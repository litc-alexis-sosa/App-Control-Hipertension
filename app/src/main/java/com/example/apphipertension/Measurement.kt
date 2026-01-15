package com.example.apphipertension

data class Measurement(
    val date: String = "",
    val time: String = "",
    val sistolica: Int = 0,
    val diastolica: Int = 0,
    val pulso: Int = 0,
    val nota: String = ""
)