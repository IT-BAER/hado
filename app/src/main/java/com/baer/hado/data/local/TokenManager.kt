package com.baer.hado.data.local

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "hado_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** One HA server login. The primary account uses the original unsuffixed keys. */
    inner class Account(val id: String) {
        private fun key(base: String) = if (id == PRIMARY_ACCOUNT_ID) base else "${base}_$id"

        var accessToken: String?
            get() = prefs.getString(key(KEY_ACCESS_TOKEN), null)
            set(value) = prefs.edit().putString(key(KEY_ACCESS_TOKEN), value).apply()

        var refreshToken: String?
            get() = prefs.getString(key(KEY_REFRESH_TOKEN), null)
            set(value) = prefs.edit().putString(key(KEY_REFRESH_TOKEN), value).apply()

        var tokenExpiry: Long
            get() = prefs.getLong(key(KEY_TOKEN_EXPIRY), 0L)
            set(value) = prefs.edit().putLong(key(KEY_TOKEN_EXPIRY), value).apply()

        var serverUrl: String?
            get() = prefs.getString(key(KEY_SERVER_URL), null)
            set(value) = prefs.edit().putString(key(KEY_SERVER_URL), value).apply()

        var name: String?
            get() = prefs.getString(key(KEY_ACCOUNT_NAME), null)
            set(value) = prefs.edit().putString(key(KEY_ACCOUNT_NAME), value?.takeIf { it.isNotBlank() }).apply()

        /** Custom name, else the host; host:port when another server shares the host. */
        val displayName: String
            get() {
                name?.takeIf { it.isNotBlank() }?.let { return it }
                val uri = serverUrl?.let(Uri::parse) ?: return id
                val host = uri.host ?: return id
                val sharesHost = accountIds.any { other ->
                    other != id && Account(other).serverUrl?.let { Uri.parse(it).host } == host
                }
                return if (sharesHost && uri.port != -1) "$host:${uri.port}" else host
            }

        val isLoggedIn: Boolean
            get() = accessToken != null && serverUrl != null

        fun setTokens(accessToken: String, refreshToken: String?, expiresAtMillis: Long) {
            prefs.edit()
                .putString(key(KEY_ACCESS_TOKEN), accessToken)
                .putString(key(KEY_REFRESH_TOKEN), refreshToken)
                .putLong(key(KEY_TOKEN_EXPIRY), expiresAtMillis)
                .apply()
        }

        internal fun clear() {
            prefs.edit()
                .remove(key(KEY_ACCESS_TOKEN))
                .remove(key(KEY_REFRESH_TOKEN))
                .remove(key(KEY_TOKEN_EXPIRY))
                .remove(key(KEY_SERVER_URL))
                .remove(key(KEY_ACCOUNT_NAME))
                .apply()
        }
    }

    /** Ordered account ids. Installs from before multi-server have no list, only the primary keys. */
    val accountIds: List<String>
        get() {
            val stored = prefs.getString(KEY_ACCOUNT_IDS, null)
                ?: return if (prefs.getString(KEY_SERVER_URL, null) != null) listOf(PRIMARY_ACCOUNT_ID) else emptyList()
            return stored.split(',').filter { it.isNotBlank() }
        }

    val accounts: List<Account>
        get() = accountIds.map { Account(it) }

    var activeAccountId: String
        get() {
            val ids = accountIds
            return prefs.getString(KEY_ACTIVE_ACCOUNT, null)?.takeIf { it in ids }
                ?: ids.firstOrNull()
                ?: PRIMARY_ACCOUNT_ID
        }
        set(value) = prefs.edit().putString(KEY_ACTIVE_ACCOUNT, value).apply()

    val activeAccount: Account
        get() = Account(activeAccountId)

    /** Null id means the active account; an unknown id returns null. */
    fun account(id: String?): Account? = when (id) {
        null -> activeAccount
        in accountIds -> Account(id)
        else -> null
    }

    /** Widgets and notifications from before multi-server have no account id; they belong to the primary account. */
    fun resolveWidgetAccountId(id: String?): String? {
        val ids = accountIds
        return (id ?: PRIMARY_ACCOUNT_ID).takeIf { it in ids }
    }

    /**
     * Stores a login and makes it active. A login to a server that already has an account
     * replaces that account's tokens instead of adding a duplicate.
     */
    fun addOrUpdateAccount(
        serverUrl: String,
        accessToken: String,
        refreshToken: String?,
        expiresAtMillis: Long
    ): String {
        val url = serverUrl.trimEnd('/')
        val ids = accountIds
        val id = ids.firstOrNull { Account(it).serverUrl.equals(url, ignoreCase = true) }
            ?: if (ids.isEmpty()) PRIMARY_ACCOUNT_ID else "a" + UUID.randomUUID().toString().replace("-", "").take(8)
        val account = Account(id)
        if (id !in ids) {
            account.clear()
            prefs.edit().putString(KEY_ACCOUNT_IDS, (ids + id).joinToString(",")).apply()
        }
        account.serverUrl = url
        account.setTokens(accessToken, refreshToken, expiresAtMillis)
        activeAccountId = id
        return id
    }

    /** Removes one account. Returns true when no account is left (full logout). */
    fun removeAccount(id: String): Boolean {
        val remaining = accountIds - id
        if (remaining.isEmpty()) {
            clearAll()
            return true
        }
        Account(id).clear()
        prefs.edit().putString(KEY_ACCOUNT_IDS, remaining.joinToString(",")).apply()
        if (prefs.getString(KEY_ACTIVE_ACCOUNT, null) == id) {
            activeAccountId = remaining.first()
        }
        return false
    }

    var accessToken: String?
        get() = activeAccount.accessToken
        set(value) { activeAccount.accessToken = value }

    var refreshToken: String?
        get() = activeAccount.refreshToken
        set(value) { activeAccount.refreshToken = value }

    var tokenExpiry: Long
        get() = activeAccount.tokenExpiry
        set(value) { activeAccount.tokenExpiry = value }

    val serverUrl: String?
        get() = activeAccount.serverUrl

    var isDemoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_MODE, value).apply()

    val isLoggedIn: Boolean
        get() = isDemoMode || activeAccount.isLoggedIn

    fun isTokenExpired(): Boolean {
        return System.currentTimeMillis() >= tokenExpiry
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PRIMARY_ACCOUNT_ID = "primary"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_ACCOUNT_IDS = "account_ids"
        private const val KEY_ACTIVE_ACCOUNT = "active_account"
        private const val KEY_DEMO_MODE = "demo_mode"

        /** Key for entity-keyed local stores; the primary account keeps the bare entity id. */
        fun scopedKey(accountId: String?, entityId: String): String =
            if (accountId == null || accountId == PRIMARY_ACCOUNT_ID) entityId else "${accountId}__$entityId"
    }
}
