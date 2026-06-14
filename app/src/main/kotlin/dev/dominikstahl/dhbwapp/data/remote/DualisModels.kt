// Portions of this file are derived from DHBWorld (https://github.com/heinf/DHBWorld)
// Copyright (c) 2022 Linus Pust, Daria Kodolova, Christian Zäske
// Licensed under the Apache License, Version 2.0

package dev.dominikstahl.dhbwapp.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class DualisSemester(
    val name: String,
    val value: String,
    val courses: List<DualisSemesterCourse> = emptyList()
)

@Serializable
data class DualisSemesterCourse(
    val number: String,
    val name: String,
    val grade: String, // "1.0", "2,3", or empty
    val credits: String,
    val examLink: String? = null, // relative URL
    val exams: List<DualisExam> = emptyList()
)

@Serializable
data class DualisExam(
    val topic: String,
    val grade: String
)

@Serializable
data class DualisOverallData(
    val earnedCredits: String,
    val neededCredits: String,
    val courses: List<DualisOverallCourse> = emptyList()
)

@Serializable
data class DualisOverallCourse(
    val moduleID: String,
    val moduleName: String,
    val credits: String,
    val grade: String,
    val passed: Boolean
)

@Serializable
data class DualisGPA(
    val totalGPA: String = "N/A",
    val majorCourseGPA: String = "N/A"
)

@Serializable
data class DualisDocument(
    val url: String, // relative path
    val name: String,
    val date: String // formatted dd.MM.yyyy HH:mm
)
