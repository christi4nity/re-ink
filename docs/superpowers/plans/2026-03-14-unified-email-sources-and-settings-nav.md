# Unified Email Sources + Settings Navigation — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify email sources into a single toggleable list (Substack becomes just another source), and move settings from the bottom tab bar to a gear icon in the top bar.

**Architecture:** Two independent changes: (1) SubstackEmailParser gets allowlist-gated via the same domain list as GenericEmailParser, with `substack.com` seeded as default; EmailSettingsSection and AllowedSendersSection merge into one unified section. (2) Settings removed from bottom nav, accessed via gear icon in the top bar alongside sync.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, DataStore, Navigation Compose

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/com/reink/data/email/SubstackEmailParser.kt` | Modify | Add allowlist check to `canParse`, implement `Refreshable` |
| `app/src/main/java/com/reink/data/repository/PreferencesRepository.kt` | Modify | Add `seedDefaultAllowedDomains()` method with seeded flag |
| `app/src/main/java/com/reink/data/email/EmailParserChain.kt` | Modify | Add PreferencesRepository, call seed in `refreshParsers` |
| `app/src/main/java/com/reink/di/EmailModule.kt` | Modify | Pass PreferencesRepository to chain |
| `app/src/main/java/com/reink/ui/settings/EmailSourcesSection.kt` | Create | Unified section merging EmailSettingsSection + AllowedSendersSection |
| `app/src/main/java/com/reink/ui/settings/EmailSettingsSection.kt` | Delete | Replaced by EmailSourcesSection |
| `app/src/main/java/com/reink/ui/settings/AllowedSendersSection.kt` | Delete | Replaced by EmailSourcesSection |
| `app/src/main/java/com/reink/ui/settings/SettingsScreen.kt` | Modify | Use new EmailSourcesSection, add back button |
| `app/src/main/java/com/reink/ui/settings/SettingsViewModel.kt` | Modify | Seed defaults on init |
| `app/src/main/java/com/reink/ui/navigation/ReInkNavGraph.kt` | Modify | Remove Settings from bottom tabs, add gear icon to top bar, pass `onNavigateToSettings` |
| `app/src/main/java/com/reink/ui/home/HomeScreen.kt` | Modify | Add `onNavigateToSettings` param, gear icon in top bar |
| `app/src/main/java/com/reink/ui/feed/FeedScreen.kt` | Modify | Add `onNavigateToSettings` param, gear icon in top bar |
| `app/src/main/java/com/reink/ui/readlater/ReadLaterScreen.kt` | Modify | Add `onNavigateToSettings` param, gear icon in top bar |

---

## Chunk 1: Unified Email Sources

### Task 1: Gate SubstackEmailParser on the allowlist

**Files:**
- Modify: `app/src/main/java/com/reink/data/email/SubstackEmailParser.kt`

- [ ] **Step 1: Add PreferencesRepository dependency and implement Refreshable**

Change the class to accept `PreferencesRepository`, implement `Refreshable`, and cache the allowlist:

```kotlin
@Singleton
class SubstackEmailParser @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : EmailParser, Refreshable {

    @Volatile
    private var cachedDomains: Set<String> = emptySet()

    override suspend fun refresh() {
        cachedDomains = preferencesRepository.getAllowedSenderDomains()
    }

    override fun canParse(message: Message): Boolean {
        if (!cachedDomains.any { it.equals("substack.com", ignoreCase = true) }) return false
        val listId = message.getHeader("List-Id")?.firstOrNull() ?: return false
        val subdomain = extractSubdomain(listId) ?: return false
        return subdomain != "www"
    }
    // ... rest unchanged
}
```

Add import: `import com.reink.data.repository.PreferencesRepository`

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/reink/data/email/SubstackEmailParser.kt
git commit -m "feat: gate SubstackEmailParser on allowlist, implement Refreshable"
```

---

### Task 2: Seed `substack.com` as default allowed domain

**Files:**
- Modify: `app/src/main/java/com/reink/data/repository/PreferencesRepository.kt`
- Modify: `app/src/main/java/com/reink/data/email/EmailParserChain.kt`

**Important:** The seed must run before every sync pass, not just when Settings is opened. Otherwise existing users who never visit Settings after the update will have an empty allowlist and SubstackEmailParser will silently skip all emails.

- [ ] **Step 1: Add seed method and seeded flag to PreferencesRepository**

Add a new key to the companion object:

```kotlin
private val KEY_DOMAINS_SEEDED = booleanPreferencesKey("allowed_sender_domains_seeded")
```

Add method at the end of the class (before closing brace). Uses a "has been seeded" flag so that if a user intentionally removes all domains (including substack.com), the seed doesn't re-add it:

```kotlin
suspend fun seedDefaultAllowedDomains() {
    val hasSeeded = dataStore.data.first()[KEY_DOMAINS_SEEDED] ?: false
    if (!hasSeeded) {
        dataStore.edit { store ->
            val current = store[KEY_ALLOWED_SENDER_DOMAINS] ?: emptySet()
            store[KEY_ALLOWED_SENDER_DOMAINS] = current + "substack.com"
            store[KEY_DOMAINS_SEEDED] = true
        }
    }
}
```

- [ ] **Step 2: Call seed in EmailParserChain.refreshParsers()**

In `EmailParserChain.kt`, add `PreferencesRepository` as a constructor parameter and call `seedDefaultAllowedDomains()` at the start of `refreshParsers()`:

```kotlin
class EmailParserChain(
    private val parsers: List<EmailParser>,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend fun refreshParsers() {
        preferencesRepository.seedDefaultAllowedDomains()
        for (parser in parsers) {
            if (parser is Refreshable) parser.refresh()
        }
    }
    // ... rest unchanged
}
```

Add import: `import com.reink.data.repository.PreferencesRepository`

Update `EmailModule.kt` to pass `preferencesRepository` to the chain:

```kotlin
@Provides
@Singleton
fun provideEmailParserChain(
    substackParser: SubstackEmailParser,
    genericParser: GenericEmailParser,
    preferencesRepository: PreferencesRepository,
): EmailParserChain = EmailParserChain(
    listOf(substackParser, genericParser),
    preferencesRepository,
)
```

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/reink/data/repository/PreferencesRepository.kt app/src/main/java/com/reink/data/email/EmailParserChain.kt app/src/main/java/com/reink/di/EmailModule.kt
git commit -m "feat: seed substack.com as default allowed sender domain on sync"
```

---

### Task 3: Create unified EmailSourcesSection

**Files:**
- Create: `app/src/main/java/com/reink/ui/settings/EmailSourcesSection.kt`

- [ ] **Step 1: Create the unified section**

This merges the email inbox config (host/username/test/sync/remove) with the domain allowlist into one section. The IMAP config sits at the top, followed by the domain list:

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EmailSourcesSection(
    emailConfigured: Boolean,
    emailHost: String,
    emailUsername: String,
    emailTesting: Boolean,
    emailTestResult: String?,
    emailSyncStatus: String?,
    showEmailConfigDialog: Boolean,
    onShowEmailConfigDialog: () -> Unit,
    onDismissEmailConfigDialog: () -> Unit,
    onSaveEmailConfig: (com.reink.data.email.EmailCredentials) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onRemoveEmail: () -> Unit,
    domains: Set<String>,
    showAddDomainDialog: Boolean,
    onShowAddDomainDialog: () -> Unit,
    onDismissAddDomainDialog: () -> Unit,
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

        // IMAP inbox config
        Surface(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (emailConfigured) {
                    Text(
                        text = emailUsername,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = emailHost,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        onClick = onTestConnection,
                        enabled = !emailTesting,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = if (emailTesting) "Testing..." else "Test connection",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (emailTestResult != null) {
                        Text(
                            text = emailTestResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        onClick = onSyncNow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = "Sync email now",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (emailSyncStatus != null) {
                        Text(
                            text = emailSyncStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        onClick = onRemoveEmail,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = "Remove email inbox",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Surface(
                        onClick = onShowEmailConfigDialog,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = "Set up email inbox",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Domain allowlist
        if (domains.isEmpty()) {
            Text(
                text = "Add a sender domain to receive newsletters via email",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        domains.sorted().forEach { domain ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        onClick = { onRemoveDomain(domain) },
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

        OutlinedButton(
            onClick = onShowAddDomainDialog,
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

        Text(
            text = "Emails from allowed domains are fetched from your inbox and displayed as articles. " +
                "Remove a domain to stop receiving its emails.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showEmailConfigDialog) {
        EmailConfigDialog(
            onDismiss = onDismissEmailConfigDialog,
            onSave = onSaveEmailConfig,
        )
    }

    if (showAddDomainDialog) {
        AddDomainDialog(
            onDismiss = onDismissAddDomainDialog,
            onConfirm = onAddDomain,
        )
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
git add app/src/main/java/com/reink/ui/settings/EmailSourcesSection.kt
git commit -m "feat: add unified EmailSourcesSection combining inbox config and domain allowlist"
```

---

### Task 4: Wire EmailSourcesSection into SettingsScreen, delete old sections

**Files:**
- Modify: `app/src/main/java/com/reink/ui/settings/SettingsScreen.kt`
- Delete: `app/src/main/java/com/reink/ui/settings/EmailSettingsSection.kt`
- Delete: `app/src/main/java/com/reink/ui/settings/AllowedSendersSection.kt`

- [ ] **Step 1: Replace EmailSettingsSection + AllowedSendersSection with EmailSourcesSection in SettingsScreen**

Replace the two items (the `EmailSettingsSection` item at lines 68-84 and the conditional `AllowedSendersSection` block at lines 86-97) with a single item:

```kotlin
item {
    EmailSourcesSection(
        emailConfigured = state.emailConfigured,
        emailHost = state.emailHost,
        emailUsername = state.emailUsername,
        emailTesting = state.emailTesting,
        emailTestResult = state.emailTestResult,
        emailSyncStatus = state.emailSyncStatus,
        showEmailConfigDialog = state.showEmailConfigDialog,
        onShowEmailConfigDialog = { viewModel.showEmailConfigDialog() },
        onDismissEmailConfigDialog = { viewModel.dismissEmailConfigDialog() },
        onSaveEmailConfig = { viewModel.saveEmailConfig(it) },
        onTestConnection = { viewModel.testEmailConnection() },
        onSyncNow = { viewModel.syncEmail() },
        onRemoveEmail = { viewModel.clearEmailConfig() },
        domains = state.allowedSenderDomains,
        showAddDomainDialog = state.showAddDomainDialog,
        onShowAddDomainDialog = { viewModel.showAddDomainDialog() },
        onDismissAddDomainDialog = { viewModel.dismissAddDomainDialog() },
        onAddDomain = { viewModel.addAllowedSenderDomain(it) },
        onRemoveDomain = { viewModel.removeAllowedSenderDomain(it) },
    )
}
```

- [ ] **Step 2: Delete the old files**

```bash
rm app/src/main/java/com/reink/ui/settings/EmailSettingsSection.kt
rm app/src/main/java/com/reink/ui/settings/AllowedSendersSection.kt
```

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/java/com/reink/ui/settings/
git commit -m "refactor: replace EmailSettingsSection + AllowedSendersSection with unified EmailSourcesSection"
```

---

## Chunk 2: Move Settings to Top Bar

### Task 5: Remove Settings from bottom nav, add gear icon to top bars

**Files:**
- Modify: `app/src/main/java/com/reink/ui/navigation/ReInkNavGraph.kt`
- Modify: `app/src/main/java/com/reink/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/reink/ui/feed/FeedScreen.kt`
- Modify: `app/src/main/java/com/reink/ui/readlater/ReadLaterScreen.kt`

- [ ] **Step 1: Update ReInkNavGraph**

Change the `bottomNavScreens` list at line 34 to remove Settings:

```kotlin
private val bottomNavScreens = listOf(Screen.Home, Screen.Feed, Screen.ReadLater)
```

Update all three screen composables to pass `onNavigateToSettings`:

```kotlin
composable(Screen.Home.route) {
    HomeScreen(
        onArticleClick = { articleId ->
            navController.navigate(Screen.Reader.createRoute("article", articleId))
        },
        onReadLaterClick = { readLaterId ->
            navController.navigate(Screen.Reader.createRoute("readlater", readLaterId))
        },
        onNavigateToSettings = {
            navController.navigate(Screen.Settings.route)
        },
    )
}
composable(Screen.Feed.route) {
    FeedScreen(
        onArticleClick = { articleId ->
            navController.navigate(Screen.Reader.createRoute("article", articleId))
        },
        onNavigateToSettings = {
            navController.navigate(Screen.Settings.route)
        },
    )
}
composable(Screen.ReadLater.route) {
    ReadLaterScreen(
        onItemClick = { readLaterId ->
            navController.navigate(Screen.Reader.createRoute("readlater", readLaterId))
        },
        onNavigateToSettings = {
            navController.navigate(Screen.Settings.route)
        },
    )
}
```

- [ ] **Step 2: Update HomeScreen top bar**

Add parameter to `HomeScreen`:

```kotlin
fun HomeScreen(
    onArticleClick: (Long) -> Unit = {},
    onReadLaterClick: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
```

Replace the top bar `Row` content (lines 41-62). Add a gear icon next to the sync button:

```kotlin
topBar = {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "re:ink",
            style = MaterialTheme.typography.titleLarge,
        )
        Row {
            TextButton(
                onClick = { viewModel.sync() },
                enabled = !state.isSyncing,
            ) {
                Text(
                    text = if (state.isSyncing) "Syncing\u2026" else "\u21BB",
                    style = if (state.isSyncing)
                        MaterialTheme.typography.labelLarge
                    else
                        MaterialTheme.typography.titleLarge,
                    color = if (state.isSyncing)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(
                onClick = onNavigateToSettings,
            ) {
                Text(
                    text = "\u2699",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
},
```

Note: `\u21BB` is the clockwise arrows (↻) for sync, `\u2699` is the gear (⚙) for settings. Both render as bold text characters consistent with the e-ink theme. When syncing, the icon changes to "Syncing…" text so the user can distinguish active vs idle state on e-ink where color differences are subtle.

- [ ] **Step 3: Update FeedScreen top bar**

Same changes as HomeScreen. Add `onNavigateToSettings: () -> Unit = {}` parameter and replace the top bar Row with the identical sync + gear icon pattern from Step 2.

- [ ] **Step 4: Update ReadLaterScreen top bar**

Same changes as HomeScreen. Add `onNavigateToSettings: () -> Unit = {}` parameter and replace the top bar Row with the identical sync + gear icon pattern from Step 2.

- [ ] **Step 5: Add back button to SettingsScreen**

Since Settings is now a navigated screen (not a tab), it needs a back button. In `SettingsScreen.kt`:

Add `onBack: () -> Unit = {}` parameter:

```kotlin
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
```

Add a navigation icon to the TopAppBar:

```kotlin
TopAppBar(
    title = { Text("Settings") },
    navigationIcon = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "\u2190",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    ),
)
```

Add imports: `import androidx.compose.foundation.clickable`, `import androidx.compose.foundation.layout.Box`, `import androidx.compose.ui.Alignment`, `import androidx.compose.ui.text.font.FontWeight`, `import androidx.compose.ui.unit.sp`

Update `ReInkNavGraph.kt` to pass `onBack`:

```kotlin
composable(Screen.Settings.route) {
    SettingsScreen(
        onBack = { navController.popBackStack() },
        onNavigateToArchive = {
            navController.navigate(Screen.Archive.route)
        },
    )
}
```

- [ ] **Step 6: Verify it compiles**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Full build**

Run: `cd /Users/christian/Code/re-ink && JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A app/src/main/java/com/reink/ui/
git commit -m "feat: move settings to top bar gear icon, remove from bottom nav"
```
