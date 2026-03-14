package com.reink.data.email

import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part

/** Extract the HTML body from a MIME message, traversing multipart structures. */
fun extractHtmlBody(part: Part): String? {
    if (part.isMimeType("text/html")) {
        return part.content as? String
    }
    if (part.isMimeType("multipart/*")) {
        val multipart = part.content as? Multipart ?: return null
        for (i in 0 until multipart.count) {
            val result = extractHtmlBody(multipart.getBodyPart(i))
            if (result != null) return result
        }
    }
    return null
}

/** Extract the Message-ID header, stripping angle brackets. */
fun getMessageId(message: Message): String? =
    message.getHeader("Message-ID")?.firstOrNull()
        ?.trim()
        ?.removePrefix("<")
        ?.removeSuffix(">")
