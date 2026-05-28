package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ServerRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkServer(server: ServerModel): ServerModel = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var success = false
        var endTime = 0L
        var currentDnsTime: Long? = null
        var currentResolvedIp: String? = null

        // Attempt DNS Resolution First
        try {
            val host = java.net.URL(server.url).host
            val dnsStart = System.currentTimeMillis()
            val address = java.net.InetAddress.getByName(host)
            currentDnsTime = System.currentTimeMillis() - dnsStart
            currentResolvedIp = address.hostAddress
        } catch (e: Exception) {
            // DNS failed
        }

        // Attempt 1: HEAD
        try {
            val headRequest = Request.Builder().url(server.url).head().build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful || response.code in 200..399 || response.code == 405) {
                    success = true
                    endTime = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            // Ignore and fallback
        }

        // Attempt 2: GET (if HEAD fails or returns 405 Method Not Allowed)
        if (!success) {
            try {
                val getRequest = Request.Builder().url(server.url).get().build()
                client.newCall(getRequest).execute().use { response ->
                    if (response.isSuccessful || response.code in 200..399) {
                        success = true
                        endTime = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                // Completely failed
            }
        }

        if (success) {
            server.copy(
                status = ServerStatus.ONLINE, 
                latencyMs = endTime - startTime,
                dnsTimeMs = currentDnsTime,
                resolvedIp = currentResolvedIp
            )
        } else {
            server.copy(
                status = ServerStatus.OFFLINE, 
                latencyMs = null,
                dnsTimeMs = currentDnsTime,
                resolvedIp = currentResolvedIp
            )
        }
    }

    fun getDefaultServers(): List<ServerModel> = listOf(
        ServerModel("ru1", "Яндекс", "https://ya.ru", Region.RU),
        ServerModel("ru2", "ВКонтакте", "https://vk.com", Region.RU),
        ServerModel("ru3", "Mail.ru", "https://mail.ru", Region.RU),
        ServerModel("ru4", "Госуслуги", "https://www.gosuslugi.ru", Region.RU),
        ServerModel("ru5", "Сбербанк", "https://www.sberbank.ru", Region.RU),
        ServerModel("ru6", "Тинькофф", "https://www.tbank.ru", Region.RU),
        
        ServerModel("eu1", "Google", "https://google.com", Region.EU),
        ServerModel("eu2", "Cloudflare DNS", "https://1.1.1.1", Region.EU),
        ServerModel("eu3", "Wikipedia", "https://wikipedia.org", Region.EU),
        ServerModel("eu4", "GitHub", "https://github.com", Region.EU),
        ServerModel("eu5", "Amazon", "https://amazon.com", Region.EU),
        ServerModel("eu6", "OpenAI", "https://openai.com", Region.EU)
    )
}
