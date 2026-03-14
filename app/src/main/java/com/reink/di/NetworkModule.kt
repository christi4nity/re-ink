package com.reink.di

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @PlainHttpClient
    @Provides
    @Singleton
    fun providePlainOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRssParser(okHttpClient: OkHttpClient): RssParser =
        RssParserBuilder(callFactory = okHttpClient).build()
}
