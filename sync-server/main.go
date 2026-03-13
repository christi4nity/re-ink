package main

import (
	"database/sql"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"time"
)

var db *sql.DB

func main() {
	db = openDB()
	defer db.Close()

	apiKey := os.Getenv("REINK_SYNC_API_KEY")
	if apiKey == "" {
		log.Fatal("REINK_SYNC_API_KEY must be set")
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", handleHealth)
	mux.HandleFunc("POST /sync", authMiddleware(apiKey, handleSync))

	port := os.Getenv("PORT")
	if port == "" {
		port = "8073"
	}

	log.Printf("reink-sync listening on :%s", port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatal(err)
	}
}

func authMiddleware(apiKey string, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-API-Key") != apiKey {
			http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
			return
		}
		next(w, r)
	}
}

func handleHealth(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte(`{"status":"ok"}`))
}

func handleSync(w http.ResponseWriter, r *http.Request) {
	var req SyncRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid json"}`, http.StatusBadRequest)
		return
	}

	syncedAt := time.Now().UnixMilli()

	// Get server changes BEFORE merging client changes (so we don't echo back)
	serverFeeds, err := GetFeedsModifiedSince(db, req.LastSyncedAt)
	if err != nil {
		log.Printf("error getting feeds: %v", err)
		http.Error(w, `{"error":"internal"}`, http.StatusInternalServerError)
		return
	}

	serverArticles, err := GetArticlesModifiedSince(db, req.LastSyncedAt)
	if err != nil {
		log.Printf("error getting articles: %v", err)
		http.Error(w, `{"error":"internal"}`, http.StatusInternalServerError)
		return
	}

	serverReadLater, err := GetReadLaterModifiedSince(db, req.LastSyncedAt)
	if err != nil {
		log.Printf("error getting read later: %v", err)
		http.Error(w, `{"error":"internal"}`, http.StatusInternalServerError)
		return
	}

	serverPrefs, err := GetPreferencesModifiedSince(db, req.LastSyncedAt)
	if err != nil {
		log.Printf("error getting preferences: %v", err)
		http.Error(w, `{"error":"internal"}`, http.StatusInternalServerError)
		return
	}

	// Merge client changes into server
	if err := MergeFeeds(db, req.Feeds); err != nil {
		log.Printf("error merging feeds: %v", err)
		http.Error(w, `{"error":"merge failed"}`, http.StatusInternalServerError)
		return
	}

	if err := MergeArticles(db, req.Articles); err != nil {
		log.Printf("error merging articles: %v", err)
		http.Error(w, `{"error":"merge failed"}`, http.StatusInternalServerError)
		return
	}

	if err := MergeReadLater(db, req.ReadLater); err != nil {
		log.Printf("error merging read later: %v", err)
		http.Error(w, `{"error":"merge failed"}`, http.StatusInternalServerError)
		return
	}

	if err := MergePreferences(db, req.Preferences); err != nil {
		log.Printf("error merging preferences: %v", err)
		http.Error(w, `{"error":"merge failed"}`, http.StatusInternalServerError)
		return
	}

	// Ensure empty slices instead of nil (Go nil → JSON null breaks kotlinx.serialization)
	if serverFeeds == nil {
		serverFeeds = []FeedSync{}
	}
	if serverArticles == nil {
		serverArticles = []ArticleStateSync{}
	}
	if serverReadLater == nil {
		serverReadLater = []ReadLaterStateSync{}
	}

	resp := SyncResponse{
		SyncedAt:    syncedAt,
		Feeds:       serverFeeds,
		Articles:    serverArticles,
		ReadLater:   serverReadLater,
		Preferences: serverPrefs,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}
