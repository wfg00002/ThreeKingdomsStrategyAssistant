package com.threecamp.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.threecamp.assistant.App
import com.threecamp.assistant.engine.DispatchEngine
import com.threecamp.assistant.notify.NotifyHelper
import com.threecamp.assistant.ocr.OcrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 无障碍服务 —— App 的"大脑"。
 *
 * 职责（硬性要求 1 / 3 / 4）：
 * 1. 监听 [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] 事件，获取当前前台应用包名
 *    - 判定目标游戏（三国志战略版）是否处于前台
 * 2. 当目标游戏进入前台 → 启动周期截图 + OCR 监听循环
 * 3. 当目标游戏退出前台 → 立即停止 OCR 监听循环，释放截图
 * 4. OriginOS 保活：服务常驻 + 前台低优先级通知
 *
 * 被动监听原则：
 * - 不主动启动游戏（不调用 startActivity 拉起游戏）
 * - 不主动点击游戏内任何控件（不调用 performAction 模拟点击）
 * - 仅通过 [takeScreenshot] 被动读取画面像素 → OCR 提取信息
 * - 不读取、不修改游戏网络数据包（OCR 仅处理位图像素）
 */
class ForegroundAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())

    /** 当前是否处于"目标游戏前台 + OCR 监听中"状态 */
    @Volatile private var monitoring = false
    private var ocrLoopJob: Job? = null
    private var tickJob: Job? = null

    /** 截图间隔（ms）。OCR 比较耗电，故间隔较长；可调 */
    private val ocrIntervalMs = 12_000L
    /** 本地推算巡检间隔（ms），不依赖 OCR，仅触发通知 */
    private val tickIntervalMs = 60_000L

    // ============ 生命周期 ============
    override fun onServiceConnected() {
        super.onServiceConnected()

        // Android 13+ 动态开启无障碍截图能力（xml 中已移除该属性，避免 CI AAPT 报错）
        val info = serviceInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.canTakeScreenshots = true
        }
        serviceInfo = info

        // 无障碍能力由 xml/accessibility_config.xml 配置，这里无需重复设置
        // OriginOS 保活：发送一条常驻低优先级通知，提升进程优先级
        showKeepAliveNotification()
        // 启动本地推算巡检（不依赖游戏是否前台，体力恢复需持续触发通知）
        startTickLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // 仅处理窗口状态变化事件 —— 用于检测前台包名切换
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        onForegroundPackageChanged(pkg)
    }

    override fun onInterrupt() { /* 期望被中断时，停止 OCR */ stopMonitoring() }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopMonitoring()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    // ============ 前台包名处理 ============
    /**
     * 前台包名变化回调。
     * - 目标游戏进入前台 → 启动 OCR 监听
     * - 目标游戏退出前台 → 停止 OCR 监听（硬性要求 3）
     */
    private fun onForegroundPackageChanged(pkg: String) {
        val target = App.pref.targetPackage
        if (pkg == target) {
            if (!monitoring) startMonitoring()
        } else {
            if (monitoring) stopMonitoring()
        }
    }

    // ============ OCR 监听循环 ============
    /**
     * 启动 OCR 监听循环：
     * - 周期性 takeScreenshot → OCR → DispatchEngine 路由
     * - 仅在目标游戏前台时运行，退出即停止
     */
    private fun startMonitoring() {
        if (monitoring) return
        monitoring = true
        ocrLoopJob?.cancel()
        ocrLoopJob = scope.launch {
            while (monitoring) {
                captureAndOcr()
                delay(ocrIntervalMs)
            }
        }
    }

    private fun stopMonitoring() {
        monitoring = false
        ocrLoopJob?.cancel()
        ocrLoopJob = null
    }

    /**
     * 截图 + OCR 单次执行。
     * AccessibilityService.takeScreenshot 自 API 30 起可用，正好覆盖 Android 13/14。
     */
    private suspend fun captureAndOcr() {
        // takeScreenshot 自 API 30 可用，项目 minSdk=33，无需运行期判断
        val bitmap = takeScreenshotSync() ?: return
        try {
            // OCR 在 IO 线程执行，避免阻塞主线程
            val text = withContext(Dispatchers.Default) { OcrEngine.recognize(bitmap) }
            // 路由到主调度引擎
            DispatchEngine.handleOcrText(this@ForegroundAccessibilityService, text)
        } catch (e: Exception) {
            // 单次识别失败不影响后续
        } finally {
            bitmap.recycle()
        }
    }

    /** 将 takeScreenshot 回调转为 suspend 等待 */
    private suspend fun takeScreenshotSync(): Bitmap? = withContext(Dispatchers.Main) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            takeScreenshot(
                MainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        // 硬件位图不能直接交给 ML Kit，复制为 ARGB_8888
                        val bmp = try {
                            result.hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        } catch (_: Throwable) { null }
                        cont.resume(bmp)
                    }
                    override fun onFailure(errorCode: Int) { cont.resume(null) }
                }
            )
        }
    }

    // ============ 本地推算巡检 ============
    /**
     * 每分钟巡检一次：
     * - 体力是否满值 → 通知
     * - 城建队列是否完成 → 通知
     * - 抽卡冷却是否结束 → 通知
     * 该循环独立于 OCR，确保用户即使不打开游戏也能收到冷却结束提醒。
     */
    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                DispatchEngine.tick(this@ForegroundAccessibilityService)
                delay(tickIntervalMs)
            }
        }
    }

    // ============ OriginOS 保活 ============
    /**
     * OriginOS 保活：发送一条常驻低优先级通知。
     *
     * 说明：AccessibilityService 本身由系统持续绑定（用户在无障碍设置开启后），
     * 是最强的保活手段。此处额外发送一条 ongoing 通知，
     * 一方面告知用户监听进行中，另一方面提升进程优先级，降低被 OriginOS
     * 后台清理回收的概率。
     *
     * 不调用 startForeground —— AccessibilityService 是系统绑定的特殊服务，
     * 调用 startForeground 在 Android 14 上需要额外声明 foregroundServiceType，
     * 且系统已保证服务存活，故 MVP 版本使用普通 ongoing 通知即可。
     */
    private fun showKeepAliveNotification() {
        try {
            val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
            nm.notify(
                NotifyHelper.ID_FOREGROUND,
                NotifyHelper.buildForegroundNotification(this)
            )
        } catch (_: Exception) {
            // 通知权限未授予等情况忽略，不影响监听
        }
    }

    /** 主线程 Executor，用于 takeScreenshot 回调 */
    private object MainExecutor : java.util.concurrent.Executor {
        private val h = Handler(Looper.getMainLooper())
        override fun execute(command: Runnable) { h.post(command) }
    }

    companion object {
        private const val TAG = "FgA11yService"
    }
}
