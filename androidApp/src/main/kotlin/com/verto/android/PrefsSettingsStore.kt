package com.verto.android

import android.content.Context
import com.verto.data.settings.SettingsStore
import com.verto.domain.VertoSettings
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.days

/** Only the values Ajustes edits are stored; the rest keep their defaults. Included in backups on purpose. */
class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("verto_settings", Context.MODE_PRIVATE)

    override fun load(): VertoSettings? {
        if (!prefs.contains(DAY_STARTS_AT)) return null
        val defaults = VertoSettings()
        return try {
            defaults.copy(
                dayStartsAt = LocalTime.parse(prefs.getString(DAY_STARTS_AT, null) ?: return null),
                absenceThreshold = prefs.getInt(ABSENCE_DAYS, defaults.absenceThreshold.inWholeDays.toInt()).days,
                quietStart = LocalTime.parse(prefs.getString(QUIET_START, null) ?: defaults.quietStart.toString()),
                quietEnd = LocalTime.parse(prefs.getString(QUIET_END, null) ?: defaults.quietEnd.toString()),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun save(settings: VertoSettings) {
        prefs.edit()
            .putString(DAY_STARTS_AT, settings.dayStartsAt.toString())
            .putInt(ABSENCE_DAYS, settings.absenceThreshold.inWholeDays.toInt())
            .putString(QUIET_START, settings.quietStart.toString())
            .putString(QUIET_END, settings.quietEnd.toString())
            .apply()
    }

    private companion object {
        const val DAY_STARTS_AT = "day_starts_at"
        const val ABSENCE_DAYS = "absence_days"
        const val QUIET_START = "quiet_start"
        const val QUIET_END = "quiet_end"
    }
}
