package com.reink.data.email

import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.ReceivedDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImapEmailContentSource @Inject constructor(
    private val credentialsStore: EmailCredentialsStore,
    private val parserChain: EmailParserChain,
) : EmailContentSource {

    override suspend fun fetchNewArticles(sinceTimestamp: Long): Result<List<EmailArticle>> =
        withContext(Dispatchers.IO) {
            val credentials = credentialsStore.get()
                ?: return@withContext Result.failure(IllegalStateException("Email not configured"))

            var store: Store? = null
            var folder: Folder? = null
            try {
                store = connectStore(credentials)
                folder = store.getFolder(credentials.folderName).apply {
                    open(Folder.READ_ONLY)
                }

                val sinceDate = Date(sinceTimestamp)
                val searchTerm = ReceivedDateTerm(ComparisonTerm.GE, sinceDate)

                val messages = folder.search(searchTerm)

                val fetchProfile = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.CONTENT_INFO)
                }
                folder.fetch(messages, fetchProfile)

                parserChain.refreshParsers()

                var parsed = 0
                var failed = 0
                val articles = messages.mapNotNull { message ->
                    try {
                        val result = parserChain.parse(message)
                        if (result != null) parsed++ else failed++
                        result
                    } catch (_: Exception) {
                        failed++
                        null
                    }
                }

                Result.success(articles)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try { folder?.close(false) } catch (_: Exception) {}
                try { store?.close() } catch (_: Exception) {}
            }
        }

    override fun streamNewArticles(sinceTimestamp: Long): Flow<EmailArticle> = flow {
        val credentials = credentialsStore.get()
            ?: throw IllegalStateException("Email not configured")

        var store: Store? = null
        var folder: Folder? = null
        try {
            store = connectStore(credentials)
            folder = store.getFolder(credentials.folderName).apply {
                open(Folder.READ_ONLY)
            }

            val sinceDate = Date(sinceTimestamp)
            val searchTerm = ReceivedDateTerm(ComparisonTerm.GE, sinceDate)
            val messages = folder.search(searchTerm)

            // Prefetch envelopes (headers) — fast, no body download
            val fetchProfile = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.CONTENT_INFO)
            }
            folder.fetch(messages, fetchProfile)

            parserChain.refreshParsers()

            // Process newest first so fresh articles appear immediately
            for (message in messages.reversed()) {
                try {
                    val result = parserChain.parse(message)
                    if (result != null) {
                        emit(result)
                    }
                } catch (_: Exception) {
                }
            }
        } finally {
            try { folder?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(): Result<String> =
        withContext(Dispatchers.IO) {
            val credentials = credentialsStore.get()
                ?: return@withContext Result.failure(IllegalStateException("Email not configured"))

            var store: Store? = null
            var folder: Folder? = null
            try {
                store = connectStore(credentials)
                folder = store.getFolder(credentials.folderName).apply {
                    open(Folder.READ_ONLY)
                }
                val count = folder.messageCount
                Result.success("Connected. $count messages in ${credentials.folderName}.")
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try { folder?.close(false) } catch (_: Exception) {}
                try { store?.close() } catch (_: Exception) {}
            }
        }

    private fun connectStore(credentials: EmailCredentials): Store {
        val props = Properties().apply {
            put("mail.imap.host", credentials.host)
            put("mail.imap.port", credentials.port.toString())
            put("mail.imap.ssl.enable", "true")
            put("mail.imap.connectiontimeout", "10000")
            put("mail.imap.timeout", "10000")
        }
        val session = Session.getInstance(props)
        return session.getStore("imap").apply {
            connect(credentials.host, credentials.port, credentials.username, credentials.password)
        }
    }
}
