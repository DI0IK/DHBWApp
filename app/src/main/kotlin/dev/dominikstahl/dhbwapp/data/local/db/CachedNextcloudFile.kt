package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nextcloud_files")
data class CachedNextcloudFile(
    @PrimaryKey
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: String?,
    val contentType: String?,
    val parentPath: String
)
