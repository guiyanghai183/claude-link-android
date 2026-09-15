package com.mobileclaude.app.data

import com.mobileclaude.app.security.CredentialVault
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DeepSeekMessage(val role: String, val content: String)

data class DeepSeekConversation(
    val id: String,
    val title: String,
    val messages: List<DeepSeekMessage>,
)

/** Local, encrypted conversations; they never enter the SSH bridge or GitHub. */
class DeepSeekChatRepository(private val vault: CredentialVault) {
    private fun name(profileId: String) = "deepseek_chats_$profileId"

    fun load(profileId: String): List<DeepSeekConversation> {
        val bytes = vault.loadSecret(name(profileId)) ?: return emptyList()
        return try {
            val array = JSONArray(bytes.toString(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val chat = array.optJSONObject(index) ?: continue
                    val messages = chat.optJSONArray("messages") ?: JSONArray()
                    add(DeepSeekConversation(
                        id = chat.optString("id"),
                        title = chat.optString("title", "新对话"),
                        messages = buildList {
                            for (messageIndex in 0 until messages.length()) {
                                val message = messages.optJSONObject(messageIndex) ?: continue
                                val role = message.optString("role")
                                if (role == "user" || role == "assistant") {
                                    add(DeepSeekMessage(role, message.optString("content")))
                                }
                            }
                        },
                    ))
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            bytes.fill(0)
        }
    }

    fun save(profileId: String, conversations: List<DeepSeekConversation>) {
        val array = JSONArray()
        conversations.forEach { chat ->
            val messages = JSONArray()
            chat.messages.forEach { message ->
                messages.put(JSONObject().put("role", message.role).put("content", message.content))
            }
            array.put(JSONObject().put("id", chat.id).put("title", chat.title).put("messages", messages))
        }
        val bytes = array.toString().toByteArray(Charsets.UTF_8)
        try {
            vault.saveSecret(name(profileId), bytes)
        } finally {
            bytes.fill(0)
        }
    }

    fun delete(profileId: String) = vault.deleteSecret(name(profileId))

    companion object {
        fun newConversation() = DeepSeekConversation(UUID.randomUUID().toString(), "新对话", emptyList())
    }
}
