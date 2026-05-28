package com.example

data class ServerModel(
    val id: String,
    val name: String,
    val url: String,
    val region: Region,
    val status: ServerStatus = ServerStatus.CHECKING,
    val latencyMs: Long? = null,
    val dnsTimeMs: Long? = null,
    val resolvedIp: String? = null
)

enum class Region { RU, EU }
enum class ServerStatus { ONLINE, OFFLINE, CHECKING }
