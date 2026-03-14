package com.reink.data.remote

import com.reink.BuildConfig
import com.reink.data.model.AppUpdate
import com.reink.di.PlainHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
)

@Singleton
class UpdateChecker @Inject constructor(
    @PlainHttpClient private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun check(): Result<AppUpdate?> = try {
        Result.success(doCheck())
    } catch (e: Exception) {
        android.util.Log.e("ReInk", "Update check failed", e)
        Result.failure(e)
    }

    private fun doCheck(): AppUpdate? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("GitHub API returned ${response.code}")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val release = json.decodeFromString<GitHubRelease>(body)
        val latestVersion = release.tag_name.removePrefix("v")

        if (!isNewer(latestVersion, BuildConfig.VERSION_NAME)) {
            return null
        }

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return null

        return AppUpdate(
            versionName = latestVersion,
            downloadUrl = apkAsset.browser_download_url,
            releaseNotes = release.body,
        )
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/christi4nity/re-ink/releases/latest"

        fun isNewer(latest: String, current: String): Boolean {
            val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }
    }
}
