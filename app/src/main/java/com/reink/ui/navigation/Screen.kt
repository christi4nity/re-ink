package com.reink.ui.navigation

sealed class Screen(val route: String, val label: String) {
    data object Feed : Screen("feed", "Feed")
    data object ReadLater : Screen("read_later", "Read Later")
    data object Settings : Screen("settings", "Settings")
    data object Reader : Screen("reader/{itemType}/{itemId}", "Reader") {
        fun createRoute(itemType: String, itemId: Long): String =
            "reader/$itemType/$itemId"
    }
}
