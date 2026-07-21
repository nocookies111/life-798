package com.water.widget

import org.json.JSONArray

enum class TaskPlatform { MAIN, APP }

data class PlannedMission(
    val refId: String,
    val name: String,
    val score: Int,
    val limit: Int,
    val platform: TaskPlatform
)

data class MissionWorkItem(
    val refId: String,
    val name: String,
    val score: Int,
    val round: Int,
    val total: Int,
    val platform: TaskPlatform
)

object TaskMissionPlanner {
    fun merge(mainMissions: JSONArray?, appMissions: JSONArray?): List<PlannedMission> {
        val out = mutableListOf<PlannedMission>()
        val seen = mutableSetOf<String>()

        fun addAll(arr: JSONArray?, platform: TaskPlatform) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val refId = obj.optString("refId", "")
                if (refId.isBlank() || !seen.add(refId)) continue
                out += PlannedMission(
                    refId = refId,
                    name = obj.optString("name", obj.optString("title", "任务")),
                    score = obj.optInt("score", 0),
                    limit = obj.optInt("limit", 0),
                    platform = platform
                )
            }
        }

        addAll(mainMissions, TaskPlatform.MAIN)
        addAll(appMissions, TaskPlatform.APP)
        return out
    }

    fun buildWork(missions: List<PlannedMission>, doneCount: Map<String, Int>, maxItems: Int = 30): List<MissionWorkItem> {
        val out = mutableListOf<MissionWorkItem>()
        for (mission in missions) {
            if (shouldSkip(mission.name)) continue
            if (mission.refId.isBlank() || mission.score <= 0 || mission.limit <= 0) continue
            val remaining = (mission.limit - (doneCount[mission.refId] ?: 0)).coerceAtLeast(0)
            if (remaining <= 0) continue
            for (round in 1..remaining) {
                out += MissionWorkItem(
                    refId = mission.refId,
                    name = mission.name,
                    score = mission.score,
                    round = round,
                    total = remaining,
                    platform = mission.platform
                )
                if (out.size >= maxItems) return out
            }
        }
        return out
    }

    private fun shouldSkip(name: String): Boolean = listOf("免费权益", "借贷", "贷款").any { name.contains(it) }
}
