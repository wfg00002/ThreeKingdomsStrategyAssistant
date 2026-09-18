package com.threecamp.assistant.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.threecamp.assistant.App
import com.threecamp.assistant.engine.ConstructionCountdownManager
import com.threecamp.assistant.engine.RecruitmentCountdownManager
import com.threecamp.assistant.engine.StaminaCalculator
import com.threecamp.assistant.widget.RecruitmentWidget
import com.threecamp.assistant.widget.StaminaWidget
import kotlinx.coroutines.delay

/**
 * 主界面 Activity —— MVP 版本。
 *
 * 功能：
 * 1. 引导用户开启无障碍服务（必须，否则无法监听前台应用）
 * 2. 引导用户授予通知权限（Android13+）
 * 3. 展示当前队伍体力 / 城建队列 / 抽卡冷却（基于本地推算，每秒刷新）
 * 4. 提供手动校正入口：体力数值、抽卡冷却时间（OCR 漏识别时使用）
 */
class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户选择结果忽略，UI 自行反映 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        ctx = this,
                        onRequestNotifPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onOpenBatteryOpt = { openBatteryOptimizationSettings() }
                    )
                }
            }
        }
    }

    /** 跳转到系统无障碍设置 */
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** 跳转到电池优化设置（OriginOS 保活） */
    private fun openBatteryOptimizationSettings() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    companion object {
        /** 判断无障碍服务是否已启用 */
        fun isAccessibilityEnabled(ctx: Context): Boolean {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            return enabled.any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
        }

        /** 判断通知权限是否已授予 */
        fun hasNotifPermission(ctx: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
    }
}

@Composable
private fun MainScreen(
    ctx: Context,
    onRequestNotifPermission: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    val pref = App.pref
    var refresh by remember { mutableStateOf(0) }

    // 每秒触发本地推算刷新
    LaunchedEffect(Unit) {
        while (true) {
            refresh++
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("三战助手 · MVP", style = MaterialTheme.typography.headlineMedium)

        // —— 权限引导区 ——
        val a11yOk = MainActivity.isAccessibilityEnabled(ctx)
        val notifOk = MainActivity.hasNotifPermission(ctx)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("权限与保活", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("无障碍服务：${if (a11yOk) "已开启" else "未开启"}", modifier = Modifier.weight(1f))
                    Button(onClick = onOpenAccessibility, enabled = !a11yOk) { Text("去开启") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("通知权限：${if (notifOk) "已授予" else "未授予"}", modifier = Modifier.weight(1f))
                    Button(onClick = onRequestNotifPermission, enabled = !notifOk) { Text("去授予") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目标包名：${pref.targetPackage}", modifier = Modifier.weight(1f))
                }
                Button(onClick = onOpenBatteryOpt) { Text("申请电池优化白名单（OriginOS 保活）") }
            }
        }

        // —— 体力区 ——
        val teams = remember(refresh) { pref.loadStamina() }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("队伍体力", style = MaterialTheme.typography.titleMedium)
                teams.forEachIndexed { i, t ->
                    val v = StaminaCalculator.estimate(t, System.currentTimeMillis())
                    var showEdit by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第${t.id}队：$v / ${StaminaCalculator.MAX_STAMINA}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "校正") }
                    }
                    if (showEdit) {
                        var input by remember { mutableStateOf(v.toString()) }
                        AlertDialog(
                            onDismissRequest = { showEdit = false },
                            title = { Text("手动校正第${t.id}队体力") },
                            text = {
                                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("体力 0-120") })
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val n = input.toIntOrNull()
                                    if (n != null && n in 0..120) {
                                        val list = pref.loadStamina()
                                        list[i] = t.copy(stamina = n, updatedAt = System.currentTimeMillis(), notifiedFull = n >= 120)
                                        pref.saveStamina(list)
                                        StaminaWidget.updateAll(ctx)
                                    }
                                    showEdit = false
                                }) { Text("保存") }
                            },
                            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("取消") } }
                        )
                    }
                }
            }
        }

        // —— 城建区 ——
        val queues = remember(refresh) { pref.loadConstruction() }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("城建队列", style = MaterialTheme.typography.titleMedium)
                if (queues.isEmpty()) Text("暂无队列（进入主城后会自动识别）")
                queues.forEach { q ->
                    val remain = ConstructionCountdownManager.remainSeconds(q, System.currentTimeMillis())
                    val status = if (remain <= 0) "已完成" else ConstructionCountdownManager.format(remain)
                    Text("${q.label}：$status")
                }
            }
        }

        // —— 抽卡冷却区 ——
        val recruit = remember(refresh) { pref.loadRecruitment() }
        val now = System.currentTimeMillis()
        val freeSec = RecruitmentCountdownManager.freeRemainSeconds(recruit, now)
        val halfSec = RecruitmentCountdownManager.halfRemainSeconds(recruit, now)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("抽卡冷却", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("免费：${RecruitmentCountdownManager.format(freeSec)}", modifier = Modifier.weight(1f))
                    var showEdit by remember { mutableStateOf(false) }
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "校正") }
                    if (showEdit) {
                        CountdownEditDialog(
                            title = "手动校正免费倒计时",
                            initial = freeSec.coerceAtLeast(0),
                            onConfirm = { secs ->
                                val updated = RecruitmentCountdownManager.applyManualFree(recruit, System.currentTimeMillis() + secs * 1000L)
                                pref.saveRecruitment(updated)
                                RecruitmentWidget.updateAll(ctx)
                                showEdit = false
                            },
                            onDismiss = { showEdit = false }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("半价：${RecruitmentCountdownManager.format(halfSec)}", modifier = Modifier.weight(1f))
                    var showEdit by remember { mutableStateOf(false) }
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "校正") }
                    if (showEdit) {
                        CountdownEditDialog(
                            title = "手动校正半价倒计时",
                            initial = halfSec.coerceAtLeast(0),
                            onConfirm = { secs ->
                                val updated = RecruitmentCountdownManager.applyManualHalf(recruit, System.currentTimeMillis() + secs * 1000L)
                                pref.saveRecruitment(updated)
                                RecruitmentWidget.updateAll(ctx)
                                showEdit = false
                            },
                            onDismiss = { showEdit = false }
                        )
                    }
                }
                Text("提示：进入招募页面会自动识别游戏原生倒计时", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("说明：本应用仅被动监听前台应用 + 离线 OCR，不联网、不抓包、不点击游戏控件。", style = MaterialTheme.typography.bodySmall)
    }
}

/** 倒计时手动编辑对话框：输入剩余秒数 */
@Composable
private fun CountdownEditDialog(
    title: String,
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("剩余秒数") })
                Text("支持时:分:秒格式，例如 3600 = 1 小时", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = input.toLongOrNull()
                if (n != null && n in 0..86400) onConfirm(n)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
