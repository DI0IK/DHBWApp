package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NextcloudDao {

    @Query("SELECT * FROM nextcloud_files WHERE parentPath = :parentPath")
    fun getFilesInDirectory(parentPath: String): List<CachedNextcloudFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFiles(files: List<CachedNextcloudFile>)

    @Query("DELETE FROM nextcloud_files WHERE parentPath = :parentPath")
    fun deleteFilesInDirectory(parentPath: String): Int

    @Query("DELETE FROM nextcloud_files")
    fun deleteAll(): Int

    @Transaction
    fun refreshDirectory(parentPath: String, files: List<CachedNextcloudFile>) {
        deleteFilesInDirectory(parentPath)
        insertFiles(files)
    }
}
