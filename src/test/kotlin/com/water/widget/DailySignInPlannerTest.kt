package com.water.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySignInPlannerTest {
    @Test
    fun `plans sign in when today's bit is clear`() {
        val plan = DailySignInPlanner.plan(weekMask = 0b0000010, weekDay = 1, adId = "DAILY_CHECK_IN", score = 5)

        assertTrue(plan.shouldSignIn)
        assertFalse(plan.alreadySigned)
        assertEquals(5, plan.baseScore)
        assertEquals(5, plan.totalScore)
    }

    @Test
    fun `skips sign in when today's bit is set`() {
        val plan = DailySignInPlanner.plan(weekMask = 1 shl 6, weekDay = 7, adId = "DAILY_CHECK_IN", score = 5)

        assertTrue(plan.alreadySigned)
        assertFalse(plan.shouldSignIn)
        assertEquals(0, plan.bonusScore)
    }

    @Test
    fun `skips sign in without ad id and normalizes negative score`() {
        val plan = DailySignInPlanner.plan(weekMask = 0, weekDay = 3, adId = " ", score = -1)

        assertFalse(plan.shouldSignIn)
        assertEquals(0, plan.baseScore)
    }

    @Test
    fun `adds reward when third day is first reached`() {
        val plan = DailySignInPlanner.plan(
            weekMask = 0b0000011,
            weekDay = 3,
            adId = "DAILY_CHECK_IN",
            score = 5,
            rules = listOf(DailySignInRule(weekMask = 0b0000111, score = 5, description = "连签3天"))
        )

        assertEquals(0b0000111, plan.newWeek)
        assertEquals(5, plan.bonusScore)
        assertEquals(10, plan.totalScore)
        assertEquals("连签3天", plan.rewards.single().description)
    }

    @Test
    fun `does not repeat reward already satisfied before sign in`() {
        val plan = DailySignInPlanner.plan(
            weekMask = 0b0001111,
            weekDay = 5,
            adId = "DAILY_CHECK_IN",
            score = 5,
            rules = listOf(DailySignInRule(weekMask = 0b0000111, score = 5, description = "连签3天"))
        )

        assertEquals(0, plan.bonusScore)
        assertTrue(plan.rewards.isEmpty())
        assertEquals(5, plan.totalScore)
    }

    @Test
    fun `adds multiple rewards reached by same sign in`() {
        val plan = DailySignInPlanner.plan(
            weekMask = 0b0000011,
            weekDay = 3,
            adId = "DAILY_CHECK_IN",
            score = 5,
            rules = listOf(
                DailySignInRule(weekMask = 0b0000111, score = 5, description = "连签3天"),
                DailySignInRule(weekMask = 0b0000101, score = 2, description = "周一周三")
            )
        )

        assertEquals(7, plan.bonusScore)
        assertEquals(12, plan.totalScore)
        assertEquals(2, plan.rewards.size)
    }

    @Test
    fun `ignores invalid and negative reward rules`() {
        val plan = DailySignInPlanner.plan(
            weekMask = 0,
            weekDay = 1,
            adId = "DAILY_CHECK_IN",
            score = 5,
            rules = listOf(
                DailySignInRule(weekMask = 0, score = 10, description = "空规则"),
                DailySignInRule(weekMask = 1 shl 7, score = 10, description = "越界规则"),
                DailySignInRule(weekMask = 1, score = -3, description = "负分规则")
            )
        )

        assertEquals(0, plan.bonusScore)
        assertTrue(plan.rewards.isEmpty())
        assertEquals(5, plan.totalScore)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid weekday`() {
        DailySignInPlanner.plan(weekMask = 0, weekDay = 0, adId = "DAILY_CHECK_IN", score = 5)
    }
}
