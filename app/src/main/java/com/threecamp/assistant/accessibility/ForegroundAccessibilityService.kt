package com.threecamp.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
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
 */
class ForegroundAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var monitoring = false
    private var ocrLoopJob: Job? = null
    private var tickJob: Job? = null

    private val ocrIntervalMs = 12_000L
    private val tickIntervalMs = 60_000L

    // ============ 生命周期 ============
    override fun onServiceConnected() {
        super.onServiceConnected()
        showKeepAliveNotification()
        startTickLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        onForegroundPackageChanged(pkg)
    }

    override fun onInterrupt() { stopMonitoring() }

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
    private fun onForegroundPackageChanged(pkg: String) {
        val target = App.pref.targetPackage
        if (pkg == target) {
            if (!monitoring) startMonitoring()
        } else {
            if (monitoring) stopMonitoring()
        }
    }

    // ============ OCR 监听循环 ============
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

    private suspend fun captureAndOcr() {
        val bitmap = takeScreenshotSync() ?: return
        try {
            val text = withContext(Dispatchers.Default) { OcrEngine.recognize(bitmap) }
            DispatchEngine.handleOcrText(this@ForegroundAccessibilityService, text)
        } catch (e: Exception) {
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun takeScreenshotSync(): Bitmap? = withContext(Dispatchers.Main) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            takeScreenshot(
                0,
                MainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
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
    private fun showKeepAliveNotification() {
        try {
            val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
            nm.notify(
                NotifyHelper.ID_FOREGROUND,
                NotifyHelper.buildForegroundNotification(this)
            )
        } catch (_: Exception) {
        }
    }

    private object MainExecutor : java.util.concurrent.Executor {
        private val h = Handler(Looper.getMainLooper())
        override fun execute(command: Runnable) { h.post(command) }
    }

    companion object {
        private const val TAG = "FgA11yService"
    }
}
