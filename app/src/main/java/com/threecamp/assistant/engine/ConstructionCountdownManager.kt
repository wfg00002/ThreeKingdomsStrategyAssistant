package com.threecamp.assistant.engine

import com.threecamp.assistant.data.ConstructionQueue

/**
 * 城建多队列本地倒计时管理器。
 *
 * 规则：
 * - 每条队列存储结束时间戳 endTime
 * - 当前剩余 = endTime - now；<=0 表示已完成
 * - OCR 识别到新倒计时文本时，按"队列序号"刷新对应队列 endTime
 * - 队列完成发送本地通知
 *
 * 多队列管理：通过列表形式支持任意条数，OCR 提取到的多个倒计时按顺序匹配。
 */
object ConstructionCountdownManager {

    /** 用 OCR 提取的剩余秒数刷新队列 endTime */
    fun applySeconds(queue: ConstructionQueue, seconds: Long, now: Long = System.currentTimeMillis()): ConstructionQueue {
        val newEnd = now + seconds * 1000L
        // 防抖：若新旧 endTime 差距 < 30s，视为同一次倒计时刷新，不覆盖
        return if (kotlin.math.abs(newEnd - queue.endTime) < 30_000L && queue.endTime > now) {
            queue.copy()  // 不更新
        } else {
            queue.copy(endTime = newEnd, notifiedDone = false)
        }
    }

    /** 计算剩余秒数（<0 返回 0） */
    fun remainSeconds(queue: ConstructionQueue, now: Long = System.currentTimeMillis()): Long {
        val r = (queue.endTime - now) / 1000L
        return if (r < 0) 0L else r
    }

    /** 是否已完成 */
    fun isDone(queue: ConstructionQueue, now: Long = System.currentTimeMillis()): Boolean =
        now >= queue.endTime

    /** 格式化剩余时间为 hh:mm:ss */
    fun format(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, ss)
        else "%02d:%02d".format(m, ss)
    }
}
