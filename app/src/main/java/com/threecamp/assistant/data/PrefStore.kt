package com.threecamp.assistant.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地数据持久化封装（SharedPreferences）。
 * 全部数据本地存储，无任何联网。
 */
class PrefStore(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ============ 游戏包名 ============
    var targetPackage: String
        get() = sp.getString(K_TARGET_PKG, DEFAULT_TARGET_PKG) ?: DEFAULT_TARGET_PKG
        set(v) { sp.edit().putString(K_TARGET_PKG, v).apply() }

    var teamCount: Int
        get() = sp.getInt(K_TEAM_COUNT, 3)
        set(v) { sp.edit().putInt(K_TEAM_COUNT, v).apply() }

    // ============ 体力数据 ============
    fun loadStamina(): MutableList<TeamStamina> {
        val raw = sp.getString(K_STAMINA, null) ?: return defaultStamina()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<TeamStamina>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += TeamStamina(
                    id = o.getInt("id"),
                    stamina = o.getInt("stamina"),
                    updatedAt = o.getLong("updatedAt"),
                    notifiedFull = o.optBoolean("notifiedFull", false)
                )
            }
            alignTeamCount(list)
        } catch (e: Exception) {
            defaultStamina()
        }
    }

    fun saveStamina(list: List<TeamStamina>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("stamina", t.stamina)
                put("updatedAt", t.updatedAt)
                put("notifiedFull", t.notifiedFull)
            })
        }
        sp.edit().putString(K_STAMINA, arr.toString()).apply()
    }

    private fun defaultStamina(): MutableList<TeamStamina> {
        val list = mutableListOf<TeamStamina>()
        for (i in 1..teamCount) list += TeamStamina(i, 0, System.currentTimeMillis())
        return list
    }

    private fun alignTeamCount(list: MutableList<TeamStamina>): MutableList<TeamStamina> {
        val have = list.map { it.id }.toMutableList()
        for (i in 1..teamCount) if (i !in have) list += TeamStamina(i, 0, System.currentTimeMillis())
        return list
    }

    // ============ 城建队列 ============
    fun loadConstruction(): MutableList<ConstructionQueue> {
        val raw = sp.getString(K_CONSTRUCTION, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ConstructionQueue>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += ConstructionQueue(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    endTime = o.getLong("endTime"),
                    notifiedDone = o.optBoolean("notifiedDone", false)
                )
            }
            list
        } catch (e: Exception) { mutableListOf() }
    }

    fun saveConstruction(list: List<ConstructionQueue>) {
        val arr = JSONArray()
        list.forEach { q ->
            arr.put(JSONObject().apply {
                put("id", q.id)
                put("label", q.label)
                put("endTime", q.endTime)
                put("notifiedDone", q.notifiedDone)
            })
        }
        sp.edit().putString(K_CONSTRUCTION, arr.toString()).apply()
    }

    // ============ 招募抽卡冷却 ============
    fun loadRecruitment(): RecruitmentCooldown {
        return RecruitmentCooldown(
            freeEndMs = sp.getLong(K_FREE_END, 0L),
            halfPriceEndMs = sp.getLong(K_HALF_END, 0L),
            lastFreeText = sp.getString(K_FREE_TEXT, "") ?: "",
            lastHalfText = sp.getString(K_HALF_TEXT, "") ?: "",
            freeNotified = sp.getBoolean(K_FREE_NOTIFIED, false),
            halfNotified = sp.getBoolean(K_HALF_NOTIFIED, false)
        )
    }

    fun saveRecruitment(r: RecruitmentCooldown) {
        sp.edit()
            .putLong(K_FREE_END, r.freeEndMs)
            .putLong(K_HALF_END, r.halfPriceEndMs)
            .putString(K_FREE_TEXT, r.lastFreeText)
            .putString(K_HALF_TEXT, r.lastHalfText)
            .putBoolean(K_FREE_NOTIFIED, r.freeNotified)
            .putBoolean(K_HALF_NOTIFIED, r.halfNotified)
            .apply()
    }

    companion object {
        private const val NAME = "three_camp_prefs"
        private const val K_TARGET_PKG = "target_pkg"
        private const val K_TEAM_COUNT = "team_count"
        private const val K_STAMINA = "stamina_list"
        private const val K_CONSTRUCTION = "construction_list"
        private const val K_FREE_END = "free_end"
        private const val K_HALF_END = "half_end"
        private const val K_FREE_TEXT = "free_text"
        private const val K_HALF_TEXT = "half_text"
        private const val K_FREE_NOTIFIED = "free_notified"
        private const val K_HALF_NOTIFIED = "half_notified"

        const val DEFAULT_TARGET_PKG = "com.tencent.tmgp.sgzcl"
    }
}
