package com.motorola.motomouse.data

import org.json.JSONException
import org.json.JSONObject

data class PairingInfo(
    val version: Int,
    val serverIp: String,
    val port: Int,
    val token: String,
    val serverName: String,
    val issuedAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put(KEY_VERSION, version)
        .put(KEY_SERVER_IP, serverIp)
        .put(KEY_PORT, port)
        .put(KEY_TOKEN, token)
        .put(KEY_SERVER_NAME, serverName)
        .put(KEY_ISSUED_AT, issuedAt)
        .toString()

    companion object {
        private const val KEY_VERSION = "version"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        private const val KEY_SERVER_NAME = "server_name"
        private const val KEY_ISSUED_AT = "issued_at"

        fun fromQrPayload(rawValue: String): Result<PairingInfo> = runCatching {
            val json = JSONObject(rawValue.trim())
            PairingInfo(
                version = json.optInt(KEY_VERSION, 1),
                serverIp = json.getString(KEY_SERVER_IP),
                port = json.getInt(KEY_PORT),
                token = json.getString(KEY_TOKEN),
                serverName = json.optString(KEY_SERVER_NAME, "Windows PC"),
                issuedAt = json.optLong(KEY_ISSUED_AT, System.currentTimeMillis()),
            )
        }

        fun fromStoredJson(rawValue: String): PairingInfo? = try {
            fromQrPayload(rawValue).getOrNull()
        } catch (_: JSONException) {
            null
        }
    }
}

