package com.reink.data.remote

import com.reink.data.repository.PreferencesRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubstackAuthInterceptor @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : Interceptor {

    @Volatile
    private var cachedSid: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        if (!host.endsWith("substack.com")) {
            return chain.proceed(request)
        }

        val sid = cachedSid ?: runBlocking {
            preferencesRepository.getSubstackSid().also { cachedSid = it }
        }

        if (sid.isBlank()) {
            return chain.proceed(request)
        }

        val authenticatedRequest = request.newBuilder()
            .header("Cookie", "substack.sid=$sid")
            .build()

        return chain.proceed(authenticatedRequest)
    }

    fun clearCache() {
        cachedSid = null
    }
}
