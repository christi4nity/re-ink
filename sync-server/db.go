package main

import (
	"database/sql"
	"log"
	"os"
	"path/filepath"

	_ "modernc.org/sqlite"
)

func openDB() *sql.DB {
	dataDir := os.Getenv("DATA_DIR")
	if dataDir == "" {
		dataDir = "/data"
	}

	if err := os.MkdirAll(dataDir, 0755); err != nil {
		log.Fatalf("failed to create data dir: %v", err)
	}

	dbPath := filepath.Join(dataDir, "sync.db")
	db, err := sql.Open("sqlite", dbPath+"?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)")
	if err != nil {
		log.Fatalf("failed to open database: %v", err)
	}

	if err := db.Ping(); err != nil {
		log.Fatalf("failed to ping database: %v", err)
	}

	migrate(db)
	return db
}

func migrate(db *sql.DB) {
	stmts := []string{
		`CREATE TABLE IF NOT EXISTS feeds (
			url TEXT PRIMARY KEY,
			title TEXT NOT NULL DEFAULT '',
			site_url TEXT NOT NULL DEFAULT '',
			requires_auth INTEGER NOT NULL DEFAULT 0,
			enabled_section_slugs TEXT NOT NULL DEFAULT '',
			email_sender_pattern TEXT NOT NULL DEFAULT '',
			is_deleted INTEGER NOT NULL DEFAULT 0,
			modified_at INTEGER NOT NULL DEFAULT 0
		)`,
		`CREATE TABLE IF NOT EXISTS article_states (
			url TEXT PRIMARY KEY,
			is_read INTEGER NOT NULL DEFAULT 0,
			is_read_at INTEGER NOT NULL DEFAULT 0,
			is_archived INTEGER NOT NULL DEFAULT 0,
			is_archived_at INTEGER NOT NULL DEFAULT 0,
			archived_at INTEGER,
			is_deleted INTEGER NOT NULL DEFAULT 0,
			is_deleted_at INTEGER NOT NULL DEFAULT 0,
			deleted_at INTEGER,
			modified_at INTEGER NOT NULL DEFAULT 0
		)`,
		`CREATE TABLE IF NOT EXISTS read_later_states (
			url TEXT PRIMARY KEY,
			is_read INTEGER NOT NULL DEFAULT 0,
			is_read_at INTEGER NOT NULL DEFAULT 0,
			is_archived INTEGER NOT NULL DEFAULT 0,
			is_archived_at INTEGER NOT NULL DEFAULT 0,
			archived_at INTEGER,
			is_deleted INTEGER NOT NULL DEFAULT 0,
			is_deleted_at INTEGER NOT NULL DEFAULT 0,
			deleted_at INTEGER,
			saved_at INTEGER NOT NULL DEFAULT 0,
			modified_at INTEGER NOT NULL DEFAULT 0
		)`,
		`CREATE TABLE IF NOT EXISTS preferences (
			id INTEGER PRIMARY KEY DEFAULT 1,
			data TEXT NOT NULL DEFAULT '',
			modified_at INTEGER NOT NULL DEFAULT 0
		)`,
	}

	for _, stmt := range stmts {
		if _, err := db.Exec(stmt); err != nil {
			log.Fatalf("migration failed: %v", err)
		}
	}

	ensureColumn(db, "article_states", "is_deleted", "INTEGER NOT NULL DEFAULT 0")
	ensureColumn(db, "article_states", "is_deleted_at", "INTEGER NOT NULL DEFAULT 0")
	ensureColumn(db, "article_states", "deleted_at", "INTEGER")
	ensureColumn(db, "read_later_states", "is_deleted", "INTEGER NOT NULL DEFAULT 0")
	ensureColumn(db, "read_later_states", "is_deleted_at", "INTEGER NOT NULL DEFAULT 0")
	ensureColumn(db, "read_later_states", "deleted_at", "INTEGER")
}

func ensureColumn(db *sql.DB, table, column, definition string) {
	rows, err := db.Query("PRAGMA table_info(" + table + ")")
	if err != nil {
		log.Fatalf("column check failed: %v", err)
	}
	defer rows.Close()

	for rows.Next() {
		var cid int
		var name string
		var typ string
		var notNull int
		var defaultValue sql.NullString
		var pk int
		if err := rows.Scan(&cid, &name, &typ, &notNull, &defaultValue, &pk); err != nil {
			log.Fatalf("column scan failed: %v", err)
		}
		if name == column {
			return
		}
	}
	if err := rows.Err(); err != nil {
		log.Fatalf("column check failed: %v", err)
	}

	if _, err := db.Exec("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); err != nil {
		log.Fatalf("column migration failed: %v", err)
	}
}

// GetFeedsModifiedSince returns all feeds modified after the given timestamp.
func GetFeedsModifiedSince(db *sql.DB, since int64) ([]FeedSync, error) {
	rows, err := db.Query(
		`SELECT url, title, site_url, requires_auth, enabled_section_slugs, email_sender_pattern, is_deleted, modified_at
		 FROM feeds WHERE modified_at > ?`, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var feeds []FeedSync
	for rows.Next() {
		var f FeedSync
		if err := rows.Scan(&f.URL, &f.Title, &f.SiteURL, &f.RequiresAuth, &f.EnabledSectionSlugs, &f.EmailSenderPattern, &f.IsDeleted, &f.ModifiedAt); err != nil {
			return nil, err
		}
		feeds = append(feeds, f)
	}
	return feeds, rows.Err()
}

// GetArticlesModifiedSince returns article states modified after the given timestamp.
func GetArticlesModifiedSince(db *sql.DB, since int64) ([]ArticleStateSync, error) {
	rows, err := db.Query(
		`SELECT url, is_read, is_read_at, is_archived, is_archived_at, archived_at, is_deleted, is_deleted_at, deleted_at, modified_at
		 FROM article_states WHERE modified_at > ?`, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var articles []ArticleStateSync
	for rows.Next() {
		var a ArticleStateSync
		var archivedAt sql.NullInt64
		var deletedAt sql.NullInt64
		if err := rows.Scan(&a.URL, &a.IsRead, &a.IsReadAt, &a.IsArchived, &a.IsArchivedAt, &archivedAt, &a.IsDeleted, &a.IsDeletedAt, &deletedAt, &a.ModifiedAt); err != nil {
			return nil, err
		}
		if archivedAt.Valid {
			a.ArchivedAt = &archivedAt.Int64
		}
		if deletedAt.Valid {
			a.DeletedAt = &deletedAt.Int64
		}
		articles = append(articles, a)
	}
	return articles, rows.Err()
}

// GetReadLaterModifiedSince returns read-later states modified after the given timestamp.
func GetReadLaterModifiedSince(db *sql.DB, since int64) ([]ReadLaterStateSync, error) {
	rows, err := db.Query(
		`SELECT url, is_read, is_read_at, is_archived, is_archived_at, archived_at, is_deleted, is_deleted_at, deleted_at, saved_at, modified_at
		 FROM read_later_states WHERE modified_at > ?`, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var items []ReadLaterStateSync
	for rows.Next() {
		var r ReadLaterStateSync
		var archivedAt sql.NullInt64
		var deletedAt sql.NullInt64
		if err := rows.Scan(&r.URL, &r.IsRead, &r.IsReadAt, &r.IsArchived, &r.IsArchivedAt, &archivedAt, &r.IsDeleted, &r.IsDeletedAt, &deletedAt, &r.SavedAt, &r.ModifiedAt); err != nil {
			return nil, err
		}
		if archivedAt.Valid {
			r.ArchivedAt = &archivedAt.Int64
		}
		if deletedAt.Valid {
			r.DeletedAt = &deletedAt.Int64
		}
		items = append(items, r)
	}
	return items, rows.Err()
}

// GetPreferencesModifiedSince returns preferences if modified after the given timestamp.
func GetPreferencesModifiedSince(db *sql.DB, since int64) (*PreferencesSync, error) {
	var p PreferencesSync
	err := db.QueryRow(
		`SELECT data, modified_at FROM preferences WHERE id = 1 AND modified_at > ?`, since,
	).Scan(&p.Data, &p.ModifiedAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &p, nil
}
