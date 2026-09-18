package com.threecamp.assistant.engine

import com.threecamp.assistant.data.TeamStamina
import kotlin.math.max

/**
 * 体力本地推算引擎。
 *
 * 规则：
 * - 每 3 分钟恢复 1 点体力
 * - 上限 120
 * - 离线时间也累计恢复（基于 updatedAt 时间戳推算）
 * - 达到 120 触发满值通知
 *
 * 该引擎只做纯计算，不持有状态；状态由 PrefStore 持久化。
 */
object StaminaCalculator {

    const val MAX_STAMINA = 120
    const val MILLIS_PER_POINT = 3 * 60 * 1000L   // 3 分钟 1 点

    /**
     * 根据上次记录值与时间戳，推算当前体力。
     * @param team 队伍数据
     * @param now 当前时间戳
     * @return 推算后的体力值（不超过 120）
     */
    fun estimate(team: TeamStamina, now: Long = System.currentTimeMillis()): Int {
        if (team.stamina >= MAX_STAMINA) return MAX_STAMINA
        val elapsed = now - team.updatedAt
        if (elapsed <= 0) return team.stamina
        val gained = (elapsed / MILLIS_PER_POINT).toInt()
        return (team.stamina + gained).coerceAtMost(MAX_STAMINA)
    }

    /**
     * 用 OCR 新值更新队伍体力。
     * - 若 OCR 值 < 推算值（用户消耗了体力），重置 updatedAt 为 now
     * - 若 OCR 值 >= 推算值（恢复或一致），用 OCR 值 + 当前时间戳
     * - 同时清除满值通知标记（满值时再触发）
     */
    fun applyOcrValue(team: TeamStamina, ocrValue: Int, now: Long = System.currentTimeMillis()): TeamStamina {
        val estimated = estimate(team, now)
        val newStamina = ocrValue.coerceIn(0, MAX_STAMINA)
        val newUpdated = when {
            // 用户消耗体力 → 重置时间戳
            newStamina < estimated -> now
            // 恢复或一致 → 以 OCR 值为准，但保留时间戳精度
            else -> now
        }
        val notified = newStamina >= MAX_STAMINA && team.notifiedFull
        return team.copy(stamina = newStamina, updatedAt = newUpdated, notifiedFull = notified)
    }

    /** 计算距离下一点体力恢复的剩余毫秒 */
    fun msToNextPoint(team: TeamStamina, now: Long = System.currentTimeMillis()): Long {
        val current = estimate(team, now)
        if (current >= MAX_STAMINA) return 0L
        val elapsedSinceUpdate = now - team.updatedAt
        val remainInCurrent = elapsedSinceUpdate % MILLIS_PER_POINT
        return max(0L, MILLIS_PER_POINT - remainInCurrent)
    }

    /** 是否满值（用于触发通知） */
    fun isFull(team: TeamStamina, now: Long = System.currentTimeMillis()): Boolean =
        estimate(team, now) >= MAX_STAMINA
}
