package com.example.kabensatudata.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication token storage using EncryptedSharedPreferences.
 * Tokens are encrypted at rest to protect sensitive data if device is lost.
 */
@Singleton
class AuthTokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = try {
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore corrupted (e.g. re-install with different signing key)
        // Delete the corrupted prefs file and re-create
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        try { context.deleteSharedPreferences("auth_prefs") } catch (_: Exception) {}
        // Also reset permission setup flag so user goes through setup again
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("permission_setup_done", false).apply()
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLES = "user_roles"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        // BOK (bokbaru) token — separate from kabensatudata token
        private const val KEY_BOK_TOKEN = "bok_token"
        private const val KEY_USER_PASSWORD = "user_password"
    }

    fun saveToken(token: String, expiresAt: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getExpiresAt(): String? = prefs.getString(KEY_EXPIRES_AT, null)

    fun saveUser(id: Long, name: String, email: String, roles: List<String>) {
        prefs.edit()
            .putLong(KEY_USER_ID, id)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_ROLES, roles.joinToString(","))
            .apply()
    }

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, 0)
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    fun getUserRoles(): List<String> = prefs.getString(KEY_USER_ROLES, "")
        ?.split(",")
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    /**
     * Returns true if user can create/edit/delete data.
     * 'admin', 'super_admin', 'bidan', and 'nakes' roles can edit.
     * 'petugas' is read-only.
     */
    fun canEdit(): Boolean {
        val roles = getUserRoles()
        return roles.any {
            it.equals("admin", ignoreCase = true) ||
            it.equals("super_admin", ignoreCase = true) ||
            it.equals("bidan", ignoreCase = true) ||
            it.equals("nakes", ignoreCase = true)
        }
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            saveDeviceId(id)
        }
        return id
    }

    fun saveLastSyncTimestamp(timestamp: String) {
        prefs.edit().putString(KEY_LAST_SYNC, timestamp).apply()
    }

    fun getLastSyncTimestamp(): String? = prefs.getString(KEY_LAST_SYNC, null)

    // ─── BOK (bokbaru) Token ────────────────────────────────────────────────
    fun saveBokToken(token: String) {
        prefs.edit().putString(KEY_BOK_TOKEN, token).apply()
    }

    fun getBokToken(): String? = prefs.getString(KEY_BOK_TOKEN, null)

    fun isBokLoggedIn(): Boolean = getBokToken() != null

    fun saveBokRole(role: String) {
        prefs.edit().putString("bok_user_role", role).apply()
    }

    fun getBokRole(): String = prefs.getString("bok_user_role", "") ?: ""

    fun isBokAdmin(): Boolean {
        val role = getBokRole()
        return role.equals("superadmin", ignoreCase = true) || role.equals("admin", ignoreCase = true)
    }

    /**
     * Password stored encrypted for auto-login to bokbaru.
     * Used to get a BOK Sanctum token using the same credentials.
     */
    fun savePassword(password: String) {
        prefs.edit().putString(KEY_USER_PASSWORD, password).apply()
    }

    fun getPassword(): String? = prefs.getString(KEY_USER_PASSWORD, null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
