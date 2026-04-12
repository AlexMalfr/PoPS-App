package com.malfreyt.alexandre.pops_app.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class GradeLocalStore(context: Context) {
    private val cacheFile = File(context.filesDir, "grades_cache.json")
    private val semesterCacheFile = File(context.filesDir, "semester_cache.json")
    private val mutex = Mutex()
    private val allGradesFlow = MutableStateFlow(readAllGrades())
    private val semesterSnapshotsFlow = MutableStateFlow(readSemesterSnapshots())

    fun observeActiveGrades(): Flow<List<StoredGradeEntity>> {
        return allGradesFlow.map { grades ->
            grades.filter { it.active }
                .sortedWith(
                    compareByDescending<StoredGradeEntity> { it.academicYear }
                        .thenByDescending { it.semester }
                        .thenByDescending { it.dateEpochDay ?: Long.MIN_VALUE }
                        .thenBy { it.subject }
                        .thenBy { it.name }
                )
        }
    }

    fun observeSemesterSnapshots(): Flow<List<SemesterSnapshot>> {
        return semesterSnapshotsFlow.map { snapshots ->
            snapshots.sortedWith(
                compareByDescending<SemesterSnapshot> { it.academicYear }
                    .thenBy { it.semester }
            )
        }
    }

    suspend fun getActiveGrades(): List<StoredGradeEntity> {
        return mutex.withLock {
            allGradesFlow.value.filter { it.active }
        }
    }

    suspend fun replaceAll(grades: List<StoredGradeEntity>) {
        mutex.withLock {
            writeAllGrades(grades)
            allGradesFlow.value = grades
        }
    }

    suspend fun replaceSemesterSnapshots(snapshots: List<SemesterSnapshot>) {
        mutex.withLock {
            writeSemesterSnapshots(snapshots)
            semesterSnapshotsFlow.value = snapshots
        }
    }

    private fun readAllGrades(): List<StoredGradeEntity> {
        if (!cacheFile.exists()) {
            return emptyList()
        }
        val text = runCatching { cacheFile.readText() }.getOrElse { return emptyList() }
        val array = runCatching { JSONArray(text) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    StoredGradeEntity(
                        id = item.optString("id"),
                        matchGroupKey = item.optString("matchGroupKey"),
                        contentFingerprint = item.optString("contentFingerprint"),
                        normalizedName = item.optString("normalizedName"),
                        subjectId = item.optString("subjectId"),
                        subject = item.optString("subject"),
                        name = item.optString("name"),
                        grade = if (item.isNull("grade")) null else item.optDouble("grade"),
                        dateText = item.optString("dateText"),
                        dateEpochDay = if (item.isNull("dateEpochDay")) null else item.optLong("dateEpochDay"),
                        semester = item.optInt("semester"),
                        academicYear = item.optInt("academicYear"),
                        firstSeenAt = item.optLong("firstSeenAt"),
                        lastSeenAt = item.optLong("lastSeenAt"),
                        changeType = item.optString("changeType", NoteChangeType.NONE.name),
                        active = item.optBoolean("active", true),
                    )
                )
            }
        }
    }

    private fun writeAllGrades(grades: List<StoredGradeEntity>) {
        val array = JSONArray()
        grades.forEach { grade ->
            array.put(
                JSONObject()
                    .put("id", grade.id)
                    .put("matchGroupKey", grade.matchGroupKey)
                    .put("contentFingerprint", grade.contentFingerprint)
                    .put("normalizedName", grade.normalizedName)
                    .put("subjectId", grade.subjectId)
                    .put("subject", grade.subject)
                    .put("name", grade.name)
                    .put("grade", grade.grade)
                    .put("dateText", grade.dateText)
                    .put("dateEpochDay", grade.dateEpochDay)
                    .put("semester", grade.semester)
                    .put("academicYear", grade.academicYear)
                    .put("firstSeenAt", grade.firstSeenAt)
                    .put("lastSeenAt", grade.lastSeenAt)
                    .put("changeType", grade.changeType)
                    .put("active", grade.active)
            )
        }
        cacheFile.writeText(array.toString())
    }

    private fun readSemesterSnapshots(): List<SemesterSnapshot> {
        if (!semesterCacheFile.exists()) {
            return emptyList()
        }
        val text = runCatching { semesterCacheFile.readText() }.getOrElse { return emptyList() }
        val array = runCatching { JSONArray(text) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    SemesterSnapshot(
                        academicYear = item.optInt("academicYear"),
                        semester = item.optInt("semester"),
                        modules = item.optJSONArray("modules").toModuleSummaries(),
                        units = item.optJSONArray("units").toUnitSummaries(),
                    )
                )
            }
        }
    }

    private fun writeSemesterSnapshots(snapshots: List<SemesterSnapshot>) {
        val array = JSONArray()
        snapshots.forEach { snapshot ->
            array.put(
                JSONObject()
                    .put("academicYear", snapshot.academicYear)
                    .put("semester", snapshot.semester)
                    .put("modules", JSONArray().apply {
                        snapshot.modules.forEach { module ->
                            put(
                                JSONObject()
                                    .put("groupCode", module.groupCode)
                                    .put("groupTitle", module.groupTitle)
                                    .put("code", module.code)
                                    .put("title", module.title)
                                    .put("coefficientLabel", module.coefficientLabel)
                                    .put("blockLabel", module.blockLabel)
                                    .put("gradeLabel", module.gradeLabel)
                                    .put("averageLabel", module.averageLabel)
                                    .put("rankLabel", module.rankLabel)
                                    .put("creditsLabel", module.creditsLabel)
                                    .put("semester", module.semester)
                                    .put("academicYear", module.academicYear)
                            )
                        }
                    })
                    .put("units", JSONArray().apply {
                        snapshot.units.forEach { unit ->
                            put(
                                JSONObject()
                                    .put("code", unit.code)
                                    .put("title", unit.title)
                                    .put("ectsLabel", unit.ectsLabel)
                                    .put("isCommonCore", unit.isCommonCore)
                                    .put("gradeLabel", unit.gradeLabel)
                                    .put("averageLabel", unit.averageLabel)
                                    .put("rankLabel", unit.rankLabel)
                                    .put("resultLabel", unit.resultLabel)
                                    .put("semester", unit.semester)
                                    .put("academicYear", unit.academicYear)
                            )
                        }
                    })
            )
        }
        semesterCacheFile.writeText(array.toString())
    }
}

private fun JSONArray?.toModuleSummaries(): List<ModuleSummary> {
    if (this == null) {
        return emptyList()
    }
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                ModuleSummary(
                    groupCode = item.optString("groupCode"),
                    groupTitle = item.optString("groupTitle"),
                    code = item.optString("code"),
                    title = item.optString("title"),
                    coefficientLabel = item.optString("coefficientLabel"),
                    blockLabel = item.optString("blockLabel"),
                    gradeLabel = item.optString("gradeLabel"),
                    averageLabel = item.optString("averageLabel"),
                    rankLabel = item.optString("rankLabel"),
                    creditsLabel = item.optString("creditsLabel"),
                    semester = item.optInt("semester"),
                    academicYear = item.optInt("academicYear"),
                )
            )
        }
    }
}

private fun JSONArray?.toUnitSummaries(): List<UnitSummary> {
    if (this == null) {
        return emptyList()
    }
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                UnitSummary(
                    code = item.optString("code"),
                    title = item.optString("title"),
                    ectsLabel = item.optString("ectsLabel"),
                    isCommonCore = item.optBoolean("isCommonCore"),
                    gradeLabel = item.optString("gradeLabel"),
                    averageLabel = item.optString("averageLabel"),
                    rankLabel = item.optString("rankLabel"),
                    resultLabel = item.optString("resultLabel"),
                    semester = item.optInt("semester"),
                    academicYear = item.optInt("academicYear"),
                )
            )
        }
    }
}