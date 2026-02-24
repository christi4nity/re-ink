package com.reink.data.model

data class ReadingPreferences(
    val fontFamily: String = "Literata",
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val marginHorizontal: Int = 16,
    val textAlign: String = "left",
    val paginationMode: String = "scroll",
)
