# Pluggable Email Parser System

**Date:** 2026-03-14
**Status:** Draft

## Problem

The app currently only ingests Substack newsletter emails via a hard-coded `SubstackEmailParser`. The `ImapEmailContentSource` directly depends on it, and emails without a Substack `List-Id` header are silently dropped. This prevents ingesting emails from other sources — notably AI-generated daily briefings and non-Substack newsletters.

## Goals

1. Extract a common `EmailParser` interface from the existing Substack parser
2. Add a `GenericEmailParser` for allowlisted sender domains (minimal cleanup, takes HTML body as-is)
3. Route emails through an ordered parser chain — first match wins
4. Add a settings UI for managing the sender domain allowlist
5. Make the system extensible so contributors can add parsers (e.g., Ghost, Beehiiv) via PR

## Non-Goals

- Building the email generation/sending pipeline (handled externally)
- Implementing parsers for specific non-Substack platforms (future PRs)
- Changing the downstream article storage, feed matching, or reader rendering

## Design

### 1. EmailParser Interface

New file: `data/email/EmailParser.kt`

```kotlin
interface EmailParser {
    /**
     * Inspect message headers/sender to determine if this parser can handle it.
     * Must be cheap — no body parsing.
     */
    fun canParse(message: Message): Boolean

    /**
     * Parse the message into an EmailArticle. Returns null if parsing fails.
     * Only called after canParse() returns true.
     */
    fun parse(message: Message): EmailArticle?
}
```

### 2. SubstackEmailParser Changes

Refactor `SubstackEmailParser` to implement `EmailParser`:

```kotlin
@Singleton
class SubstackEmailParser @Inject constructor() : EmailParser {
    override fun canParse(message: Message): Boolean {
        val listId = message.getHeader("List-Id")?.firstOrNull() ?: return false
        val subdomain = extractSubdomain(listId) ?: return false
        return subdomain != "www"
    }

    override fun parse(message: Message): EmailArticle? {
        // Existing implementation unchanged
    }
}
```

The current `parse()` method already returns null on failure, so the contract is already satisfied. The only change is extracting the `List-Id` check into `canParse()` so the chain can skip without entering the full parse path.

### 3. GenericEmailParser

New file: `data/email/GenericEmailParser.kt`

```kotlin
@Singleton
class GenericEmailParser @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : EmailParser {

    // Cached allowlist — avoids DataStore reads on every message.
    // Refreshed before each sync pass via refreshAllowlist().
    @Volatile
    private var cachedDomains: Set<String> = emptySet()

    /** Call before processing a batch of emails (e.g., at sync start). */
    suspend fun refreshAllowlist() {
        cachedDomains = preferencesRepository.getAllowedSenderDomains()
    }

    override fun canParse(message: Message): Boolean {
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
            substackSubdomain = "",  // Not applicable
            receivedAt = (message.receivedDate ?: message.sentDate)?.time
                ?: System.currentTimeMillis(),
            contentHtml = cleanHtml,
            viewOnlineUrl = null,  // No web version
            messageId = messageId,
        )
    }
}
```

**Allowlist caching:** `GenericEmailParser` caches the allowlist in memory. `ImapEmailContentSource` calls `refreshAllowlist()` once before iterating messages (in both `fetchNewArticles` and `streamNewArticles`). This avoids `runBlocking` and keeps `canParse` cheap and synchronous.

**Content cleanup (`cleanEmailHtml`)** is minimal and best-effort:
- Strip `<head>`, `<style>`, `<script>` tags
- Remove tracking pixels (`<img>` elements with `width="1"` or `height="1"` attributes)
- Preserve all remaining content HTML as-is

Note: we intentionally do NOT strip wrapper tables. The user controls the email format for their briefings, and aggressively removing tables risks destroying legitimate content tables. Future parsers for specific platforms (Ghost, Beehiiv) can add their own targeted cleanup.

**Shared utilities:** `extractHtmlBody()` and `getMessageId()` are currently private methods on `SubstackEmailParser`. Extract these into a top-level `EmailParserUtils.kt` file so both parsers can use them without inheritance.

### 4. EmailParserChain

New file: `data/email/EmailParserChain.kt`

```kotlin
class EmailParserChain(private val parsers: List<EmailParser>) {
    fun parse(message: Message): EmailArticle? {
        for (parser in parsers) {
            if (parser.canParse(message)) {
                return parser.parse(message)
            }
        }
        return null
    }
}
```

Order matters: Substack first (specific), generic last (catch-all). Future parsers slot in between.

**Debug logging:** The chain should log at debug level which parser matched each message (or that none matched). This is essential for troubleshooting "why didn't my email appear?" issues. Use `android.util.Log.d("ReInk", ...)` consistent with existing logging.

### 5. DI Changes

`EmailModule.kt` changes from abstract `@Binds` to concrete `@Provides` for the chain:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class EmailModule {

    @Binds @Singleton
    abstract fun bindEmailCredentialsStore(
        impl: EncryptedEmailCredentialsStore,
    ): EmailCredentialsStore

    @Binds @Singleton
    abstract fun bindEmailContentSource(
        impl: ImapEmailContentSource,
    ): EmailContentSource

    companion object {
        @Provides @Singleton
        fun provideEmailParserChain(
            substackParser: SubstackEmailParser,
            genericParser: GenericEmailParser,
        ): EmailParserChain = EmailParserChain(
            listOf(substackParser, genericParser)
        )
    }
}
```

### 6. ImapEmailContentSource Changes

Replace the direct `SubstackEmailParser` dependency with `EmailParserChain`:

```kotlin
@Singleton
class ImapEmailContentSource @Inject constructor(
    private val credentialsStore: EmailCredentialsStore,
    private val parserChain: EmailParserChain,  // was: parser: SubstackEmailParser
) : EmailContentSource {
    // ...
    // Replace all `parser.parse(message)` calls with `parserChain.parse(message)`
}
```

**Important:** Both `fetchNewArticles` and `streamNewArticles` must call `parserChain.refreshParsers()` once before iterating messages. `EmailParserChain` delegates this to parsers that need it (i.e., `GenericEmailParser.refreshAllowlist()`):

```kotlin
class EmailParserChain(private val parsers: List<EmailParser>) {
    /** Called once before a sync pass to let parsers refresh cached state. */
    suspend fun refreshParsers() {
        for (parser in parsers) {
            if (parser is GenericEmailParser) parser.refreshAllowlist()
        }
    }
    // ...
}
```

### 7. EmailSyncRepository Changes

The feed-matching logic in `EmailSyncRepository.matchSenderToFeed()` currently relies on `substackSubdomain` as Strategy 1. For generic emails, `substackSubdomain` is empty, so Strategy 1 won't match. The existing strategies 2-4 also won't match for a new sender.

This means generic emails will fall through to `getOrCreateFeedForSender()`. That method currently creates feeds keyed on `substackSubdomain`. For generic emails, we add a branch at the **top** of the method — before the existing subdomain logic — so it's handled first when `substackSubdomain` is blank:

```kotlin
private suspend fun getOrCreateFeedForSender(email: EmailArticle): FeedEntity {
    val subdomain = email.substackSubdomain

    // Generic emails (no subdomain) — key on sender address local part
    if (subdomain.isBlank()) {
        val senderLocal = email.senderAddress.substringBefore("@", "")
        if (senderLocal.isNotBlank()) {
            // Check existing feeds using in-memory filter on getFeedsWithEmailPatterns()
            val existing = feedDao.getFeedsWithEmailPatterns().find { feed ->
                feed.emailSenderPattern?.equals(senderLocal, ignoreCase = true) == true
            }
            if (existing != null) return existing
        }

        val id = feedDao.insert(FeedEntity(
            title = email.senderName.ifBlank { email.senderAddress },
            url = "email://${email.senderAddress}",
            siteUrl = "",
            substackSubdomain = null,
            requiresAuth = false,
            addedAt = System.currentTimeMillis(),
            emailSenderPattern = senderLocal.ifBlank { null },
        ))
        return feedDao.getById(id)!!
    }

    // Existing Substack subdomain-based logic (unchanged)...
    val allFeeds = feedDao.getAllOnce()
    val bySubdomain = allFeeds.find { feed ->
        feed.substackSubdomain?.equals(subdomain, ignoreCase = true) == true
    }
    if (bySubdomain != null) return bySubdomain

    // ... rest of existing feed creation for Substack
}
```

**Key details:**
- Uses the existing `getFeedsWithEmailPatterns()` DAO method with an in-memory filter — no new DAO query needed.
- The generic branch runs first because `substackSubdomain` is blank, so there's no risk of it intercepting Substack emails (which always have a subdomain).
- On subsequent syncs, Strategy 2 (`emailSenderPattern`) in `matchSenderToFeed()` matches the sender, so emails from the same address group into the same feed automatically.
- Feed URL uses `email://{senderAddress}` — if the same sender is removed and re-added, `feedDao.insert` with `OnConflictStrategy.IGNORE` on the URL prevents duplicates.

### 8. Sender Domain Allowlist Storage

Add to `PreferencesRepository`:

```kotlin
private val KEY_ALLOWED_SENDER_DOMAINS = stringSetPreferencesKey("allowed_sender_domains")

fun observeAllowedSenderDomains(): Flow<Set<String>> =
    dataStore.data.map { prefs -> prefs[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet() }

suspend fun getAllowedSenderDomains(): Set<String> =
    dataStore.data.first()[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()

suspend fun addAllowedSenderDomain(domain: String) {
    dataStore.edit { store ->
        val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
        store[KEY_ALLOWED_SENDER_DOMAINS] = current + domain.lowercase()
    }
}

suspend fun removeAllowedSenderDomain(domain: String) {
    dataStore.edit { store ->
        val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
        store[KEY_ALLOWED_SENDER_DOMAINS] = current - domain.lowercase()
    }
}
```

DataStore is the right choice here — domain names aren't sensitive, and `stringSetPreferencesKey` gives us a native set type without JSON serialization. Note: requires adding `import androidx.datastore.preferences.core.stringSetPreferencesKey` (not currently used in the file).

**Domain validation:** `addAllowedSenderDomain` should strip any `@` prefix and whitespace, and validate the domain contains at least one dot. Reject values like `user@gmail.com` (extract `gmail.com`), empty strings, or values without a dot.

### 9. Settings UI

Add a new `AllowedSendersSection` composable in the settings screen, placed after the email config section. It shows:

- Section header: "Email Sources" (or "Allowed Senders")
- List of currently allowed domains with a delete button each
- "Add domain" button that opens a simple text input dialog
- Disabled/hidden state when email is not configured
- Empty state text when no domains added: "Add a sender domain to receive non-Substack newsletters"

This follows the same pattern as `FeedManagementSection` — a list with add/delete.

The section is only visible when email ingestion is configured (credentials saved). The ViewModel gets new methods: `addAllowedSenderDomain(domain)`, `removeAllowedSenderDomain(domain)`, and the state exposes `allowedSenderDomains: Set<String>`.

### 10. EmailArticle Compatibility

The `EmailArticle` data class has a `substackSubdomain` field that's Substack-specific. For generic emails this will be an empty string. No structural change needed — the field is only used for feed matching (Strategy 1) and feed creation, both of which already handle the empty case with the changes in Section 7.

A future cleanup could rename this to something more generic or make it nullable, but that's cosmetic and doesn't block this feature.

## File Changes Summary

| File | Change |
|------|--------|
| `data/email/EmailParser.kt` | **New** — interface |
| `data/email/EmailParserChain.kt` | **New** — ordered chain |
| `data/email/GenericEmailParser.kt` | **New** — allowlist-based parser |
| `data/email/EmailParserUtils.kt` | **New** — shared utilities extracted from SubstackEmailParser |
| `data/email/SubstackEmailParser.kt` | **Modify** — implement EmailParser interface, extract shared utils |
| `data/email/ImapEmailContentSource.kt` | **Modify** — use chain instead of direct parser |
| `data/repository/PreferencesRepository.kt` | **Modify** — add allowed domains storage |
| `data/repository/EmailSyncRepository.kt` | **Modify** — handle generic emails in feed creation |
| `di/EmailModule.kt` | **Modify** — provide chain |
| `ui/settings/SettingsScreen.kt` | **Modify** — add AllowedSendersSection |
| `ui/settings/SettingsViewModel.kt` | **Modify** — expose/manage allowed domains |
| `ui/settings/AllowedSendersSection.kt` | **New** — settings UI component |

## Extensibility for Contributors

Adding a new parser (e.g., Ghost) requires:

1. Create `data/email/GhostEmailParser.kt` implementing `EmailParser`
2. Add it to the chain in `EmailModule.kt` (between Substack and generic)

No other files need to change. The parser handles its own detection (`canParse`) and cleanup (`parse`). Feed matching and article storage work automatically through the existing strategies.

## Appendix: Email Formatting Guide (informational, not implementation scope)

Emails sent to the allowlisted inbox should follow this structure:

- **Subject line** becomes the article title
- **From name** becomes the feed title (on first email) and article author
- **From address** determines feed grouping (one feed per unique sender address, keyed on local part)
- **HTML body** is taken nearly verbatim — keep it clean

Supported HTML elements that render well in the reader:
- Headings (`h1`-`h6`), paragraphs, lists, blockquotes, code blocks, tables, images, figures with captions, horizontal rules, links
- `<blockquote class="tweet-card">` for tweet-style cards
- `<blockquote class="embed-card">` for article reference cards

Example email body:

```html
<h2>Company Updates</h2>
<p>Revenue hit $X this quarter, up Y% from last quarter...</p>

<blockquote class="tweet-card">
    <p class="tweet-text">Interesting industry development...</p>
    <footer class="tweet-author">-- @username</footer>
</blockquote>

<h2>Industry News</h2>
<blockquote class="embed-card">
    <p class="embed-title"><a href="https://example.com/article">Article Title</a></p>
    <footer class="embed-meta">Author Name -- Publication</footer>
</blockquote>

<p>Analysis of the above...</p>
```

All reader preferences (font, size, margins, pagination, alignment) apply to generic emails identically to Substack articles.
