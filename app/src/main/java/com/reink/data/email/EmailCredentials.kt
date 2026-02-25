package com.reink.data.email

data class EmailCredentials(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val folderName: String = "INBOX",
)

interface EmailCredentialsStore {
    fun get(): EmailCredentials?
    fun save(credentials: EmailCredentials)
    fun clear()
    fun isConfigured(): Boolean
}
