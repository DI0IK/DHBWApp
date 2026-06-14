// Portions of this file are derived from DHBWorld (https://github.com/heinf/DHBWorld)
// Copyright (c) 2022 Linus Pust, Daria Kodolova, Christian Zäske
// Licensed under the Apache License, Version 2.0

package dev.dominikstahl.dhbwapp.data.remote

import org.jsoup.Jsoup
import java.util.regex.Pattern

object DualisHtmlParser {

    private val EXAM_LINK_PATTERN = Pattern.compile("""dl_popUp\("(.+?)","Resultdetails"""")

    fun parseSemesters(html: String): List<DualisSemester> {
        val document = Jsoup.parse(html)
        val options = document.select("select#semester > option")
        return options.map { option ->
            DualisSemester(
                name = option.text().trim(),
                value = option.attr("value").trim()
            )
        }
    }

    fun parseCourses(html: String): List<DualisSemesterCourse> {
        val document = Jsoup.parse(html)
        val rows = document.select("table.nb.list").firstOrNull()?.select("tbody tr") ?: emptyList()
        val courses = mutableListOf<DualisSemesterCourse>()

        for (row in rows) {
            val tds = row.select("td")
            if (tds.size >= 4) {
                val number = tds[0].text().trim()
                if (number.isEmpty()) continue // Skip empty rows or header placeholders

                val name = tds[1].text().trim()
                val grade = tds[2].text().trim()
                val credits = tds[3].text().trim()

                // Check td[5] for script to extract popup link
                var examLink: String? = null
                if (tds.size > 5) {
                    val script = tds[5].select("script").firstOrNull()?.html()
                    if (script != null) {
                        val matcher = EXAM_LINK_PATTERN.matcher(script)
                        if (matcher.find()) {
                            examLink = matcher.group(1)
                        }
                    }
                }

                courses.add(
                    DualisSemesterCourse(
                        number = number,
                        name = name,
                        grade = grade,
                        credits = credits,
                        examLink = examLink
                    )
                )
            }
        }
        return courses
    }

    fun parseExams(html: String): List<DualisExam> {
        val document = Jsoup.parse(html)
        val rows = document.select("table").firstOrNull()?.select("tr") ?: emptyList()
        val exams = mutableListOf<DualisExam>()

        for (row in rows) {
            val tds = row.select("td")
            if (tds.size >= 4) {
                val topicTd = tds[1]
                val gradeTd = tds[3]
                if (topicTd.hasClass("tbdata") && gradeTd.text().trim().isNotEmpty()) {
                    exams.add(
                        DualisExam(
                            topic = topicTd.text().trim(),
                            grade = gradeTd.text().trim()
                        )
                    )
                }
            }
        }
        return exams
    }

    fun parseOverallData(html: String): DualisOverallData {
        val document = Jsoup.parse(html)
        val tables = document.select("table.nb.list.students_results")
        val firstTable = tables.firstOrNull() ?: return DualisOverallData("", "")

        var earnedCredits = ""
        var neededCredits = ""
        val courses = mutableListOf<DualisOverallCourse>()

        val rows = firstTable.select("tr:not(.subhead,.tbsubhead)")
        for (row in rows) {
            val tds = row.select("td")
            if (tds.isEmpty()) continue

            val hasLevel00 = tds[0].select(".level00").isNotEmpty()
            if (hasLevel00) {
                // Credit summary row
                if (tds.size == 5) {
                    earnedCredits = tds[2].text().trim()
                } else if (tds.size == 1) {
                    val text = tds[0].text().trim()
                    neededCredits = text.removePrefix("Erforderliche Credits für Abschluss:").trim()
                }
            } else {
                // Module Row
                if (tds.size >= 6) {
                    val moduleID = tds[0].text().trim()
                    if (moduleID.isEmpty()) continue

                    val nameTd = tds[1]
                    val moduleName = if (nameTd.children().isNotEmpty()) {
                        nameTd.child(0).text().trim()
                    } else {
                        nameTd.text().trim()
                    }
                    val credits = tds[3].text().trim()
                    val grade = tds[4].text().trim()
                    val passed = tds[5].select("a, img, span").firstOrNull()?.attr("title") == "Bestanden"

                    courses.add(
                        DualisOverallCourse(
                            moduleID = moduleID,
                            moduleName = moduleName,
                            credits = credits,
                            grade = grade,
                            passed = passed
                        )
                    )
                }
            }
        }

        return DualisOverallData(
            earnedCredits = earnedCredits,
            neededCredits = neededCredits,
            courses = courses
        )
    }

    fun parseGPA(html: String): DualisGPA {
        val document = Jsoup.parse(html)
        val tables = document.select("table.nb.list.students_results")
        if (tables.size < 2) return DualisGPA()

        val secondTable = tables[1]
        val rows = secondTable.select("tr")
        var totalGPA = "N/A"
        var majorCourseGPA = "N/A"

        for (row in rows) {
            val ths = row.select("th")
            if (ths.size >= 2) {
                val label = ths[0].text().trim()
                val value = ths[1].text().trim()
                if (label.contains("Gesamt-GPA")) {
                    totalGPA = value
                } else if (label.contains("Hauptfach-GPA")) {
                    majorCourseGPA = value
                }
            }
        }

        return DualisGPA(totalGPA, majorCourseGPA)
    }

    fun parseDocuments(html: String): List<DualisDocument> {
        val document = Jsoup.parse(html)
        // Documents are in rows of table in form1
        val rows = document.select("#form1 tr")
        val documents = mutableListOf<DualisDocument>()

        for (row in rows) {
            val tds = row.select("td.tbdata")
            if (tds.size >= 5) {
                val name = tds[0].text().trim()
                val date = tds[1].text().trim()
                val time = tds[2].text().trim()
                val downloadUrl = tds[4].select("a").firstOrNull()?.attr("href")?.trim() ?: ""

                if (name.isNotEmpty() && downloadUrl.isNotEmpty()) {
                    documents.add(
                        DualisDocument(
                            url = downloadUrl,
                            name = name,
                            date = "$date $time".trim()
                        )
                    )
                }
            }
        }
        return documents
    }
}
