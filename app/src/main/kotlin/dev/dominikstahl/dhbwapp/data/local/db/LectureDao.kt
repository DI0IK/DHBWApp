package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureDao {

    @Query("SELECT * FROM lectures WHERE course = :course")
    fun getLecturesForCourse(course: String): Flow<List<CachedLecture>>

    @Query("SELECT * FROM lectures WHERE site = :site")
    fun getLecturesForSite(site: String): Flow<List<CachedLecture>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLectures(lectures: List<CachedLecture>)

    @Query("DELETE FROM lectures WHERE course = :course")
    fun deleteLecturesForCourse(course: String): Int

    @Query("DELETE FROM lectures WHERE site = :site")
    fun deleteLecturesForSite(site: String): Int

    @Transaction
    fun refreshLecturesForCourse(course: String, lectures: List<CachedLecture>) {
        deleteLecturesForCourse(course)
        insertLectures(lectures)
    }

    @Transaction
    fun refreshLecturesForSite(site: String, lectures: List<CachedLecture>) {
        deleteLecturesForSite(site)
        insertLectures(lectures)
    }
}
