package de.soliswidget.data

import android.content.Context
import de.soliswidget.model.SolisData
import org.json.JSONObject

class Cache(context: Context) {
    private val prefs = context.getSharedPreferences("solis_cache", Context.MODE_PRIVATE)

    fun save(data: SolisData) {
        prefs.edit()
            .putString("json", data.rawJson)
            .putLong("timestamp", data.timestampMillis)
            .apply()
    }

    fun load(): SolisData? {
        val raw = prefs.getString("json", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            SolisData(
                stationName = o.optString("stationName"),
                powerKw = o.optDouble("powerKw", 0.0),
                dayEnergyKwh = o.optDouble("dayEnergyKwh", 0.0),
                batteryPowerKw = o.optDouble("batteryPowerKw", 0.0),
                batteryPercent = o.optDouble("batteryPercent", 0.0),
                gridPowerKw = o.optDouble("gridPowerKw", 0.0),
                homeLoadKw = o.optDouble("homeLoadKw", 0.0),
                timestampMillis = o.optLong("timestampMillis", prefs.getLong("timestamp", 0)),
                rawJson = raw
            )
        }.getOrNull()
    }
}
