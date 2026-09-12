package com.viple.looby.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.viple.looby.core.model.MobileTokenResponse
import com.viple.looby.core.model.MobileUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredSession(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: MobileUser
) {
    fun isAccessTokenFresh(skewSeconds: Long = 30) = Instant.now().plusSeconds(skewSeconds).isBefore(accessTokenExpiresAt)
}

/** Stockage chiffré (Android Keystore) des jetons, du deviceId stable et du jeton Viple ID. */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context, private val json: Json) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "looby_secure_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _session = MutableStateFlow(load())
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    val deviceModel: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    val osVersion: String get() = "Android ${Build.VERSION.RELEASE}"

    var vipleIdAccessToken: String?
        get() = prefs.getString(KEY_VIPLE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_VIPLE_TOKEN, value).apply()

    var pushToken: String?
        get() = prefs.getString(KEY_PUSH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_PUSH_TOKEN, value).apply()

    @Synchronized
    fun save(response: MobileTokenResponse) {
        val stored = StoredSession(
            accessToken = response.accessToken,
            accessTokenExpiresAt = Instant.parse(response.accessTokenExpiresAtUtc.ensureUtc()),
            refreshToken = response.refreshToken,
            refreshTokenExpiresAt = Instant.parse(response.refreshTokenExpiresAtUtc.ensureUtc()),
            user = response.user
        )
        prefs.edit()
            .putString(KEY_ACCESS, stored.accessToken)
            .putLong(KEY_ACCESS_EXP, stored.accessTokenExpiresAt.epochSecond)
            .putString(KEY_REFRESH, stored.refreshToken)
            .putLong(KEY_REFRESH_EXP, stored.refreshTokenExpiresAt.epochSecond)
            .putString(KEY_USER, json.encodeToString(stored.user))
            .apply()
        _session.value = stored
    }

    @Synchronized
    fun updateUser(user: MobileUser) {
        val current = _session.value ?: return
        prefs.edit().putString(KEY_USER, json.encodeToString(user)).apply()
        _session.value = current.copy(user = user)
    }

    @Synchronized
    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS).remove(KEY_ACCESS_EXP).remove(KEY_REFRESH).remove(KEY_REFRESH_EXP)
            .remove(KEY_USER).remove(KEY_VIPLE_TOKEN)
            .apply()
        _session.value = null
    }

    private fun load(): StoredSession? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val userJson = prefs.getString(KEY_USER, null) ?: return null
        return runCatching {
            StoredSession(
                accessToken = access,
                accessTokenExpiresAt = Instant.ofEpochSecond(prefs.getLong(KEY_ACCESS_EXP, 0)),
                refreshToken = refresh,
                refreshTokenExpiresAt = Instant.ofEpochSecond(prefs.getLong(KEY_REFRESH_EXP, 0)),
                user = json.decodeFromString(userJson)
            )
        }.getOrNull()
    }

    private fun String.ensureUtc() = if (endsWith("Z") || contains('+')) this else this + "Z"

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_ACCESS_EXP = "access_token_exp"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_REFRESH_EXP = "refresh_token_exp"
        const val KEY_USER = "user"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_VIPLE_TOKEN = "viple_id_access_token"
        const val KEY_PUSH_TOKEN = "push_token"
    }
}
