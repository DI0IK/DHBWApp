package dev.dominikstahl.dhbwapp.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodleDao {

    @Query("SELECT * FROM moodle_courses")
    fun getCoursesFlow(): Flow<List<CachedMoodleCourse>>

    @Query("SELECT * FROM moodle_courses")
    fun getCourses(): List<CachedMoodleCourse>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCourses(courses: List<CachedMoodleCourse>): List<Long>

    @Query("DELETE FROM moodle_courses")
    fun deleteCourses(): Int

    @Transaction
    fun refreshCourses(courses: List<CachedMoodleCourse>) {
        deleteCourses()
        insertCourses(courses)
    }

    @Query("SELECT * FROM moodle_assignments")
    fun getAssignmentsFlow(): Flow<List<CachedMoodleAssignment>>

    @Query("SELECT * FROM moodle_assignments WHERE courseId = :courseId")
    fun getAssignmentsForCourseFlow(courseId: Int): Flow<List<CachedMoodleAssignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAssignments(assignments: List<CachedMoodleAssignment>): List<Long>

    @Query("DELETE FROM moodle_assignments")
    fun deleteAssignments(): Int

    @Query("DELETE FROM moodle_assignments WHERE courseId = :courseId")
    fun deleteAssignmentsForCourse(courseId: Int): Int

    @Transaction
    fun refreshAssignments(assignments: List<CachedMoodleAssignment>) {
        deleteAssignments()
        insertAssignments(assignments)
    }

    @Query("SELECT * FROM moodle_course_contents WHERE courseId = :courseId")
    fun getContentForCourseFlow(courseId: Int): Flow<List<CachedMoodleContent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContents(contents: List<CachedMoodleContent>): List<Long>

    @Query("DELETE FROM moodle_course_contents WHERE courseId = :courseId")
    fun deleteContentForCourse(courseId: Int): Int

    @Query("DELETE FROM moodle_course_contents")
    fun deleteContents(): Int

    @Transaction
    fun refreshContentForCourse(courseId: Int, contents: List<CachedMoodleContent>) {
        deleteContentForCourse(courseId)
        insertContents(contents)
    }
}
