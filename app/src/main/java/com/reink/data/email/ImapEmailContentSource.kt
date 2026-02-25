package com.reink.data.email

import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.FromStringTerm
import jakarta.mail.search.HeaderTerm
import jakarta.mail.search.OrTerm
import jakarta.mail.search.ReceivedDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImapEmailContentSource @Inject constructor(
    private val credentialsStore: EmailCredentialsStore,
    private val parser: SubstackEmailParser,
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
                val searchTerm = jakarta.mail.search.AndTerm(
                    ReceivedDateTerm(ComparisonTerm.GE, sinceDate),
                    OrTerm(
                        FromStringTerm("substack.com"),
                        HeaderTerm("List-Id", "substack.com"),
                    ),
                )

                val messages = folder.search(searchTerm)

                val fetchProfile = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.CONTENT_INFO)
                }
                folder.fetch(messages, fetchProfile)

                val articles = messages.mapNotNull { message ->
                    try {
                        parser.parse(message)
                    } catch (_: Exception) {
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
