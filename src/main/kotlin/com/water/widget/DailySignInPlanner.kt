package com.water.widget

data class DailySignInRule(
    val weekMask: Int,
    val score: Int,
    val description: String
)

data class DailySignInReward(
    val score: Int,
    val description: String
)

data class DailySignInPlan(
    val weekDay: Int,
    val adId: String,
    val alreadySigned: Boolean,
    val newWeek: Int,
    val baseScore: Int,
    val bonusScore: Int,
    val totalScore: Int,
    val rewards: List<DailySignInReward>
) {
    val shouldSignIn: Boolean
        get() = adId.isNotBlank() && !alreadySigned
}

object DailySignInPlanner {
    private const val VALID_WEEK_MASK = 0b1111111

    fun plan(
        weekMask: Int,
        weekDay: Int,
        adId: String?,
        score: Int,
        rules: List<DailySignInRule> = emptyList()
    ): DailySignInPlan {
        require(weekDay in 1..7)
        val normalizedWeek = weekMask and VALID_WEEK_MASK
        val todayBit = 1 shl (weekDay - 1)
        val alreadySigned = normalizedWeek and todayBit != 0
        val newWeek = normalizedWeek or todayBit
        val rewards = if (alreadySigned) {
            emptyList()
        } else {
            rules.mapNotNull { rule ->
                val ruleMask = rule.weekMask
                val validRule = ruleMask > 0 && ruleMask and VALID_WEEK_MASK == ruleMask && rule.score > 0
                val wasSatisfied = normalizedWeek and ruleMask == ruleMask
                val isSatisfied = newWeek and ruleMask == ruleMask
                if (validRule && !wasSatisfied && isSatisfied) {
                    DailySignInReward(rule.score, rule.description.trim())
                } else {
                    null
                }
            }
        }
        val baseScore = score.coerceAtLeast(0)
        val bonusScore = rewards.sumOf { it.score }
        return DailySignInPlan(
            weekDay = weekDay,
            adId = adId.orEmpty().trim(),
            alreadySigned = alreadySigned,
            newWeek = newWeek,
            baseScore = baseScore,
            bonusScore = bonusScore,
            totalScore = baseScore + bonusScore,
            rewards = rewards
        )
    }
}
