package com.mobileclaude.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ProfileRepository(context: Context) {
    private val preferences = context.getSharedPreferences("server_profiles", Context.MODE_PRIVATE)

    fun load(): List<ServerProfile> {
        val raw = preferences.getString("profiles", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ServerProfile(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        host = item.optString("host"),
                        port = item.optInt("port", 22),
                        username = item.optString("username"),
                        hostKey = item.optString("hostKey"),
                        fingerprint = item.optString("fingerprint"),
                    )
                )
            }
        }
    }

    fun save(profile: ServerProfile) {
        val updated = load().filterNot { it.id == profile.id } + profile
        val array = JSONArray()
        updated.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("host", it.host)
                    .put("port", it.port)
                    .put("username", it.username)
                    .put("hostKey", it.hostKey)
                    .put("fingerprint", it.fingerprint)
            )
        }
        preferences.edit().putString("profiles", array.toString()).apply()
    }

    fun delete(id: String) {
        val array = JSONArray()
        load().filterNot { it.id == id }.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("host", it.host)
                    .put("port", it.port)
                    .put("username", it.username)
                    .put("hostKey", it.hostKey)
                    .put("fingerprint", it.fingerprint)
            )
        }
        preferences.edit().putString("profiles", array.toString()).apply()
    }
}
