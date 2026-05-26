package com.reink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeedEntity::class, ArticleEntity::class, ReadLaterEntity::class],
    version = 12,
    exportSchema = false,
)
abstract class ReInkDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun readLaterDao(): ReadLaterDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN authToken TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE feeds ADD COLUMN substackSubdomain TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN imageUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN enabledSectionSlugs TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN contentStatus TEXT NOT NULL DEFAULT 'full'")
                // Backfill: mark existing articles as truncated if they contain
                // paywall markers. Note: "Read more" links are already stripped
                // by cleanContent() before storage, so we check for paywall class
                // and also for short content from known Substack patterns.
                db.execSQL(
                    """UPDATE articles SET contentStatus = 'truncated'
                       WHERE contentStatus = 'full'
                         AND (contentHtml LIKE '%class="paywall%'
                              OR contentHtml LIKE '%class=''paywall%')""",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN emailMessageId TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX index_articles_emailMessageId ON articles(emailMessageId)")
                db.execSQL("ALTER TABLE feeds ADD COLUMN emailSenderPattern TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE read_later ADD COLUMN sourceDomain TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE read_later ADD COLUMN excerpt TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN archivedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE read_later ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE read_later ADD COLUMN archivedAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL("ALTER TABLE feeds ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE feeds ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE read_later ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
                // Backfill: mark existing stateful rows so first sync picks them up
                db.execSQL("UPDATE feeds SET modifiedAt = $now")
                db.execSQL("UPDATE articles SET modifiedAt = $now WHERE isRead = 1 OR isArchived = 1")
                db.execSQL("UPDATE read_later SET modifiedAt = $now WHERE isRead = 1 OR isArchived = 1")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE articles
                    SET feedId = (
                        SELECT keep.id
                        FROM feeds AS old
                        JOIN feeds AS keep ON keep.url = old.url
                        WHERE old.id = articles.feedId
                        ORDER BY keep.isDeleted ASC, keep.modifiedAt DESC, keep.id DESC
                        LIMIT 1
                    )
                    WHERE EXISTS (
                        SELECT 1
                        FROM feeds AS old
                        JOIN feeds AS keep ON keep.url = old.url
                        WHERE old.id = articles.feedId
                          AND (
                              keep.isDeleted < old.isDeleted OR
                              (keep.isDeleted = old.isDeleted AND keep.modifiedAt > old.modifiedAt) OR
                              (keep.isDeleted = old.isDeleted AND keep.modifiedAt = old.modifiedAt AND keep.id > old.id)
                          )
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    DELETE FROM feeds
                    WHERE id NOT IN (
                        SELECT current.id
                        FROM feeds AS current
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM feeds AS candidate
                            WHERE candidate.url = current.url
                              AND (
                                  candidate.isDeleted < current.isDeleted OR
                                  (candidate.isDeleted = current.isDeleted AND candidate.modifiedAt > current.modifiedAt) OR
                                  (candidate.isDeleted = current.isDeleted AND candidate.modifiedAt = current.modifiedAt AND candidate.id > current.id)
                              )
                        )
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_feeds_url ON feeds(url)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE read_later ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE read_later ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            }
        }
    }
}
