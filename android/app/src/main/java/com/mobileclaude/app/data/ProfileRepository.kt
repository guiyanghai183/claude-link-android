package com.mobileclaude.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ProfileRepository(context: Context) {
    private val preferences = context.getSharedPreferences("server_profiles", Context.MODE_PRIVATE)

    fun load(): List<ServerProfile> {
        val raw = preferences.getString(PROFILES_KEY, "[]") ?: "[]"
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
        preferences.edit().putString(PROFILES_KEY, array.toString()).apply()
    }

    fun lastConnectedProfileId(): String? = preferences
        .getString(LAST_CONNECTED_PROFILE_KEY, null)
        ?.takeIf { it.isNotBlank() }

    fun setLastConnectedProfileId(profileId: String?) {
        preferences.edit().apply {
            if (profileId.isNullOrBlank()) remove(LAST_CONNECTED_PROFILE_KEY)
            else putString(LAST_CONNECTED_PROFILE_KEY, profileId)
        }.apply()
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
        preferences.edit().apply {
            putString(PROFILES_KEY, array.toString())
            if (lastConnectedProfileId() == id) remove(LAST_CONNECTED_PROFILE_KEY)
        }.apply()
    }

    private companion object {
        const val PROFILES_KEY = "profiles"
        const val LAST_CONNECTED_PROFILE_KEY = "last_connected_profile_id"
    }
}
