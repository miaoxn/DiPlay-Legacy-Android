package com.shilapi.xcertplay.bluetooth

import org.json.JSONArray
import org.json.JSONObject

data class KnownPhone(
    val address: String,
    val name: String,
    val alias: String? = null,
    val lastConnectedTime: Long = 0L,
) {
    fun displayName(): String = alias?.takeIf { it.isNotBlank() } ?: name

    fun toJson(): JSONObject = JSONObject().apply {
        put("address", address)
        put("name", name)
        put("alias", alias ?: "")
        put("lastConnectedTime", lastConnectedTime)
    }

    companion object {
        fun fromJson(json: JSONObject): KnownPhone? {
            val address = json.optString("address")?.takeIf { it.isNotBlank() } ?: return null
            val name = json.optString("name").ifEmpty { "iPhone" }
            val alias = json.optString("alias").takeIf { it.isNotBlank() }
            val lastConnectedTime = json.optLong("lastConnectedTime", 0L)
            return KnownPhone(address, name, alias, lastConnectedTime)
        }

        fun parseList(jsonString: String?): List<KnownPhone> {
            if (jsonString.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(jsonString)
                val list = mutableListOf<KnownPhone>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    fromJson(obj)?.let { list.add(it) }
                }
                list
            }.getOrDefault(emptyList())
        }

        fun serializeList(list: List<KnownPhone>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
