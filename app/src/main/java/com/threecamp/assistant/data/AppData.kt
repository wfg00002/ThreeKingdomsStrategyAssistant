package com.threecamp.assistant.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 单支队伍体力数据。
 * @param id     队伍唯一标识（1..N）
 * @param stamina 当前体力数值
 * @param updatedAt 上次确认该体力值的时间戳（ms）
 * @param notifiedFull 是否已发送过满值通知（避免重复）
 */
data class TeamStamina(
    val id: Int,
    val stamina: Int,
    val updatedAt: Long,
    val notifiedFull: Boolean = false
)

/**
 * 单条城建队列倒计时。
 * @param id 队列唯一 id
 * @param label 自定义名称（如"兵营"）
 * @param endTime 结束时间戳（ms）
 * @param notifiedDone 是否已发送过完成通知
 */
data class ConstructionQueue(
    val id: String,
    val label: String,
    val endTime: Long,
    val notifiedDone: Boolean = false
)

/**
 * 抽卡冷却数据。
 * @param freeEndMs       免费抽卡结束时间戳；0 表示无记录
 * @param halfPriceEndMs  半价抽卡结束时间戳；0 表示无记录
 * @param lastFreeText    OCR 原始识别文本（用于防抖比对）
 * @param lastHalfText    OCR 原始识别文本
 * @param freeNotified    免费冷却到点是否已通知
 * @param halfNotified    半价冷却到点是否已通知
 */
data class RecruitmentCooldown(
    val freeEndMs: Long = 0L,
    val halfPriceEndMs: Long = 0L,
    val lastFreeText: String = "",
    val lastHalfText: String = "",
    val freeNotified: Boolean = false,
    val halfNotified: Boolean = false
)
