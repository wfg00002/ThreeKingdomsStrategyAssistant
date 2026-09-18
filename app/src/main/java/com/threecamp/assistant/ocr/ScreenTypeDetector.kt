package com.threecamp.assistant.ocr

/**
 * 画面类型识别器 —— 解决"OCR 如何判断当前画面是主城/招募/其他"的核心问题。
 *
 * ============ 判定策略（硬性要求 7） ============
 * 1. 先在 OCR 全文本中"快速关键词匹配"，命中关键字才进入对应提取流程；
 *    未命中任何关键字 → 判定为"其他页面"，直接跳过 OCR 提取，降低耗电与误识别。
 *
 * 2. 主城 vs 招募 通过互斥关键字区分：
 *    - 主城页面：必须同时出现【体力】和【城建】（主城 UI 永远显示这两个入口）
 *      且不应出现【招募】顶栏关键字
 *    - 招募页面：必须出现【招募】且伴随【免费】或【半价】关键字
 *
 * 3. 二次校验：提取到的数值需满足合理性约束（体力 0..120、倒计时非负），
 *    不满足则视为误识别，本次不写入数据。
 *
 * 通过这种"关键字门控 + 互斥区分 + 数值合理性校验"三道关，
 * 避免跨页面误识别（例如主城 UI 上偶然出现"招募"字样也不会触发招募提取）。
 */
object ScreenTypeDetector {

    enum class ScreenType {
        UNKNOWN,        // 其他页面：跳过 OCR 提取
        MAIN_CITY,      // 主城页面：提取体力 + 城建
        RECRUITMENT     // 招募页面：提取免费/半价倒计时
    }

    /** 主城页面互斥关键字 */
    private const val KW_MAIN_1 = "体力"
    private const val KW_MAIN_2 = "城建"
    /** 招募页面关键字 */
    private const val KW_RECRUIT = "招募"
    private const val KW_FREE = "免费"
    private const val KW_HALF = "半价"

    /**
     * 根据整段 OCR 文本判定画面类型。
     * 命中多个时按优先级：招募页面优先（避免主城背景含"招募"按钮导致误判），
     * 但要求招募页必须同时出现免费/半价字样，进一步收紧。
     */
    fun detect(text: String): ScreenType {
        if (text.isBlank()) return ScreenType.UNKNOWN

        val hasRecruit = text.contains(KW_RECRUIT)
        val hasFree = text.contains(KW_FREE)
        val hasHalf = text.contains(KW_HALF)
        // 招募页：招募 + (免费 或 半价)
        if (hasRecruit && (hasFree || hasHalf)) return ScreenType.RECRUITMENT

        val hasStamina = text.contains(KW_MAIN_1)
        val hasCity = text.contains(KW_MAIN_2)
        // 主城页：体力 + 城建 同时出现
        if (hasStamina && hasCity) return ScreenType.MAIN_CITY

        return ScreenType.UNKNOWN
    }

    // ===== 关键字常量对外暴露，便于引擎模块复用 =====
    const val K_STAMINA = KW_MAIN_1
    const val K_CITY = KW_MAIN_2
    const val K_RECRUITMENT = KW_RECRUIT
    const val K_FREE = KW_FREE
    const val K_HALF = KW_HALF
}
