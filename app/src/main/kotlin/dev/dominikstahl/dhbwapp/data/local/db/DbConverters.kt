package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.TypeConverter
import dev.dominikstahl.dhbwapp.remote.models.MenuItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class DbConverters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return Json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromMenuItemList(value: List<MenuItem>?): String {
        return Json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toMenuItemList(value: String): List<MenuItem> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromAttachmentList(value: List<CachedMoodleAttachment>?): String {
        return Json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toAttachmentList(value: String): List<CachedMoodleAttachment> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
