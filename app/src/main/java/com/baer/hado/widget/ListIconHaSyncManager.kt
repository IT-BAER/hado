package com.baer.hado.widget

import android.content.Context
import android.util.Log
import com.baer.hado.data.local.TokenManager

/**
 * Syncs local list icon choices to Home Assistant when that choice can be represented as a
 * standard entity-registry icon override. A null accountId means the active server.
 */
object ListIconHaSyncManager {

    enum class SyncAvailability {
        AVAILABLE,
        REQUIRES_ADMIN,
        UNAVAILABLE
    }

    private const val PREFS_NAME = "hado_list_icon_sync"
    private const val KEY_BASELINE_PREFIX = "ha_icon_baseline_"
    private const val NULL_SENTINEL = "__null__"

    fun getSyncAvailability(context: Context, accountId: String?): SyncAvailability {
        val httpClient = WidgetHttpClient(context, accountId)
        if (!httpClient.hasRemoteSession()) {
            return SyncAvailability.UNAVAILABLE
        }

        val currentUser = httpClient.getCurrentUserInfo() ?: return SyncAvailability.AVAILABLE
        return if (currentUser.isAdmin) {
            SyncAvailability.AVAILABLE
        } else {
            SyncAvailability.REQUIRES_ADMIN
        }
    }

    fun syncEmojiOverride(
        context: Context,
        accountId: String?,
        entityId: String,
        emoji: String,
        currentHaIcon: String?
    ) {
        syncMdiOverride(
            context = context,
            accountId = accountId,
            entityId = entityId,
            mdiIcon = ListIconManager.mapEmojiToHaIcon(emoji),
            currentHaIcon = currentHaIcon
        )
    }

    fun syncMdiOverride(
        context: Context,
        accountId: String?,
        entityId: String,
        mdiIcon: String,
        currentHaIcon: String?
    ) {
        if (getSyncAvailability(context, accountId) != SyncAvailability.AVAILABLE) {
            return
        }

        val httpClient = WidgetHttpClient(context, accountId)

        rememberBaselineIfAbsent(context, baselineKey(httpClient.accountId, entityId), currentHaIcon)
        if (!httpClient.updateTodoListIcon(entityId, mdiIcon)) {
            Log.w("HAdo", "Failed to sync mdi icon to HA for $entityId")
        }
    }

    fun restoreOriginalIconIfNeeded(context: Context, accountId: String?, entityId: String) {
        val httpClient = WidgetHttpClient(context, accountId)
        val key = baselineKey(httpClient.accountId, entityId)
        if (!hasRememberedBaseline(context, key)) {
            return
        }

        if (getSyncAvailability(context, accountId) != SyncAvailability.AVAILABLE) {
            return
        }

        val originalIcon = getRememberedBaseline(context, key)
        if (httpClient.updateTodoListIcon(entityId, originalIcon)) {
            clearRememberedBaseline(context, key)
        } else {
            Log.w("HAdo", "Failed to restore HA icon for $entityId")
        }
    }

    private fun syncPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun baselineKey(accountId: String?, entityId: String): String =
        "$KEY_BASELINE_PREFIX${TokenManager.scopedKey(accountId, entityId)}"

    private fun rememberBaselineIfAbsent(context: Context, key: String, currentHaIcon: String?) {
        val prefs = syncPrefs(context)
        if (!prefs.contains(key)) {
            prefs.edit().putString(key, currentHaIcon ?: NULL_SENTINEL).apply()
        }
    }

    private fun hasRememberedBaseline(context: Context, key: String): Boolean {
        return syncPrefs(context).contains(key)
    }

    private fun getRememberedBaseline(context: Context, key: String): String? {
        val stored = syncPrefs(context).getString(key, NULL_SENTINEL)
        return stored?.takeUnless { it == NULL_SENTINEL }
    }

    private fun clearRememberedBaseline(context: Context, key: String) {
        syncPrefs(context).edit().remove(key).apply()
    }
}