package com.reink.data.email

import jakarta.mail.Message

/**
 * Pluggable email parser. Implementations detect and parse specific email formats
 * (e.g., Substack, Ghost, generic). Registered in an ordered chain — first match wins.
 *
 * To add a new parser:
 * 1. Create a class implementing this interface
 * 2. Register it in EmailModule.kt's parser chain (between Substack and generic)
 */
interface EmailParser {
    /**
     * Inspect message headers/sender to determine if this parser can handle it.
     * Must be cheap — no body parsing. Called for every message in the inbox.
     */
    fun canParse(message: Message): Boolean

    /**
     * Parse the message into an EmailArticle. Returns null if parsing fails.
     * Only called after [canParse] returns true.
     */
    fun parse(message: Message): EmailArticle?
}
