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
import com.threecamp.assistant.engine.RecruitmentCountdownManager
import com.threecamp.assistant.ui.MainActivity

/**
 * 桌面原子组件 —— 招募抽卡倒计时（4x2）。
 *
 * 展示：
 * - 免费抽卡剩余冷却时间
 * - 半价抽卡剩余冷却时间
 * 数据来源：PrefStore 持久化的 endTime，本地每秒推算（组件刷新由系统 + 自触发）
 */
class RecruitmentWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    /** 由外部（如 DispatchEngine）调用，刷新所有实例 */
    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RecruitmentWidget::class.java))
            if (ids.isNotEmpty()) RecruitmentWidget().onUpdate(ctx, mgr, ids)
        }
    }

    private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val r = App.pref.loadRecruitment()
        val now = System.currentTimeMillis()
        val freeSec = RecruitmentCountdownManager.freeRemainSeconds(r, now)
        val halfSec = RecruitmentCountdownManager.halfRemainSeconds(r, now)

        val views = RemoteViews(ctx.packageName, R.layout.widget_recruitment).apply {
            setTextViewText(R.id.tv_free, "免费：" + RecruitmentCountdownManager.format(freeSec))
            setTextViewText(R.id.tv_half, "半价：" + RecruitmentCountdownManager.format(halfSec))
            // 点击组件打开主界面（提供手动修改入口）
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
