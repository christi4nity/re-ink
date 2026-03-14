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
    private val updatesDir: File
        get() = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }

    private val apkFile: File
        get() = File(updatesDir, "reink-update.apk")

    val isDownloaded: Boolean
        get() = apkFile.exists() && apkFile.length() > 0

    suspend fun download(downloadUrl: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: ${response.code}")
            }

            val tempFile = File(updatesDir, "reink-update.apk.tmp")
            response.body?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw RuntimeException("Empty response body")

            tempFile.renameTo(apkFile)
            apkFile
        }
    }

    fun install() {
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

    suspend fun downloadAndInstall(downloadUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        download(downloadUrl).map { install() }
    }

    fun cleanupDownload() {
        apkFile.delete()
    }
}
