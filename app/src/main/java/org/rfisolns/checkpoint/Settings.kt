package org.rfisolns.checkpoint

import android.content.Context

// ponytail: plain SharedPreferences, no DataStore dependency needed for a handful of flat keys.
object Settings {
    private const val PREFS = "checkpoint_prefs"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_PAUSED = "paused"
    private const val KEY_DEVICE_LABEL = "device_label"
    private const val KEY_AUTO_START_ON_UNLOCK = "auto_start_on_unlock"
    private const val KEY_NEXT_PROMPT_EPOCH_MILLIS = "next_prompt_epoch_millis"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_MINUTES, 30)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL_MINUTES, minutes).apply()
    }

    fun isPaused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, paused: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAUSED, paused).apply()
        if (paused) clearNextPromptEpochMillis(context)
    }

    fun getDeviceLabel(context: Context): String =
        prefs(context).getString(KEY_DEVICE_LABEL, "pixel8a") ?: "pixel8a"

    fun setDeviceLabel(context: Context, label: String) {
        prefs(context).edit().putString(KEY_DEVICE_LABEL, label).apply()
    }

    fun isAutoStartOnUnlockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_START_ON_UNLOCK, true)

    fun setAutoStartOnUnlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_START_ON_UNLOCK, enabled).apply()
    }

    fun getNextPromptEpochMillis(context: Context): Long? {
        val value = prefs(context).getLong(KEY_NEXT_PROMPT_EPOCH_MILLIS, -1L)
        return if (value == -1L) null else value
    }

    fun setNextPromptEpochMillis(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_NEXT_PROMPT_EPOCH_MILLIS, epochMillis).apply()
    }

    fun clearNextPromptEpochMillis(context: Context) {
        prefs(context).edit().remove(KEY_NEXT_PROMPT_EPOCH_MILLIS).apply()
    }
}
