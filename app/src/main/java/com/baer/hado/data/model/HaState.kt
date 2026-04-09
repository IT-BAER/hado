package com.baer.hado.data.model

import com.google.gson.annotations.SerializedName

data class HaState(
    @SerializedName("entity_id") val entityId: String,
    val state: String,
    val attributes: HaAttributes
)

data class HaAttributes(
    @SerializedName("friendly_name") val friendlyName: String? = null,
    @SerializedName("supported_features") val supportedFeatures: Int? = null
)
