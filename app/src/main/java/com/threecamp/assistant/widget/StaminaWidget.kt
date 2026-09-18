package com.threecamp.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.threecamp.assistant.App
import com.threecamp.assistant.R
import com.threecamp.assistant.engine.ConstructionCountdownManager
import com.threecamp.assistant.engine.StaminaCalculator
import com.threecamp.assistant.ui.MainActivity

/**
 * 桌面原子组件 —— 体力 + 城建（4x2）。
 *
 * 展示：
 * - 多支队伍体力数值（基于本地推算）
 * - 多条城建队列剩余时间
 */
class StaminaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StaminaWidget::class.java))
            if (ids.isNotEmpty()) StaminaWidget().onUpdate(ctx, mgr, ids)
        }
    }

    private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val pref = App.pref
        val now = System.currentTimeMillis()
        val teams = pref.loadStamina()
        val queues = pref.loadConstruction()

        // 体力：每队一行
        val staminaText = buildString {
            teams.forEach { t ->
                val v = StaminaCalculator.estimate(t, now)
                append("第${t.id}队：$v/${StaminaCalculator.MAX_STAMINA}\n")
            }
        }.trimEnd()

        // 城建：每队列一行
        val constructionText = if (queues.isEmpty()) {
            "暂无城建队列"
        } else {
            buildString {
                queues.forEach { q ->
                    val remain = ConstructionCountdownManager.remainSeconds(q, now)
                    val status = if (remain <= 0) "已完成" else ConstructionCountdownManager.format(remain)
                    append("${q.label}：$status\n")
                }
            }.trimEnd()
        }

        val views = RemoteViews(ctx.packageName, R.layout.widget_stamina).apply {
            setTextViewText(R.id.tv_stamina, staminaText)
            setTextViewText(R.id.tv_construction, constructionText)
            val pi = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setOnClickPendingIntent(R.id.widget_root, pi)
        }
        mgr.updateAppWidget(id, views)
    }
}
