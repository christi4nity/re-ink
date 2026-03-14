package com.reink.data.email

import android.util.Log
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress

/**
 * Ordered chain of email parsers. First parser whose [EmailParser.canParse]
 * returns true handles the message. Generic parser should be last (catch-all).
 */
class EmailParserChain(private val parsers: List<EmailParser>) {

    /** Called once before a sync pass to let parsers refresh cached state. */
    suspend fun refreshParsers() {
        for (parser in parsers) {
            if (parser is Refreshable) parser.refresh()
        }
    }

    /** Route the message to the first matching parser. Returns null if none match. */
    fun parse(message: Message): EmailArticle? {
        val sender = try {
            (message.from?.firstOrNull() as? InternetAddress)?.address ?: "unknown"
        } catch (_: Exception) { "unknown" }

        for (parser in parsers) {
            if (parser.canParse(message)) {
                val name = parser::class.simpleName ?: "Unknown"
                Log.d("ReInk", "EmailParserChain: $name matched message from $sender")
                return parser.parse(message)
            }
        }
        Log.d("ReInk", "EmailParserChain: no parser matched message from $sender")
        return null
    }
}
