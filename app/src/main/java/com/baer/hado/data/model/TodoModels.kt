package com.baer.hado.data.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class TodoItem(
    val uid: String,
    val summary: String,
    val status: TodoItemStatus,
    val description: String? = null,
    val due: String? = null
) {
    val isCompleted: Boolean get() = status == TodoItemStatus.COMPLETED

    /** Parsed due date (date-only or date part of datetime) */
    val dueDate: LocalDate?
        get() = due?.let { parseDueDate(it) }

    /** Parsed due datetime (only if due contains time component) */
    val dueDateTime: LocalDateTime?
        get() = due?.let { parseDueDateTime(it) }

    /** Whether this item is overdue */
    val isOverdue: Boolean
        get() {
            if (isCompleted || due == null) return false
            val dt = dueDateTime
            if (dt != null) return dt.isBefore(LocalDateTime.now())
            val d = dueDate
            if (d != null) return d.isBefore(LocalDate.now())
            return false
        }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE  // YYYY-MM-DD

        private fun parseDueDate(due: String): LocalDate? = try {
            LocalDate.parse(due.substringBefore("T").substringBefore(" "), DATE_FORMAT)
        } catch (_: DateTimeParseException) { null }

        private fun parseDueDateTime(due: String): LocalDateTime? = try {
            val normalized = due.replace(" ", "T")
            when {
                normalized.contains("T") -> {
                    // Handle offset datetimes like 2026-04-09T12:00:00+02:00
                    if (normalized.matches(Regex(".*[+\\-]\\d{2}:\\d{2}$")) || normalized.endsWith("Z")) {
                        java.time.OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime()
                    } else {
                        LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    }
                }
                else -> null
            }
        } catch (_: DateTimeParseException) { null }
    }
}

enum class TodoItemStatus {
    @SerializedName("needs_action") NEEDS_ACTION,
    @SerializedName("completed") COMPLETED
}

/** Feature flags from HA entity supported_features bitmask */
object TodoListFeature {
    const val CREATE_TODO_ITEM = 1
    const val DELETE_TODO_ITEM = 2
    const val UPDATE_TODO_ITEM = 4
    const val MOVE_TODO_ITEM = 8
    const val SET_DUE_DATE_ON_ITEM = 16
    const val SET_DUE_DATETIME_ON_ITEM = 32
    const val SET_DESCRIPTION_ON_ITEM = 64

    fun hasFeature(supportedFeatures: Int?, feature: Int): Boolean =
        supportedFeatures != null && (supportedFeatures and feature) != 0
}

data class TodoItemsResponse(
    val items: List<TodoItem>
)

data class ServiceCallRequest(
    @SerializedName("entity_id") val entityId: String,
    val item: String? = null,
    val rename: String? = null,
    val status: String? = null
)
