package com.baer.hado.widget

import android.content.Context
import android.util.Log

/**
 * Syncs local list icon choices to Home Assistant when that choice can be represented as a
 * standard entity-registry icon override.
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

    fun getSyncAvailability(context: Context): SyncAvailability {
        val httpClient = WidgetHttpClient(context)
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
        entityId: String,
        emoji: String,
        currentHaIcon: String?
    ) {
        syncMdiOverride(
            context = context,
            entityId = entityId,
            mdiIcon = ListIconManager.mapEmojiToHaIcon(emoji),
            currentHaIcon = currentHaIcon
        )
    }

    fun syncMdiOverride(
        context: Context,
        entityId: String,
        mdiIcon: String,
        currentHaIcon: String?
    ) {
        if (getSyncAvailability(context) != SyncAvailability.AVAILABLE) {
            return
        }

        val httpClient = WidgetHttpClient(context)

        rememberBaselineIfAbsent(context, entityId, currentHaIcon)
        if (!httpClient.updateTodoListIcon(entityId, mdiIcon)) {
            Log.w("HAdo", "Failed to sync mdi icon to HA for $entityId")
        }
    }

    fun restoreOriginalIconIfNeeded(context: Context, entityId: String) {
        if (!hasRememberedBaseline(context, entityId)) {
            return
        }

        if (getSyncAvailability(context) != SyncAvailability.AVAILABLE) {
            return
        }

        val httpClient = WidgetHttpClient(context)

        val originalIcon = getRememberedBaseline(context, entityId)
        if (httpClient.updateTodoListIcon(entityId, originalIcon)) {
            clearRememberedBaseline(context, entityId)
        } else {
            Log.w("HAdo", "Failed to restore HA icon for $entityId")
        }
    }

    private fun syncPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun baselineKey(entityId: String): String = "$KEY_BASELINE_PREFIX$entityId"

    private fun rememberBaselineIfAbsent(context: Context, entityId: String, currentHaIcon: String?) {
        val prefs = syncPrefs(context)
        val key = baselineKey(entityId)
        if (!prefs.contains(key)) {
            prefs.edit().putString(key, currentHaIcon ?: NULL_SENTINEL).apply()
        }
    }

    private fun hasRememberedBaseline(context: Context, entityId: String): Boolean {
        return syncPrefs(context).contains(baselineKey(entityId))
    }

    private fun getRememberedBaseline(context: Context, entityId: String): String? {
        val stored = syncPrefs(context).getString(baselineKey(entityId), NULL_SENTINEL)
        return stored?.takeUnless { it == NULL_SENTINEL }
    }

    private fun clearRememberedBaseline(context: Context, entityId: String) {
        syncPrefs(context).edit().remove(baselineKey(entityId)).apply()
    }
}