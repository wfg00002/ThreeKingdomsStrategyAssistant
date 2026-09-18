package com.threecamp.assistant.ocr

/**
 * OCR 文本提取器：从识别到的整段文本中抽取所需数值。
 *
 * 提取规则说明（硬性要求 7 —— 避免误识别）：
 * - 体力数值：紧邻【体力】关键字后方的数字（最多 3 位），范围 0..120
 * - 城建倒计时：在【城建】关键字附近查找 hh:mm:ss / mm:ss / "剩余X" 文本
 * - 招募倒计时：紧邻【免费】【半价】关键字的 hh:mm:ss / 时间文本
 *
 * 所有提取结果均做合理性校验，不通过则返回 null，由上层丢弃本次结果。
 */
object OcrTextExtractor {

    /**
     * 提取体力数值。
     * 三战主城 UI 中体力通常显示为"体力 87/120"或"体力 87"，
     * 取斜杠前的数字。
     */
    fun extractStamina(text: String): Int? {
        val idx = text.indexOf("体力")
        if (idx < 0) return null
        // 在关键字后 20 字符内寻找首个 1~3 位数字
        val sub = text.substring(idx, (idx + 20).coerceAtMost(text.length))
        val m = Regex("(\\d{1,3})").find(sub) ?: return null
        val v = m.groupValues[1].toIntOrNull() ?: return null
        return if (v in 0..120) v else null
    }

    /**
     * 提取多支队伍体力（三战主城队伍栏并排显示）。
     * 通过查找多个"体力"关键字位置依次提取，最多取 [maxTeams] 支。
     */
    fun extractStaminaList(text: String, maxTeams: Int): List<Int> {
        val result = mutableListOf<Int>()
        var from = 0
        while (result.size < maxTeams) {
            val idx = text.indexOf("体力", from)
            if (idx < 0) break
            val sub = text.substring(idx, (idx + 20).coerceAtMost(text.length))
            val m = Regex("(\\d{1,3})").find(sub)
            val v = m?.groupValues?.get(1)?.toIntOrNull()
            if (v != null && v in 0..120) result += v
            from = idx + 2
        }
        return result
    }

    /**
     * 提取城建队列倒计时（秒数）。
     * 三战城建 UI 显示形如 "01:23:45" 或 "23:45" 或 "剩余 1:23:45"。
     * 返回所有匹配到的倒计时（单位：秒）。
     */
    fun extractConstructionCountdowns(text: String): List<Long> {
        val results = mutableListOf<Long>()
        // 支持 1~2 位:1~2位:1~2位 (hh:mm:ss) 与 mm:ss
        val regex = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
        regex.findAll(text).forEach { m ->
            val h = m.groupValues[1].toLongOrNull() ?: 0L
            val min = m.groupValues[2].toLongOrNull() ?: 0L
            val s = m.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
            val total = h * 3600 + min * 60 + s
            // 仅保留 10s ~ 24h 之间的合理城建倒计时
            if (total in 10..86400) results += total
        }
        return results
    }

    /**
     * 提取招募免费倒计时文本（原文，用于防抖比对）。
     * 在【免费】关键字附近查找时间格式。
     */
    fun extractFreeText(text: String): String? {
        return findTimeNear(text, "免费")
    }

    /** 提取招募半价倒计时文本 */
    fun extractHalfPriceText(text: String): String? {
        return findTimeNear(text, "半价")
    }

    /** 将 "hh:mm:ss" / "mm:ss" 文本转换为剩余秒数 */
    fun parseTimeToSeconds(t: String): Long {
        val parts = t.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun findTimeNear(text: String, keyword: String): String? {
        val idx = text.indexOf(keyword)
        if (idx < 0) return null
        // 在关键字后 15 字符内查找时间
        val sub = text.substring(idx, (idx + 15).coerceAtMost(text.length))
        val m = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?)").find(sub) ?: return null
        return m.groupValues[1]
    }
}
