package com.voidchat.app.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "voidchat_secure_preferences",
        Context.MODE_PRIVATE
    )

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

    var defaultSelfDestruct: Int
        get() = prefs.getInt("default_self_destruct", 0) // 0 = Off
        set(value) = prefs.edit().putInt("default_self_destruct", value).apply()

    var biometricLock: Boolean
        get() = prefs.getBoolean("biometric_lock", false)
        set(value) = prefs.edit().putBoolean("biometric_lock", value).apply()

    var pinCode: String?
        get() = prefs.getString("pin_code", null)
        set(value) = prefs.edit().putString("pin_code", value).apply()

    var theme: String
        get() = prefs.getString("theme", "DARK") ?: "DARK"
        set(value) = prefs.edit().putString("theme", value).apply()

    var dismissedAnnouncementId: String?
        get() = prefs.getString("dismissed_announcement_id", null)
        set(value) = prefs.edit().putString("dismissed_announcement_id", value).apply()

    var donorPerks: Boolean
        get() = prefs.getBoolean("donor_perks", false)
        set(value) = prefs.edit().putBoolean("donor_perks", value).apply()

    fun saveChatKey(chatId: String, keyBase64: String) {
        prefs.edit().putString("chat_key_$chatId", keyBase64).apply()
    }

    fun getChatKey(chatId: String): String? {
        return prefs.getString("chat_key_$chatId", null)
    }

    fun deleteChatKey(chatId: String) {
        prefs.edit().remove("chat_key_$chatId").apply()
    }

    fun getLocallyDeletedChats(): Set<String> {
        return prefs.getStringSet("locally_deleted_chats", emptySet()) ?: emptySet()
    }

    fun saveLocallyDeletedChats(chats: Set<String>) {
        prefs.edit().putStringSet("locally_deleted_chats", chats).apply()
    }

    fun saveBlockedUser(displayId: String) {
        val blocked = getBlockedUsers().toMutableSet()
        blocked.add(displayId)
        prefs.edit().putStringSet("blocked_users", blocked).apply()
    }

    fun removeBlockedUser(displayId: String) {
        val blocked = getBlockedUsers().toMutableSet()
        blocked.remove(displayId)
        prefs.edit().putStringSet("blocked_users", blocked).apply()
    }

    fun getBlockedUsers(): Set<String> {
        return prefs.getStringSet("blocked_users", emptySet()) ?: emptySet()
    }

    fun isUserBlocked(displayId: String): Boolean {
        return getBlockedUsers().contains(displayId)
    }

    fun saveChatSelfDestructDefault(chatId: String, seconds: Int) {
        prefs.edit().putInt("self_destruct_$chatId", seconds).apply()
    }

    fun getChatSelfDestructDefault(chatId: String): Int {
        return prefs.getInt("self_destruct_$chatId", 0)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
