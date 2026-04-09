package com.baer.hado.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Manages per-list icon overrides. Each entity can have an emoji or a compressed image.
 * Priority chain: client-side override > MDI emoji mapping > no icon.
 */
object ListIconManager {

    private const val PREFS_NAME = "hado_list_icons"
    private const val KEY_TYPE_PREFIX = "icon_type_"   // "emoji" or "image"
    private const val KEY_VALUE_PREFIX = "icon_value_"  // emoji chars or file name
    private const val ICONS_DIR = "list_icons"
    private const val ICON_SIZE = 48 // px — small enough for widget, big enough for clarity

    enum class IconType { EMOJI, IMAGE }

    data class ResolvedIcon(
        val type: IconType,
        val value: String // emoji chars or absolute file path
    )

    /** Default icon when nothing is set (mdi:clipboard-list → 📋) */
    private const val DEFAULT_EMOJI = "📋"
    /** Special value stored when user explicitly disables the icon */
    private const val DISABLED_VALUE = "__disabled__"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun iconsDir(context: Context): File =
        File(context.filesDir, ICONS_DIR).also { it.mkdirs() }

    private fun imageFile(context: Context, entityId: String): File =
        File(iconsDir(context), "${entityId.replace(".", "_")}.jpg")

    /**
     * Resolve the icon for an entity using the priority chain:
     * 1. Client-side disabled → null
     * 2. Client-side override (emoji or image)
     * 3. MDI icon from HA mapped to emoji
     * 4. Default fallback (📋)
     */
    fun resolveIcon(context: Context, entityId: String, haIcon: String? = null): ResolvedIcon? {
        val p = prefs(context)
        val type = p.getString("$KEY_TYPE_PREFIX$entityId", null)
        val value = p.getString("$KEY_VALUE_PREFIX$entityId", null)

        // 1. Explicitly disabled
        if (type == "disabled") return null

        // 2. Client-side override
        if (type != null && value != null) {
            return when (type) {
                "emoji" -> ResolvedIcon(IconType.EMOJI, value)
                "image" -> {
                    val file = imageFile(context, entityId)
                    if (file.exists()) ResolvedIcon(IconType.IMAGE, file.absolutePath) else null
                }
                else -> null
            }
        }

        // 3. MDI mapping
        if (haIcon != null) {
            val emoji = MDI_TO_EMOJI[haIcon]
            if (emoji != null) return ResolvedIcon(IconType.EMOJI, emoji)
        }

        // 4. Default fallback
        return ResolvedIcon(IconType.EMOJI, DEFAULT_EMOJI)
    }

    /** Check if icon is explicitly disabled for an entity */
    fun isDisabled(context: Context, entityId: String): Boolean {
        return prefs(context).getString("$KEY_TYPE_PREFIX$entityId", null) == "disabled"
    }

    /** Explicitly disable the icon for an entity */
    fun disableIcon(context: Context, entityId: String) {
        imageFile(context, entityId).delete()
        prefs(context).edit().apply {
            putString("$KEY_TYPE_PREFIX$entityId", "disabled")
            remove("$KEY_VALUE_PREFIX$entityId")
            apply()
        }
    }

    fun setEmoji(context: Context, entityId: String, emoji: String) {
        // Remove any existing image
        imageFile(context, entityId).delete()
        prefs(context).edit().apply {
            putString("$KEY_TYPE_PREFIX$entityId", "emoji")
            putString("$KEY_VALUE_PREFIX$entityId", emoji)
            apply()
        }
    }

    /**
     * Compress and save image from Uri. Returns true on success.
     */
    fun setImage(context: Context, entityId: String, uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return false

            // Scale to ICON_SIZE x ICON_SIZE
            val scaled = Bitmap.createScaledBitmap(original, ICON_SIZE, ICON_SIZE, true)
            original.recycle()

            // Save as JPEG
            val file = imageFile(context, entityId)
            file.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            scaled.recycle()

            prefs(context).edit().apply {
                putString("$KEY_TYPE_PREFIX$entityId", "image")
                putString("$KEY_VALUE_PREFIX$entityId", file.name)
                apply()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearIcon(context: Context, entityId: String) {
        imageFile(context, entityId).delete()
        prefs(context).edit().apply {
            remove("$KEY_TYPE_PREFIX$entityId")
            remove("$KEY_VALUE_PREFIX$entityId")
            apply()
        }
    }

    fun loadBitmap(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mapping of common MDI icons to emoji equivalents.
     */
    private val MDI_TO_EMOJI = mapOf(
        "mdi:cart" to "🛒",
        "mdi:cart-outline" to "🛒",
        "mdi:shopping" to "🛒",
        "mdi:format-list-checks" to "✅",
        "mdi:format-list-bulleted" to "📝",
        "mdi:format-list-numbered" to "📝",
        "mdi:clipboard-text" to "📋",
        "mdi:clipboard-text-outline" to "📋",
        "mdi:clipboard-list" to "📋",
        "mdi:clipboard-check" to "📋",
        "mdi:clipboard-check-outline" to "📋",
        "mdi:home" to "🏠",
        "mdi:home-outline" to "🏠",
        "mdi:star" to "⭐",
        "mdi:star-outline" to "⭐",
        "mdi:heart" to "❤️",
        "mdi:heart-outline" to "❤️",
        "mdi:calendar" to "📅",
        "mdi:calendar-blank" to "📅",
        "mdi:calendar-check" to "📅",
        "mdi:calendar-today" to "📅",
        "mdi:book" to "📖",
        "mdi:book-outline" to "📖",
        "mdi:book-open" to "📖",
        "mdi:bookmark" to "🔖",
        "mdi:bookmark-outline" to "🔖",
        "mdi:briefcase" to "💼",
        "mdi:briefcase-outline" to "💼",
        "mdi:account" to "👤",
        "mdi:account-outline" to "👤",
        "mdi:account-group" to "👥",
        "mdi:account-group-outline" to "👥",
        "mdi:bell" to "🔔",
        "mdi:bell-outline" to "🔔",
        "mdi:flag" to "🚩",
        "mdi:flag-outline" to "🚩",
        "mdi:gift" to "🎁",
        "mdi:gift-outline" to "🎁",
        "mdi:lightbulb" to "💡",
        "mdi:lightbulb-outline" to "💡",
        "mdi:wrench" to "🔧",
        "mdi:wrench-outline" to "🔧",
        "mdi:tools" to "🛠️",
        "mdi:cog" to "⚙️",
        "mdi:cog-outline" to "⚙️",
        "mdi:phone" to "📱",
        "mdi:phone-outline" to "📱",
        "mdi:email" to "📧",
        "mdi:email-outline" to "📧",
        "mdi:camera" to "📷",
        "mdi:camera-outline" to "📷",
        "mdi:music" to "🎵",
        "mdi:music-note" to "🎵",
        "mdi:food" to "🍽️",
        "mdi:food-apple" to "🍎",
        "mdi:food-fork-drink" to "🍽️",
        "mdi:silverware-fork-knife" to "🍽️",
        "mdi:pill" to "💊",
        "mdi:medical-bag" to "🏥",
        "mdi:dog" to "🐕",
        "mdi:cat" to "🐈",
        "mdi:paw" to "🐾",
        "mdi:car" to "🚗",
        "mdi:car-outline" to "🚗",
        "mdi:airplane" to "✈️",
        "mdi:run" to "🏃",
        "mdi:walk" to "🚶",
        "mdi:dumbbell" to "🏋️",
        "mdi:weight-lifter" to "🏋️",
        "mdi:school" to "🏫",
        "mdi:school-outline" to "🏫",
        "mdi:pencil" to "✏️",
        "mdi:lead-pencil" to "✏️",
        "mdi:baby-face" to "👶",
        "mdi:baby-face-outline" to "👶",
        "mdi:broom" to "🧹",
        "mdi:washing-machine" to "🧺",
        "mdi:flower" to "🌸",
        "mdi:tree" to "🌳",
        "mdi:leaf" to "🍃",
        "mdi:water" to "💧",
        "mdi:fire" to "🔥",
        "mdi:map-marker" to "📍",
        "mdi:map-marker-outline" to "📍",
        "mdi:package-variant" to "📦",
        "mdi:package-variant-closed" to "📦",
        "mdi:cash" to "💰",
        "mdi:currency-usd" to "💵",
        "mdi:credit-card" to "💳",
        "mdi:credit-card-outline" to "💳",
        "mdi:lock" to "🔒",
        "mdi:lock-outline" to "🔒",
        "mdi:key" to "🔑",
        "mdi:key-variant" to "🔑",
        "mdi:target" to "🎯",
        "mdi:trophy" to "🏆",
        "mdi:trophy-outline" to "🏆",
        "mdi:gamepad-variant" to "🎮",
        "mdi:controller" to "🎮",
        "mdi:movie" to "🎬",
        "mdi:television" to "📺",
        "mdi:laptop" to "💻",
        "mdi:desktop-classic" to "🖥️",
        "mdi:printer" to "🖨️",
        "mdi:basket" to "🧺",
        "mdi:basket-outline" to "🧺",
        "mdi:recycle" to "♻️",
        "mdi:delete" to "🗑️",
        "mdi:trash-can" to "🗑️",
        "mdi:trash-can-outline" to "🗑️",
        "mdi:weather-sunny" to "☀️",
        "mdi:white-balance-sunny" to "☀️",
        "mdi:clock" to "🕐",
        "mdi:clock-outline" to "🕐",
        "mdi:alarm" to "⏰",
        "mdi:timer" to "⏱️",
        "mdi:check" to "✔️",
        "mdi:check-circle" to "✅",
        "mdi:check-circle-outline" to "✅",
        "mdi:checkbox-marked" to "☑️",
        "mdi:checkbox-marked-outline" to "☑️",
        "mdi:note" to "📝",
        "mdi:note-text" to "📝",
        "mdi:note-text-outline" to "📝",
        "mdi:pin" to "📌",
        "mdi:pin-outline" to "📌",
        "mdi:tag" to "🏷️",
        "mdi:tag-outline" to "🏷️",
        "mdi:folder" to "📁",
        "mdi:folder-outline" to "📁",
        "mdi:file" to "📄",
        "mdi:file-document" to "📄",
        "mdi:file-document-outline" to "📄"
    )
}
