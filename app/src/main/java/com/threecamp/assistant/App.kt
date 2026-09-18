package com.threecamp.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.threecamp.assistant.data.PrefStore
import com.threecamp.assistant.notify.NotifyHelper

/**
 * App 全局入口。
 * - 创建本地通知渠道（体力满 / 城建完成 / 抽卡冷却）
 * - 初始化 SharedPreferences 封装
 * 不做任何联网初始化（纯本地）。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        pref = PrefStore(this)
        createChannels()
    }

    /** 创建三个通知渠道，分别对应三类提醒 */
    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(NotifyHelper.CH_STAMINA, "体力提醒", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "体力恢复满值提醒" }
        )
        nm.createNotificationChannel(
            NotificationChannel(NotifyHelper.CH_CONSTRUCTION, "城建提醒", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "城建队列完成提醒" }
        )
        nm.createNotificationChannel(
            NotificationChannel(NotifyHelper.CH_RECRUITMENT, "抽卡提醒", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "免费/半价抽卡冷却结束提醒" }
        )
        nm.createNotificationChannel(
            NotificationChannel(NotifyHelper.CH_FOREGROUND, "监听服务", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "无障碍保活前台通知（低优先级）" }
        )
    }

    companion object {
        @JvmStatic
        lateinit var instance: App
            private set
        @JvmStatic
        lateinit var pref: PrefStore
            private set
    }
}
