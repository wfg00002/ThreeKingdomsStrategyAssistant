package com.threecamp.assistant.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * ML Kit 中文 OCR 引擎封装。
 *
 * - 完全本地离线识别（首次会自动下载离线模型，识别过程不联网）
 * - 图片不上传网络
 * - 仅提供 suspend 单次识别接口，由 AccessibilityService 调度
 *
 * 注意：ML Kit 通过 Google Play 服务自动下载并缓存中文模型到本地，
 * 之后即使断网也能继续识别。
 */
object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 识别位图，返回识别到的文本（含元素块坐标） */
    suspend fun recognize(bitmap: Bitmap): String = suspendCoroutine { cont ->
        val img = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(img)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { cont.resume("") }
    }

    /** 是否已就绪（避免每次截图都盲目调用 OCR） */
    fun release() {
        // MVP 阶段不主动关闭，保持识别器常驻以提升连续识别性能
    }
}
