package com.reink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeedEntity::class, ArticleEntity::class, ReadLaterEntity::class],
    version = 8,
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
    }
}
