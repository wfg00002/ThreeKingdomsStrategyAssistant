package com.threecamp.assistant.engine

import android.content.Context
import com.threecamp.assistant.App
import com.threecamp.assistant.data.PrefStore
import com.threecamp.assistant.data.RecruitmentCooldown
import com.threecamp.assistant.data.TeamStamina
import com.threecamp.assistant.notify.NotifyHelper
import com.threecamp.assistant.ocr.OcrTextExtractor
import com.threecamp.assistant.ocr.ScreenTypeDetector
import com.threecamp.assistant.widget.RecruitmentWidget
import com.threecamp.assistant.widget.StaminaWidget

/**
 * 主调度引擎：将 OCR 识别文本路由到对应子引擎，并触发通知与桌面组件刷新。
 *
 * 三步流程：
 * 1. ScreenTypeDetector 判定画面类型；UNKNOWN 直接返回，不做任何写入（降耗 + 防误识别）
 * 2. 命中主城 → 体力/城建引擎处理；命中招募 → 招募引擎处理
 * 3. 触发满值/完成/冷却结束通知；刷新桌面原子组件
 *
 * 该对象无状态，所有数据来自 [PrefStore]，便于测试与重入。
 */
object DispatchEngine {

    /**
     * 处理一次 OCR 文本结果。
     * @return 处理后的画面类型，便于上层日志记录
     */
    fun handleOcrText(ctx: Context, text: String): ScreenTypeDetector.ScreenType {
        val type = ScreenTypeDetector.detect(text)
        when (type) {
            ScreenTypeDetector.ScreenType.MAIN_CITY -> handleMainCity(ctx, text)
            ScreenTypeDetector.ScreenType.RECRUITMENT -> handleRecruitment(ctx, text)
            ScreenTypeDetector.ScreenType.UNKNOWN -> {
                // 其他页面：跳过提取，不写入数据，降低耗电与误识别
            }
        }
        return type
    }

    /** 周期性巡检：基于本地推算触发通知（不依赖 OCR，每分钟调用一次） */
    fun tick(ctx: Context) {
        tickStamina(ctx)
        tickConstruction(ctx)
        tickRecruitment(ctx)
    }

    // ============ 主城 ============
    private fun handleMainCity(ctx: Context, text: String) {
        val pref = App.pref
        val teams = pref.loadStamina()
        val now = System.currentTimeMillis()

        // 1) 多队伍体力
        val ocrStaminas = OcrTextExtractor.extractStaminaList(text, teams.size)
        if (ocrStaminas.isNotEmpty()) {
            for (i in teams.indices) {
                if (i < ocrStaminas.size) {
                    teams[i] = StaminaCalculator.applyOcrValue(teams[i], ocrStaminas[i], now)
                }
            }
            pref.saveStamina(teams)
        }

        // 2) 城建队列（按 OCR 提取的多条倒计时刷新）
        val countdowns = OcrTextExtractor.extractConstructionCountdowns(text)
        if (countdowns.isNotEmpty()) {
            val queues = pref.loadConstruction()
            for (i in countdowns.indices) {
                val id = "q${i + 1}"
                val existed = queues.find { it.id == id }
                val updated = if (existed != null) {
                    ConstructionCountdownManager.applySeconds(existed, countdowns[i], now)
                } else {
                    com.threecamp.assistant.data.ConstructionQueue(
                        id = id,
                        label = "队列${i + 1}",
                        endTime = now + countdowns[i] * 1000L,
                        notifiedDone = false
                    )
                }
                if (existed != null) queues[queues.indexOf(existed)] = updated
                else queues += updated
            }
            pref.saveConstruction(queues)
        }

        StaminaWidget.updateAll(ctx)
    }

    private fun tickStamina(ctx: Context) {
        val pref = App.pref
        val teams = pref.loadStamina()
        val now = System.currentTimeMillis()
        var changed = false
        teams.forEachIndexed { i, t ->
            val full = StaminaCalculator.isFull(t, now)
            if (full && !t.notifiedFull) {
                NotifyHelper.notifyStaminaFull(ctx, t.id)
                teams[i] = t.copy(notifiedFull = true)
                changed = true
            } else if (!full && t.notifiedFull) {
                // 消耗体力后重置标记，下次满值可再次通知
                teams[i] = t.copy(notifiedFull = false)
                changed = true
            }
        }
        if (changed) pref.saveStamina(teams)
        StaminaWidget.updateAll(ctx)
    }

    private fun tickConstruction(ctx: Context) {
        val pref = App.pref
        val queues = pref.loadConstruction()
        val now = System.currentTimeMillis()
        var changed = false
        queues.forEachIndexed { i, q ->
            if (ConstructionCountdownManager.isDone(q, now) && !q.notifiedDone) {
                NotifyHelper.notifyConstructionDone(ctx, q.label)
                queues[i] = q.copy(notifiedDone = true)
                changed = true
            }
        }
        if (changed) pref.saveConstruction(queues)
        StaminaWidget.updateAll(ctx)
    }

    // ============ 招募 ============
    private fun handleRecruitment(ctx: Context, text: String) {
        val pref = App.pref
        val current = pref.loadRecruitment()
        val updated = RecruitmentCountdownManager.applyOcrText(current, text)
        pref.saveRecruitment(updated)
        RecruitmentWidget.updateAll(ctx)
    }

    private fun tickRecruitment(ctx: Context) {
        val pref = App.pref
        val r = pref.loadRecruitment()
        val now = System.currentTimeMillis()
        var changed: RecruitmentCooldown = r

        if (RecruitmentCountdownManager.isFreeReady(r, now) && !r.freeNotified) {
            NotifyHelper.notifyFreeReady(ctx)
            changed = changed.copy(freeNotified = true)
        }
        if (RecruitmentCountdownManager.isHalfReady(r, now) && !r.halfNotified) {
            NotifyHelper.notifyHalfReady(ctx)
            changed = changed.copy(halfNotified = true)
        }
        if (changed != r) pref.saveRecruitment(changed)
        RecruitmentWidget.updateAll(ctx)
    }
}
