package com.threecamp.assistant.engine

import com.threecamp.assistant.data.RecruitmentCooldown
import com.threecamp.assistant.ocr.OcrTextExtractor

/**
 * 招募抽卡冷却本地倒计时管理器。
 *
 * 规则：
 * - OCR 识别到【免费】【半价】附近的时间文本 → 解析为剩余秒数 → 计算 endTime = now + seconds
 * - 保存 endTime 后本地持续递减（无需持续 OCR）
 * - 防抖：与上次 OCR 文本相同则不刷新覆盖（避免同一段倒计时被频繁覆盖）
 * - 冷却结束发送通知
 *
 * 支持手动修改：[applyManualEnd] 由 UI 调用直接设置 endTime。
 */
object RecruitmentCountdownManager {

    /** 处理 OCR 文本，返回更新后的 RecruitmentCooldown（若未命中或防抖则原样返回） */
    fun applyOcrText(current: RecruitmentCooldown, ocrText: String, now: Long = System.currentTimeMillis()): RecruitmentCooldown {
        var result = current

        // 免费抽卡
        val freeText = OcrTextExtractor.extractFreeText(ocrText)
        if (freeText != null && freeText != current.lastFreeText) {
            // 防抖：与上次文本不同才刷新
            val secs = OcrTextExtractor.parseTimeToSeconds(freeText)
            if (secs in 1..86400) {
                result = result.copy(
                    freeEndMs = now + secs * 1000L,
                    lastFreeText = freeText,
                    freeNotified = false
                )
            }
        }

        // 半价抽卡
        val halfText = OcrTextExtractor.extractHalfPriceText(ocrText)
        if (halfText != null && halfText != current.lastHalfText) {
            val secs = OcrTextExtractor.parseTimeToSeconds(halfText)
            if (secs in 1..86400) {
                result = result.copy(
                    halfPriceEndMs = now + secs * 1000L,
                    lastHalfText = halfText,
                    halfNotified = false
                )
            }
        }
        return result
    }

    /** 手动设置免费抽卡结束时间（OCR 漏识别时由 UI 调用） */
    fun applyManualFree(current: RecruitmentCooldown, endMs: Long): RecruitmentCooldown =
        current.copy(freeEndMs = endMs, freeNotified = false, lastFreeText = "manual")

    /** 手动设置半价抽卡结束时间 */
    fun applyManualHalf(current: RecruitmentCooldown, endMs: Long): RecruitmentCooldown =
        current.copy(halfPriceEndMs = endMs, halfNotified = false, lastHalfText = "manual")

    fun freeRemainSeconds(r: RecruitmentCooldown, now: Long = System.currentTimeMillis()): Long {
        if (r.freeEndMs <= 0) return -1L
        val s = (r.freeEndMs - now) / 1000L
        return s.coerceAtLeast(0L)
    }

    fun halfRemainSeconds(r: RecruitmentCooldown, now: Long = System.currentTimeMillis()): Long {
        if (r.halfPriceEndMs <= 0) return -1L
        val s = (r.halfPriceEndMs - now) / 1000L
        return s.coerceAtLeast(0L)
    }

    fun isFreeReady(r: RecruitmentCooldown, now: Long = System.currentTimeMillis()): Boolean =
        r.freeEndMs in 1..now

    fun isHalfReady(r: RecruitmentCooldown, now: Long = System.currentTimeMillis()): Boolean =
        r.halfPriceEndMs in 1..now

    fun format(seconds: Long): String {
        if (seconds < 0) return "未记录"
        if (seconds == 0L) return "可抽取"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
