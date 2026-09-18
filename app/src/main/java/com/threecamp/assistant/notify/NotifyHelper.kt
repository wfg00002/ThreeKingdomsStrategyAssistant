package com.threecamp.assistant.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.threecamp.assistant.R
import com.threecamp.assistant.ui.MainActivity

/**
 * 本地通知工具：体力满 / 城建完成 / 抽卡冷却结束。
 * 全部本地通知，无任何推送服务。
 */
object NotifyHelper {

    const val CH_STAMINA = "ch_stamina"
    const val CH_CONSTRUCTION = "ch_construction"
    const val CH_RECRUITMENT = "ch_recruitment"
    const val CH_FOREGROUND = "ch_foreground"

    private const val ID_STAMINA_BASE = 1000
    private const val ID_CONSTRUCTION_BASE = 2000
    const val ID_FREE_READY = 3000
    const val ID_HALF_READY = 3001
    const val ID_FOREGROUND = 4000

    private fun mainPendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** 队伍体力满值通知 */
    fun notifyStaminaFull(ctx: Context, teamId: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n: Notification = NotificationCompat.Builder(ctx, CH_STAMINA)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("体力已满")
            .setContentText("第 $teamId 队体力恢复至 120，记得出征！")
            .setContentIntent(mainPendingIntent(ctx))
            .setAutoCancel(true)
            .build()
        nm.notify(ID_STAMINA_BASE + teamId, n)
    }

    /** 城建队列完成通知 */
    fun notifyConstructionDone(ctx: Context, label: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CH_CONSTRUCTION)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("城建完成")
            .setContentText("「$label」建造完成")
            .setContentIntent(mainPendingIntent(ctx))
            .setAutoCancel(true)
            .build()
        nm.notify(ID_CONSTRUCTION_BASE + label.hashCode(), n)
    }

    /** 免费抽卡冷却结束 */
    fun notifyFreeReady(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CH_RECRUITMENT)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("免费抽卡就绪")
            .setContentText("免费抽卡冷却已结束，可以抽卡啦！")
            .setContentIntent(mainPendingIntent(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(ID_FREE_READY, n)
    }

    /** 半价抽卡冷却结束 */
    fun notifyHalfReady(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CH_RECRUITMENT)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("半价抽卡就绪")
            .setContentText("半价抽卡冷却已结束，可以抽卡啦！")
            .setContentIntent(mainPendingIntent(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(ID_HALF_READY, n)
    }

    /** 无障碍保活前台通知（低优先级，常驻） */
    fun buildForegroundNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_FOREGROUND)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("三战助手运行中")
            .setContentText("正在被动监听前台应用（无障碍保活）")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent(ctx))
            .build()
}
