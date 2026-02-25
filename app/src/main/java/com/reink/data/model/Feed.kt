package com.reink.data.model

data class Feed(
    val id: Long = 0,
    val title: String,
    val url: String,
    val siteUrl: String = "",
    val imageUrl: String? = null,
    val requiresAuth: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val authToken: String? = null,
    val substackSubdomain: String? = null,
    val enabledSectionSlugs: String? = null,
    val emailSenderPattern: String? = null,
) {
    /**
     * Returns the URL to use for fetching RSS content.
     * If token auth is available, uses the canonical substack.com URL with token.
     * Otherwise falls back to the stored feed URL.
     */
    val authenticatedFeedUrl: String
        get() = if (authToken != null && substackSubdomain != null) {
            "https://$substackSubdomain.substack.com/feed?token=$authToken"
        } else {
            url
        }

    /** Parsed list of enabled section slugs, empty if no section filtering. */
    val sectionSlugs: List<String>
        get() = enabledSectionSlugs
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /**
     * Returns section-specific feed URLs for each enabled section.
     * Only meaningful when both authToken/subdomain and sections are set.
     */
    fun authenticatedSectionUrls(): List<String> {
        if (authToken == null || substackSubdomain == null) return emptyList()
        return sectionSlugs.map { slug ->
            "https://$substackSubdomain.substack.com/feed/section/$slug?token=$authToken"
        }
    }
}
