package com.baer.hado.data.model

/**
 * Lightweight state model for parsing HA API /api/states responses via Gson.
 * Uses snake_case field names to match JSON keys directly (no @SerializedName needed).
 * Shared across widget worker, widget settings, and app settings.
 */
data class SimpleState(
    val entity_id: String,
    val attributes: Map<String, Any>?
)
