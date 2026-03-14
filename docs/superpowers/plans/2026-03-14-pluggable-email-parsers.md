# Pluggable Email Parser System — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable non-Substack email ingestion by extracting an `EmailParser` interface, adding a `GenericEmailParser` for allowlisted sender domains, and wiring it through an ordered parser chain.

**Architecture:** Parser chain pattern — ordered list of `EmailParser` implementations, first `canParse` match wins. Substack parser first (specific), generic parser last (catch-all). Chain injected via Hilt, replaces direct `SubstackEmailParser` dependency in `ImapEmailContentSource`.

**Tech Stack:** Kotlin, Hilt DI, DataStore (allowlist storage), Jsoup (HTML cleanup), Jakarta Mail, Jetpack Compose (settings UI)

**Spec:** `docs/superpowers/specs/2026-03-14-pluggable-email-parsers-design.md`

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/com/reink/data/email/EmailParser.kt` | Create | Interface: `canParse` + `parse` |
| `app/src/main/java/com/reink/data/email/EmailParserUtils.kt` | Create | Shared utilities: `extractHtmlBody`, `getMessageId` |
| `app/src/main/java/com/reink/data/email/EmailParserChain.kt` | Create | Ordered chain with debug logging + `refreshParsers` |
| `app/src/main/java/com/reink/data/email/GenericEmailParser.kt` | Create | Allowlist-based parser, minimal HTML cleanup |
| `app/src/main/java/com/reink/data/email/SubstackEmailParser.kt` | Modify | Implement `EmailParser`, use shared utils |
| `app/src/main/java/com/reink/data/email/ImapEmailContentSource.kt` | Modify | Use `EmailParserChain` instead of `SubstackEmailParser` |
| `app/src/main/java/com/reink/data/repository/PreferencesRepository.kt` | Modify | Add allowed sender domain storage |
| `app/src/main/java/com/reink/data/repository/EmailSyncRepository.kt` | Modify | Handle generic emails in `getOrCreateFeedForSender` |
| `app/src/main/java/com/reink/di/EmailModule.kt` | Modify | Provide `EmailParserChain` |
| `app/src/main/java/com/reink/ui/settings/AllowedSendersSection.kt` | Create | Settings UI for domain allowlist |
| `app/src/main/java/com/reink/ui/settings/SettingsViewModel.kt` | Modify | Expose/manage allowed domains in state |
| `app/src/main/java/com/reink/ui/settings/SettingsScreen.kt` | Modify | Add `AllowedSendersSection` |

---

## Chunk 1: Core Parser Infrastructure

### Task 1: Create `EmailParser` interface

**Files:**
- Create: `app/src/main/java/com/reink/data/email/EmailParser.kt`

- [ ] **Step 1: Create the interface file**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/reink/data/email/EmailParser.kt
git commit -m "feat: add EmailParser interface for pluggable email parsing"
```

---

### Task 2: Extract shared utilities from `SubstackEmailParser`

**Files:**
- Create: `app/src/main/java/com/reink/data/email/EmailParserUtils.kt`
- Modify: `app/src/main/java/com/reink/data/email/SubstackEmailParser.kt`

- [ ] **Step 1: Create `EmailParserUtils.kt` with extracted functions**

Extract `extractHtmlBody` (lines 228-240) and `getMessageId` (lines 242-246) from `SubstackEmailParser.kt`. These are currently `private` methods — make them package-level functions:

```kotlin
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
```

- [ ] **Step 2: Update `SubstackEmailParser` to use shared utils**

In `SubstackEmailParser.kt`:
- Remove the `private fun extractHtmlBody(part: Part)` method (lines 228-240)
- Remove the `private fun getMessageId(message: Message)` method (lines 242-246)
- The calls to `extractHtmlBody(message)` and `getMessageId(message)` on lines 24 and 26 now resolve to the package-level functions in `EmailParserUtils.kt` (same package, no import needed)

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/reink/data/email/EmailParserUtils.kt app/src/main/java/com/reink/data/email/SubstackEmailParser.kt
git commit -m "refactor: extract shared email parsing utils from SubstackEmailParser"
```

---

### Task 3: Make `SubstackEmailParser` implement `EmailParser`

**Files:**
- Modify: `app/src/main/java/com/reink/data/email/SubstackEmailParser.kt`

- [ ] **Step 1: Add `canParse` and implement the interface**

Change the class declaration at line 13:

```kotlin
// Before:
class SubstackEmailParser @Inject constructor() {

// After:
class SubstackEmailParser @Inject constructor() : EmailParser {
```

Add the `canParse` method before the existing `parse`:

```kotlin
override fun canParse(message: Message): Boolean {
    val listId = message.getHeader("List-Id")?.firstOrNull() ?: return false
    val subdomain = extractSubdomain(listId) ?: return false
    return subdomain != "www"
}
```

Add `override` to the existing `parse` method signature at line 15:

```kotlin
// Before:
fun parse(message: Message): EmailArticle? {

// After:
override fun parse(message: Message): EmailArticle? {
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/reink/data/email/SubstackEmailParser.kt
git commit -m "refactor: SubstackEmailParser implements EmailParser interface"
```

---

### Task 4: Create `EmailParserChain`

**Files:**
- Create: `app/src/main/java/com/reink/data/email/EmailParserChain.kt`

- [ ] **Step 1: Create the chain class**

```kotlin
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
            if (parser is GenericEmailParser) parser.refreshAllowlist()
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
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (note: `GenericEmailParser` doesn't exist yet — the `is` check will compile but never match until Task 6)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/reink/data/email/EmailParserChain.kt
git commit -m "feat: add EmailParserChain for ordered parser dispatch"
```

---

### Task 5: Wire chain into DI and `ImapEmailContentSource`

**Files:**
- Modify: `app/src/main/java/com/reink/di/EmailModule.kt`
- Modify: `app/src/main/java/com/reink/data/email/ImapEmailContentSource.kt`

- [ ] **Step 1: Update `EmailModule.kt` to provide the chain**

Replace the full file content. The module needs both `@Binds` (abstract) and `@Provides` (companion object):

```kotlin
package com.reink.di

import com.reink.data.email.EmailContentSource
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.email.EmailParserChain
import com.reink.data.email.EncryptedEmailCredentialsStore
import com.reink.data.email.ImapEmailContentSource
import com.reink.data.email.SubstackEmailParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmailModule {

    @Binds
    @Singleton
    abstract fun bindEmailCredentialsStore(
        impl: EncryptedEmailCredentialsStore,
    ): EmailCredentialsStore

    @Binds
    @Singleton
    abstract fun bindEmailContentSource(
        impl: ImapEmailContentSource,
    ): EmailContentSource

    companion object {
        @Provides
        @Singleton
        fun provideEmailParserChain(
            substackParser: SubstackEmailParser,
        ): EmailParserChain = EmailParserChain(
            listOf(substackParser)
        )
    }
}
```

Note: only `SubstackEmailParser` in the chain for now. `GenericEmailParser` is added in Task 6 after it's created. This keeps the app working identically during intermediate commits.

- [ ] **Step 2: Update `ImapEmailContentSource.kt` to use chain**

Change the constructor parameter (line 21-23):

```kotlin
// Before:
@Singleton
class ImapEmailContentSource @Inject constructor(
    private val credentialsStore: EmailCredentialsStore,
    private val parser: SubstackEmailParser,
) : EmailContentSource {

// After:
@Singleton
class ImapEmailContentSource @Inject constructor(
    private val credentialsStore: EmailCredentialsStore,
    private val parserChain: EmailParserChain,
) : EmailContentSource {
```

In `fetchNewArticles` (line 51), add `parserChain.refreshParsers()` before the message loop, and change `parser.parse` to `parserChain.parse`:

```kotlin
// Add before line 51 (before `var parsed = 0`):
parserChain.refreshParsers()

// Line 53: change parser.parse to parserChain.parse
val result = parserChain.parse(message)
```

In `streamNewArticles` (line 95), add refresh before the loop and change the parse call:

```kotlin
// Add before line 95 (before `for (message in messages.reversed())`):
parserChain.refreshParsers()

// Line 98: change parser.parse to parserChain.parse
val result = parserChain.parse(message)
```

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/reink/di/EmailModule.kt app/src/main/java/com/reink/data/email/ImapEmailContentSource.kt
git commit -m "refactor: wire EmailParserChain into DI and ImapEmailContentSource"
```

---

## Chunk 2: Generic Parser + Allowlist

### Task 6: Add allowed sender domains to `PreferencesRepository`

**Files:**
- Modify: `app/src/main/java/com/reink/data/repository/PreferencesRepository.kt`

- [ ] **Step 1: Add the DataStore key and methods**

Add to the companion object (after line 44, the `KEY_UPDATE_DISMISSED_VERSION` line):

```kotlin
private val KEY_ALLOWED_SENDER_DOMAINS = stringSetPreferencesKey("allowed_sender_domains")
```

Add the import at the top of the file (alongside the other `*PreferencesKey` imports):

```kotlin
import androidx.datastore.preferences.core.stringSetPreferencesKey
```

Add these methods at the end of the class (before the closing brace):

```kotlin
fun observeAllowedSenderDomains(): Flow<Set<String>> =
    dataStore.data.map { prefs -> prefs[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet() }

suspend fun getAllowedSenderDomains(): Set<String> =
    dataStore.data.first()[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()

suspend fun addAllowedSenderDomain(domain: String) {
    val cleaned = domain
        .trim()
        .lowercase()
        .substringAfter("@")  // Handle user@domain.com -> domain.com
    if (cleaned.isBlank() || '.' !in cleaned) return  // Basic validation
    dataStore.edit { store ->
        val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
        store[KEY_ALLOWED_SENDER_DOMAINS] = current + cleaned
    }
}

suspend fun removeAllowedSenderDomain(domain: String) {
    dataStore.edit { store ->
        val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
        store[KEY_ALLOWED_SENDER_DOMAINS] = current - domain.lowercase()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/reink/data/repository/PreferencesRepository.kt
git commit -m "feat: add allowed sender domain storage to PreferencesRepository"
```

---

### Task 7: Create `GenericEmailParser`

**Files:**
- Create: `app/src/main/java/com/reink/data/email/GenericEmailParser.kt`

- [ ] **Step 1: Create the parser**

```kotlin
package com.reink.data.email

import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import com.reink.data.repository.PreferencesRepository
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses emails from allowlisted sender domains. Performs minimal HTML cleanup —
 * strips head/style/script tags and tracking pixels, preserves all content as-is.
 *
 * The allowlist is cached in memory and refreshed once per sync pass via
 * [refreshAllowlist], called by [EmailParserChain.refreshParsers].
 */
@Singleton
class GenericEmailParser @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : EmailParser {

    @Volatile
    private var cachedDomains: Set<String> = emptySet()

    /** Refresh the cached allowlist from DataStore. Call once before each sync pass. */
    suspend fun refreshAllowlist() {
        cachedDomains = preferencesRepository.getAllowedSenderDomains()
    }

    override fun canParse(message: Message): Boolean {
        if (cachedDomains.isEmpty()) return false
        val fromAddress = (message.from?.firstOrNull() as? InternetAddress)
            ?.address ?: return false
        val domain = fromAddress.substringAfter("@", "")
        return cachedDomains.any { domain.equals(it, ignoreCase = true) }
    }

    override fun parse(message: Message): EmailArticle? {
        val fromAddress = (message.from?.firstOrNull() as? InternetAddress)
            ?: return null
        val messageId = getMessageId(message) ?: return null
        val htmlBody = extractHtmlBody(message) ?: return null

        val cleanHtml = cleanEmailHtml(htmlBody)

        return EmailArticle(
            subject = message.subject ?: "",
            subtitle = "",
            senderAddress = fromAddress.address ?: "",
            senderName = fromAddress.personal ?: fromAddress.address ?: "",
            substackSubdomain = "",
            receivedAt = (message.receivedDate ?: message.sentDate)?.time
                ?: System.currentTimeMillis(),
            contentHtml = cleanHtml,
            viewOnlineUrl = null,
            messageId = messageId,
        )
    }
}

/**
 * Minimal HTML cleanup for generic emails:
 * - Strips <head>, <style>, <script> tags
 * - Removes tracking pixels (1x1 images)
 * - Preserves all other content as-is
 */
internal fun cleanEmailHtml(html: String): String {
    val doc = Jsoup.parse(html)

    // Remove non-content tags
    doc.select("head, style, script").remove()

    // Remove tracking pixels (1x1 images)
    doc.select("img").forEach { img ->
        val width = img.attr("width")
        val height = img.attr("height")
        if (width == "1" || height == "1") {
            img.remove()
        }
    }

    return doc.body()?.html() ?: html
}
```

- [ ] **Step 2: Register in the parser chain**

Update `EmailModule.kt` companion object to include `GenericEmailParser`:

```kotlin
companion object {
    @Provides
    @Singleton
    fun provideEmailParserChain(
        substackParser: SubstackEmailParser,
        genericParser: GenericEmailParser,
    ): EmailParserChain = EmailParserChain(
        listOf(substackParser, genericParser)
    )
}
```

Add the import:

```kotlin
import com.reink.data.email.GenericEmailParser
```

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/reink/data/email/GenericEmailParser.kt app/src/main/java/com/reink/di/EmailModule.kt
git commit -m "feat: add GenericEmailParser for allowlisted sender domains"
```

---

### Task 8: Handle generic emails in `EmailSyncRepository`

**Files:**
- Modify: `app/src/main/java/com/reink/data/repository/EmailSyncRepository.kt`

- [ ] **Step 1: Update `getOrCreateFeedForSender` to handle blank subdomain**

In `getOrCreateFeedForSender` (starts at line 194), the method currently starts with:

```kotlin
private suspend fun getOrCreateFeedForSender(email: EmailArticle): FeedEntity {
    val subdomain = email.substackSubdomain
    val allFeeds = feedDao.getAllOnce()
```

Replace the entire method body with:

```kotlin
private suspend fun getOrCreateFeedForSender(email: EmailArticle): FeedEntity {
    val subdomain = email.substackSubdomain

    // Generic emails (no subdomain) — key on sender address local part
    if (subdomain.isBlank()) {
        val senderLocal = email.senderAddress.substringBefore("@", "")
        if (senderLocal.isNotBlank()) {
            val existing = feedDao.getFeedsWithEmailPatterns().find { feed ->
                feed.emailSenderPattern?.equals(senderLocal, ignoreCase = true) == true
            }
            if (existing != null) return existing
        }

        val id = feedDao.insert(
            FeedEntity(
                title = email.senderName.ifBlank { email.senderAddress },
                url = "email://${email.senderAddress}",
                siteUrl = "",
                substackSubdomain = null,
                requiresAuth = false,
                addedAt = System.currentTimeMillis(),
                emailSenderPattern = senderLocal.ifBlank { null },
            ),
        )
        return feedDao.getById(id)!!
    }

    // Substack emails — match by subdomain
    val allFeeds = feedDao.getAllOnce()
    val bySubdomain = allFeeds.find { feed ->
        feed.substackSubdomain?.equals(subdomain, ignoreCase = true) == true
    }
    if (bySubdomain != null) return bySubdomain

    // Create new Substack feed
    val title = email.senderName.ifBlank { subdomain }
    val siteUrl = email.viewOnlineUrl?.let { url ->
        try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) { "" }
    } ?: ""

    val senderLocal = email.senderAddress.substringBefore("@", "")

    val id = feedDao.insert(
        FeedEntity(
            title = title,
            url = "email://$subdomain",
            siteUrl = siteUrl,
            substackSubdomain = subdomain,
            requiresAuth = false,
            addedAt = System.currentTimeMillis(),
            emailSenderPattern = senderLocal.ifBlank { null },
        ),
    )
    return feedDao.getById(id)!!
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/reink/data/repository/EmailSyncRepository.kt
git commit -m "feat: handle generic email feed creation in EmailSyncRepository"
```

---

## Chunk 3: Settings UI

### Task 9: Create `AllowedSendersSection` composable

**Files:**
- Create: `app/src/main/java/com/reink/ui/settings/AllowedSendersSection.kt`

- [ ] **Step 1: Create the UI component**

Follow the same pattern as `FeedManagementSection.kt` — section label, list with delete, add button with dialog:

```kotlin
package com.reink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AllowedSendersSection(
    domains: Set<String>,
    showAddDialog: Boolean,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "EMAIL SOURCES",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (domains.isEmpty()) {
            Text(
                text = "Add a sender domain to receive non-Substack newsletters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        domains.sorted().forEach { domain ->
            DomainRow(
                domain = domain,
                onRemove = { onRemoveDomain(domain) },
            )
        }

        OutlinedButton(
            onClick = onShowAddDialog,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Add Domain", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (showAddDialog) {
        AddDomainDialog(
            onDismiss = onDismissAddDialog,
            onConfirm = onAddDomain,
        )
    }
}

@Composable
private fun DomainRow(
    domain: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = domain,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = 48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Remove", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AddDomainDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var domain by remember { mutableStateOf("") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.onSurface,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Email Source", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the domain to accept emails from (e.g. mydomain.com)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(domain)
                    onDismiss()
                },
                enabled = domain.isNotBlank() && '.' in domain,
            ) {
                Text(
                    "Add",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/reink/ui/settings/AllowedSendersSection.kt
git commit -m "feat: add AllowedSendersSection settings UI component"
```

---

### Task 10: Wire allowed senders into `SettingsViewModel` and `SettingsScreen`

**Files:**
- Modify: `app/src/main/java/com/reink/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/reink/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add state and methods to `SettingsViewModel`**

Add to `SettingsUiState` data class (after `showEmailConfigDialog` at line 41):

```kotlin
val allowedSenderDomains: Set<String> = emptySet(),
val showAddDomainDialog: Boolean = false,
```

Add a new `MutableStateFlow` (after `emailConfigured` at line 84):

```kotlin
private val showAddDomainDialog = MutableStateFlow(false)
```

Update the `uiState` combine. The current combine at line 163 uses 7 flows with the array overload. Add `allowedSenderDomains` as an 8th flow and `showAddDomainDialog` as a 9th:

```kotlin
val uiState: StateFlow<SettingsUiState> = combine(
    feedRepository.observeRssFeeds(),
    preferencesRepository.observeReadingPreferences(),
    showAddFeedDialog,
    emailState,
    cloudQueueState,
    deviceSyncState,
    updateState,
    preferencesRepository.observeAllowedSenderDomains(),
    showAddDomainDialog,
) { flows ->
    @Suppress("UNCHECKED_CAST")
    val feeds = flows[0] as List<Feed>
    val prefs = flows[1] as ReadingPreferences
    val showDialog = flows[2] as Boolean
    val email = flows[3] as EmailState
    val cloud = flows[4] as CloudQueueState
    val sync = flows[5] as DeviceSyncState
    val update = flows[6] as UpdateState
    val domains = flows[7] as Set<String>
    val showDomainDialog = flows[8] as Boolean
    val creds = emailCredentialsStore.get()
    SettingsUiState(
        feeds = feeds,
        readingPreferences = prefs,
        showAddFeedDialog = showDialog,
        emailConfigured = email.configured,
        emailHost = creds?.host ?: "",
        emailUsername = creds?.username ?: "",
        emailTesting = email.testing,
        emailTestResult = email.testResult,
        emailSyncStatus = email.syncStatus,
        showEmailConfigDialog = email.showDialog,
        allowedSenderDomains = domains,
        showAddDomainDialog = showDomainDialog,
        cloudQueueConfig = cloud.config,
        cloudQueueSetupInProgress = cloud.setupInProgress,
        cloudQueueStatus = cloud.status,
        syncConfig = sync.config,
        syncConnectInProgress = sync.connectInProgress,
        syncInProgress = sync.inProgress,
        syncStatus = sync.status,
        syncLastSyncTime = sync.lastSyncTime,
        availableUpdate = update.availableUpdate,
        updateCheckInProgress = update.checkInProgress,
        updateDownloadInProgress = update.downloadInProgress,
        updateStatus = update.status,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SettingsUiState(),
)
```

Add methods at the end of the class (before the `formatSyncTime` method):

```kotlin
fun showAddDomainDialog() {
    showAddDomainDialog.value = true
}

fun dismissAddDomainDialog() {
    showAddDomainDialog.value = false
}

fun addAllowedSenderDomain(domain: String) {
    viewModelScope.launch {
        preferencesRepository.addAllowedSenderDomain(domain)
        showAddDomainDialog.value = false
    }
}

fun removeAllowedSenderDomain(domain: String) {
    viewModelScope.launch {
        preferencesRepository.removeAllowedSenderDomain(domain)
    }
}
```

- [ ] **Step 2: Add `AllowedSendersSection` to `SettingsScreen`**

In `SettingsScreen.kt`, add a new `item` block after the `EmailSettingsSection` item (after line 83), only visible when email is configured:

```kotlin
if (state.emailConfigured) {
    item {
        AllowedSendersSection(
            domains = state.allowedSenderDomains,
            showAddDialog = state.showAddDomainDialog,
            onShowAddDialog = { viewModel.showAddDomainDialog() },
            onDismissAddDialog = { viewModel.dismissAddDomainDialog() },
            onAddDomain = { viewModel.addAllowedSenderDomain(it) },
            onRemoveDomain = { viewModel.removeAllowedSenderDomain(it) },
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Build full debug APK to verify everything links**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/reink/ui/settings/SettingsViewModel.kt app/src/main/java/com/reink/ui/settings/SettingsScreen.kt
git commit -m "feat: wire allowed sender domains into settings UI"
```
