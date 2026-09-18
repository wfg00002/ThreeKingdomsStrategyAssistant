# 三战助手 · OriginOS 桌面原子组件 (MVP)

面向 iQOO OriginOS4 / OriginOS5 的三国志战略版辅助 App。
**核心原则：被动监听 + 本地离线 OCR，不联网、不抓包、不点击游戏控件。**

## 核心功能
- 通过 AccessibilityService 被动监听前台应用包名
- 用户手动打开三国志战略版 → 自动开启无障碍截图 + ML Kit 中文离线 OCR
- 仅当画面出现关键字时才触发对应提取逻辑（降耗 + 防误识别）
- 退出游戏 → 自动停止 OCR 监听
- 体力 / 城建 / 招募三大本地推算引擎 + 桌面原子组件
- 满值 / 完成 / 冷却结束本地通知
- 支持手动校正

## 模块说明
| 模块 | 关键字 | 提取内容 |
|------|--------|----------|
| 主城页面 | 体力 + 城建 | 多队伍体力数值、城建倒计时 |
| 招募页面 | 招募 + (免费\|半价) | 免费/半价抽卡倒计时 |

详见代码注释，特别是 [ScreenTypeDetector.kt](app/src/main/java/com/threecamp/assistant/ocr/ScreenTypeDetector.kt) 中的画面判定策略。

## 画面类型识别策略（硬性要求 7）
1. **关键字门控**：OCR 全文本未命中任何关键字 → 直接判 UNKNOWN，跳过提取（降耗）。
2. **互斥区分**：主城 = 体力+城建；招募 = 招募+(免费\|半价)。
3. **数值合理性校验**：体力 0..120、倒计时 10s..24h，不满足则丢弃。

## 兼容性
- minSdk 33 (Android 13) / targetSdk 34 (Android 14)
- ML Kit 中文 OCR（首次自动下载离线模型，之后断网可用）

## 构建
```bash
gradle assembleDebug
```
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## CI
push 到 main 自动构建并上传 APK：见 [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml)

## 手机端上传教程
见 [PHONE_UPLOAD_TUTORIAL.md](PHONE_UPLOAD_TUTORIAL.md)

## 安全与合规
- 仅被动监听前台包名 + 被动读取画面像素
- 不自动拉起游戏、不自动点击游戏控件
- OCR 完全本地，图片不上传网络
- 不抓包、不读取游戏网络数据、不修改游戏数据
- 全部数据本地 SharedPreferences 存储，无任何联网请求
