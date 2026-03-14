package com.reink.di

import com.reink.data.email.EmailContentSource
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.email.EmailParserChain
import com.reink.data.email.EncryptedEmailCredentialsStore
import com.reink.data.email.GenericEmailParser
import com.reink.data.email.ImapEmailContentSource
import com.reink.data.email.SubstackEmailParser
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    companion object {
        @Provides
        @Singleton
        fun provideEmailParserChain(
            substackParser: SubstackEmailParser,
            genericParser: GenericEmailParser,
        ): EmailParserChain = EmailParserChain(
            listOf(substackParser, genericParser)
        )
    }
}
