package com.reink.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.reink.data.local.ArticleDao
import com.reink.data.local.FeedDao
import com.reink.data.local.ReadLaterDao
import com.reink.data.local.ReInkDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reink_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(app: Application): ReInkDatabase =
        Room.databaseBuilder(
            app,
            ReInkDatabase::class.java,
            "reink.db",
        )
            .addMigrations(
                ReInkDatabase.MIGRATION_1_2,
                ReInkDatabase.MIGRATION_2_3,
                ReInkDatabase.MIGRATION_3_4,
                ReInkDatabase.MIGRATION_4_5,
                ReInkDatabase.MIGRATION_5_6,
                ReInkDatabase.MIGRATION_6_7,
                ReInkDatabase.MIGRATION_7_8,
                ReInkDatabase.MIGRATION_8_9,
                ReInkDatabase.MIGRATION_9_10,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // One-time backfill for devices that already ran migration 9→10
                    // before it included the backfill. Sets modifiedAt on stateful rows
                    // so the first sync picks them up. No-op if already backfilled.
                    val now = System.currentTimeMillis()
                    db.execSQL("UPDATE feeds SET modifiedAt = $now WHERE modifiedAt = 0")
                    db.execSQL("UPDATE articles SET modifiedAt = $now WHERE modifiedAt = 0 AND (isRead = 1 OR isArchived = 1)")
                    db.execSQL("UPDATE read_later SET modifiedAt = $now WHERE modifiedAt = 0 AND (isRead = 1 OR isArchived = 1)")
                }
            })
            .build()

    @Provides
    fun provideFeedDao(db: ReInkDatabase): FeedDao = db.feedDao()

    @Provides
    fun provideArticleDao(db: ReInkDatabase): ArticleDao = db.articleDao()

    @Provides
    fun provideReadLaterDao(db: ReInkDatabase): ReadLaterDao = db.readLaterDao()

    @Provides
    @Singleton
    fun provideDataStore(app: Application): DataStore<Preferences> = app.dataStore

    @Provides
    @Singleton
    fun provideWorkManager(app: Application): WorkManager =
        WorkManager.getInstance(app)
}
