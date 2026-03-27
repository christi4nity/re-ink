# Reader

The reader renders article HTML in a WebView with e-ink optimizations.

## Reading modes

- **Scroll:** Standard vertical scrolling
- **Paginated:** Content split into pages. Navigate with volume keys (Volume Down = next page, Volume Up = previous page). A progress bar shows your position.

## Typography

Tap the **Aa** icon in the reader overlay to customize:

| Setting | Options |
|---------|---------|
| Font | Literata, Source Serif 4, Atkinson Hyperlegible |
| Size | 14-36px |
| Line height | 1.2-2.2x |
| Side margins | 8-192dp |
| Vertical margins | 0-96dp |
| Alignment | Left, Center, Right, Justify |

Changes apply immediately with a live preview. Preferences persist across sessions and sync to other devices.

## Links in articles

Tapping a link in an article opens a bottom sheet with options:
- **Save for Later** — adds to your read-later queue
- **Open in Browser** — opens externally
- **Cancel**

This prevents accidental navigation away from the reader.

## Read-later queue

Save URLs for later reading. Content is extracted automatically in the background.

### How articles get saved

- Tap a link in the reader and choose **Save for Later**
- Share a URL via the [cloud queue](cloud-queue.md) from another device

### Content extraction

The app extracts clean article text using multiple strategies (tried in order):

1. Standard HTTP fetch + Readability4J
2. Googlebot User-Agent (bypasses some paywalls)
3. Google Cache
4. Archive.org Wayback Machine

Extraction runs automatically every 4 hours. Failed items retry up to 3 times.
