package main

// SyncRequest is sent by the client with local changes since lastSyncedAt.
type SyncRequest struct {
	DeviceID    string               `json:"deviceId"`
	LastSyncedAt int64               `json:"lastSyncedAt"`
	Feeds       []FeedSync           `json:"feeds"`
	Articles    []ArticleStateSync   `json:"articles"`
	ReadLater   []ReadLaterStateSync `json:"readLater"`
	Preferences *PreferencesSync     `json:"preferences,omitempty"`
}

// SyncResponse returns server-side changes since the client's lastSyncedAt.
type SyncResponse struct {
	SyncedAt    int64                `json:"syncedAt"`
	Feeds       []FeedSync           `json:"feeds"`
	Articles    []ArticleStateSync   `json:"articles"`
	ReadLater   []ReadLaterStateSync `json:"readLater"`
	Preferences *PreferencesSync     `json:"preferences,omitempty"`
}

type FeedSync struct {
	URL                string `json:"url"`
	Title              string `json:"title"`
	SiteURL            string `json:"siteUrl"`
	RequiresAuth       bool   `json:"requiresAuth"`
	EnabledSectionSlugs string `json:"enabledSectionSlugs"`
	EmailSenderPattern string `json:"emailSenderPattern"`
	IsDeleted          bool   `json:"isDeleted"`
	ModifiedAt         int64  `json:"modifiedAt"`
}

type ArticleStateSync struct {
	URL        string `json:"url"`
	IsRead     bool   `json:"isRead"`
	IsReadAt   int64  `json:"isReadAt"`
	IsArchived bool   `json:"isArchived"`
	IsArchivedAt int64 `json:"isArchivedAt"`
	ArchivedAt *int64 `json:"archivedAt,omitempty"`
	ModifiedAt int64  `json:"modifiedAt"`
}

type ReadLaterStateSync struct {
	URL        string `json:"url"`
	IsRead     bool   `json:"isRead"`
	IsReadAt   int64  `json:"isReadAt"`
	IsArchived bool   `json:"isArchived"`
	IsArchivedAt int64 `json:"isArchivedAt"`
	ArchivedAt *int64 `json:"archivedAt,omitempty"`
	SavedAt    int64  `json:"savedAt"`
	ModifiedAt int64  `json:"modifiedAt"`
}

type PreferencesSync struct {
	Data       string `json:"data"`
	ModifiedAt int64  `json:"modifiedAt"`
}
