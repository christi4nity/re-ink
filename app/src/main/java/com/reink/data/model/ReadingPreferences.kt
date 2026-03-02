package com.reink.data.model

data class ReadingPreferences(
    val fontFamily: String = "Source Serif 4",
    val fontSize: Int = 32,
    val lineHeight: Float = 1.4f,
    val marginHorizontal: Int = 144,
    val textAlign: String = "justify",
    val paginationMode: String = "paginated",
)
