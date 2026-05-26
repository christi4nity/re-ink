package main

import "database/sql"

// MergeFeeds applies per-field timestamp merge for incoming feed changes.
func MergeFeeds(db *sql.DB, feeds []FeedSync) error {
	for _, f := range feeds {
		var existing FeedSync
		err := db.QueryRow(
			`SELECT url, title, site_url, requires_auth, enabled_section_slugs, email_sender_pattern, is_deleted, modified_at
			 FROM feeds WHERE url = ?`, f.URL,
		).Scan(&existing.URL, &existing.Title, &existing.SiteURL, &existing.RequiresAuth, &existing.EnabledSectionSlugs, &existing.EmailSenderPattern, &existing.IsDeleted, &existing.ModifiedAt)

		if err == sql.ErrNoRows {
			_, err = db.Exec(
				`INSERT INTO feeds (url, title, site_url, requires_auth, enabled_section_slugs, email_sender_pattern, is_deleted, modified_at)
				 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
				f.URL, f.Title, f.SiteURL, f.RequiresAuth, f.EnabledSectionSlugs, f.EmailSenderPattern, f.IsDeleted, f.ModifiedAt)
			if err != nil {
				return err
			}
			continue
		}
		if err != nil {
			return err
		}

		if f.ModifiedAt > existing.ModifiedAt {
			_, err = db.Exec(
				`UPDATE feeds SET title = ?, site_url = ?, requires_auth = ?, enabled_section_slugs = ?, email_sender_pattern = ?, is_deleted = ?, modified_at = ?
				 WHERE url = ?`,
				f.Title, f.SiteURL, f.RequiresAuth, f.EnabledSectionSlugs, f.EmailSenderPattern, f.IsDeleted, f.ModifiedAt, f.URL)
			if err != nil {
				return err
			}
		}
	}
	return nil
}

// MergeArticles applies per-field timestamp merge for article read/archive/delete state.
func MergeArticles(db *sql.DB, articles []ArticleStateSync) error {
	for _, a := range articles {
		var existing ArticleStateSync
		var archivedAt sql.NullInt64
		var deletedAt sql.NullInt64
		err := db.QueryRow(
			`SELECT url, is_read, is_read_at, is_archived, is_archived_at, archived_at, is_deleted, is_deleted_at, deleted_at, modified_at
			 FROM article_states WHERE url = ?`, a.URL,
		).Scan(&existing.URL, &existing.IsRead, &existing.IsReadAt, &existing.IsArchived, &existing.IsArchivedAt, &archivedAt, &existing.IsDeleted, &existing.IsDeletedAt, &deletedAt, &existing.ModifiedAt)

		if err == sql.ErrNoRows {
			var archVal interface{} = nil
			if a.ArchivedAt != nil {
				archVal = *a.ArchivedAt
			}
			var deleteVal interface{} = nil
			if a.DeletedAt != nil {
				deleteVal = *a.DeletedAt
			}
			_, err = db.Exec(
				`INSERT INTO article_states (url, is_read, is_read_at, is_archived, is_archived_at, archived_at, is_deleted, is_deleted_at, deleted_at, modified_at)
				 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
				a.URL, a.IsRead, a.IsReadAt, a.IsArchived, a.IsArchivedAt, archVal, a.IsDeleted, a.IsDeletedAt, deleteVal, a.ModifiedAt)
			if err != nil {
				return err
			}
			continue
		}
		if err != nil {
			return err
		}

		// Per-field merge: each field wins if its timestamp is newer.
		isRead := existing.IsRead
		isReadAt := existing.IsReadAt
		if a.IsReadAt > existing.IsReadAt {
			isRead = a.IsRead
			isReadAt = a.IsReadAt
		}

		isArchived := existing.IsArchived
		isArchivedAt := existing.IsArchivedAt
		existingArchivedAt := existing.ArchivedAt
		if archivedAt.Valid {
			existingArchivedAt = &archivedAt.Int64
		}
		if a.IsArchivedAt > existing.IsArchivedAt {
			isArchived = a.IsArchived
			isArchivedAt = a.IsArchivedAt
			existingArchivedAt = a.ArchivedAt
		}

		isDeleted := existing.IsDeleted
		isDeletedAt := existing.IsDeletedAt
		existingDeletedAt := existing.DeletedAt
		if deletedAt.Valid {
			existingDeletedAt = &deletedAt.Int64
		}
		if a.IsDeletedAt > existing.IsDeletedAt {
			isDeleted = a.IsDeleted
			isDeletedAt = a.IsDeletedAt
			existingDeletedAt = a.DeletedAt
		}

		modifiedAt := existing.ModifiedAt
		if a.ModifiedAt > existing.ModifiedAt {
			modifiedAt = a.ModifiedAt
		}

		var archVal interface{} = nil
		if existingArchivedAt != nil {
			archVal = *existingArchivedAt
		}
		var deleteVal interface{} = nil
		if existingDeletedAt != nil {
			deleteVal = *existingDeletedAt
		}

		_, err = db.Exec(
			`UPDATE article_states SET is_read = ?, is_read_at = ?, is_archived = ?, is_archived_at = ?, archived_at = ?, is_deleted = ?, is_deleted_at = ?, deleted_at = ?, modified_at = ?
			 WHERE url = ?`,
			isRead, isReadAt, isArchived, isArchivedAt, archVal, isDeleted, isDeletedAt, deleteVal, modifiedAt, a.URL)
		if err != nil {
			return err
		}
	}
	return nil
}

// MergeReadLater applies per-field timestamp merge for read-later state.
func MergeReadLater(db *sql.DB, items []ReadLaterStateSync) error {
	for _, r := range items {
		var existing ReadLaterStateSync
		var archivedAt sql.NullInt64
		var deletedAt sql.NullInt64
		err := db.QueryRow(
			`SELECT url, is_read, is_read_at, is_archived, is_archived_at, archived_at, is_deleted, is_deleted_at, deleted_at, saved_at, modified_at
			 FROM read_later_states WHERE url = ?`, r.URL,
		).Scan(&existing.URL, &existing.IsRead, &existing.IsReadAt, &existing.IsArchived, &existing.IsArchivedAt, &archivedAt, &existing.IsDeleted, &existing.IsDeletedAt, &deletedAt, &existing.SavedAt, &existing.ModifiedAt)

		if err == sql.ErrNoRows {
			var archVal interface{} = nil
			if r.ArchivedAt != nil {
				archVal = *r.ArchivedAt
			}
			var deleteVal interface{} = nil
			if r.DeletedAt != nil {
				deleteVal = *r.DeletedAt
			}
			_, err = db.Exec(
				`INSERT INTO read_later_states (url, is_read, is_read_at, is_archived, is_archived_at, archived_at, is_deleted, is_deleted_at, deleted_at, saved_at, modified_at)
				 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
				r.URL, r.IsRead, r.IsReadAt, r.IsArchived, r.IsArchivedAt, archVal, r.IsDeleted, r.IsDeletedAt, deleteVal, r.SavedAt, r.ModifiedAt)
			if err != nil {
				return err
			}
			continue
		}
		if err != nil {
			return err
		}

		isRead := existing.IsRead
		isReadAt := existing.IsReadAt
		if r.IsReadAt > existing.IsReadAt {
			isRead = r.IsRead
			isReadAt = r.IsReadAt
		}

		isArchived := existing.IsArchived
		isArchivedAt := existing.IsArchivedAt
		existingArchivedAt := existing.ArchivedAt
		if archivedAt.Valid {
			existingArchivedAt = &archivedAt.Int64
		}
		if r.IsArchivedAt > existing.IsArchivedAt {
			isArchived = r.IsArchived
			isArchivedAt = r.IsArchivedAt
			existingArchivedAt = r.ArchivedAt
		}

		isDeleted := existing.IsDeleted
		isDeletedAt := existing.IsDeletedAt
		existingDeletedAt := existing.DeletedAt
		if deletedAt.Valid {
			existingDeletedAt = &deletedAt.Int64
		}
		if r.IsDeletedAt > existing.IsDeletedAt {
			isDeleted = r.IsDeleted
			isDeletedAt = r.IsDeletedAt
			existingDeletedAt = r.DeletedAt
		}

		savedAt := existing.SavedAt
		if r.SavedAt > existing.SavedAt {
			savedAt = r.SavedAt
		}

		modifiedAt := existing.ModifiedAt
		if r.ModifiedAt > existing.ModifiedAt {
			modifiedAt = r.ModifiedAt
		}

		var archVal interface{} = nil
		if existingArchivedAt != nil {
			archVal = *existingArchivedAt
		}
		var deleteVal interface{} = nil
		if existingDeletedAt != nil {
			deleteVal = *existingDeletedAt
		}

		_, err = db.Exec(
			`UPDATE read_later_states SET is_read = ?, is_read_at = ?, is_archived = ?, is_archived_at = ?, archived_at = ?, is_deleted = ?, is_deleted_at = ?, deleted_at = ?, saved_at = ?, modified_at = ?
			 WHERE url = ?`,
			isRead, isReadAt, isArchived, isArchivedAt, archVal, isDeleted, isDeletedAt, deleteVal, savedAt, modifiedAt, r.URL)
		if err != nil {
			return err
		}
	}
	return nil
}

// MergePreferences applies last-write-wins for preferences blob.
func MergePreferences(db *sql.DB, p *PreferencesSync) error {
	if p == nil {
		return nil
	}

	var existingModifiedAt int64
	err := db.QueryRow(`SELECT modified_at FROM preferences WHERE id = 1`).Scan(&existingModifiedAt)
	if err == sql.ErrNoRows {
		_, err = db.Exec(
			`INSERT INTO preferences (id, data, modified_at) VALUES (1, ?, ?)`,
			p.Data, p.ModifiedAt)
		return err
	}
	if err != nil {
		return err
	}

	if p.ModifiedAt > existingModifiedAt {
		_, err = db.Exec(
			`UPDATE preferences SET data = ?, modified_at = ? WHERE id = 1`,
			p.Data, p.ModifiedAt)
		return err
	}
	return nil
}
