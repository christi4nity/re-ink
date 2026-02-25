package com.reink.di

import com.reink.data.email.EmailContentSource
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.email.EncryptedEmailCredentialsStore
import com.reink.data.email.ImapEmailContentSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmailModule {

    @Binds
    @Singleton
    abstract fun bindEmailCredentialsStore(
        impl: EncryptedEmailCredentialsStore,
    ): EmailCredentialsStore

    @Binds
    @Singleton
    abstract fun bindEmailContentSource(
        impl: ImapEmailContentSource,
    ): EmailContentSource
}
