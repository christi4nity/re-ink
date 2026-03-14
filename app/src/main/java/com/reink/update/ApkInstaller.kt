package com.reink.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.reink.di.PlainHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @PlainHttpClient private val httpClient: OkHttpClient,
) {
    suspend fun downloadAndInstall(downloadUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "reink-update.apk")

            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: ${response.code}")
            }

            response.body?.byteStream()?.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw RuntimeException("Empty response body")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }
    }
}
