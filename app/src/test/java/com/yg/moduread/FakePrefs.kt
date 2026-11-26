package com.yg.moduread

import android.content.SharedPreferences

/**
 * SharedPreferences 的内存替身。
 *
 * 只实现本项目的存储层真正用到的四个类型（String / Int / Float / Long），
 * 其余 put/get 保持无操作，足以覆盖编解码、上限淘汰、偏好回落等纯逻辑。
 * 不引入 Robolectric —— 这些类没有任何 Android 运行时依赖。
 */
internal class FakePrefs : SharedPreferences {
    private val map = HashMap<String, Any?>()

    override fun getString(key: String?, defValue: String?): String? =
        if (key == null) defValue else (map[key] as? String) ?: defValue

    override fun getInt(key: String?, defValue: Int): Int =
        if (key == null) defValue else (map[key] as? Int) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        if (key == null) defValue else (map[key] as? Float) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        if (key == null) defValue else (map[key] as? Long) ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removals = HashSet<String>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            map.clear()
            return this
        }

        override fun apply() {
            removals.forEach { map.remove(it) }
            map.putAll(pending)
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun putStringSet(k: String?, v: MutableSet<String>?) = this
        override fun putBoolean(k: String?, v: Boolean) = this
    }

    override fun getAll(): MutableMap<String, *> = map
    override fun getStringSet(k: String?, d: MutableSet<String>?) = d
    override fun getBoolean(k: String?, d: Boolean) = d
    override fun contains(k: String?) = k != null && map.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
