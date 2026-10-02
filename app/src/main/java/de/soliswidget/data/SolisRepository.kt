package de.soliswidget.data

import android.content.Context
import de.soliswidget.api.SolisApi
import de.soliswidget.model.BatteryState
import de.soliswidget.model.GridState
import de.soliswidget.model.SolisData
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SolisRepository {
    const val BATTERY_POSITIVE_MEANS_CHARGING = true
    const val GRID_POSITIVE_MEANS_IMPORT = true

    fun fetch(context: Context): SolisData {
        val store = SecureStore(context)
        require(store.keyId.isNotBlank()) { "KeyID fehlt." }
        require(store.keySecret.isNotBlank()) { "KeySecret fehlt." }
        require(store.stationId > 0) { "Keine Anlage ausgewählt." }

        val api = SolisApi(store.keyId, store.keySecret)

        // userStationList is the reliable base source: according to the SolisCloud
        // documentation it already contains real-time power and today's energy.
        val selected = api.listPlants().firstOrNull { it.id == store.stationId }
            ?: throw IllegalStateException("Anlage ${store.stationId} wurde in userStationList nicht gefunden.")

        store.stationName = selected.name
        store.stationMoney = selected.money
        store.stationTimeZone = selected.timeZone
        store.stationPlantId = selected.plantId
        store.stationNmiCode = selected.nmiCode

        val now = System.currentTimeMillis()
        val baseRaw = org.json.JSONObject()
            .put("source", "userStationList")
            .put("stationName", selected.name)
            .put("powerKw", selected.powerKw)
            .put("dayEnergyKwh", selected.dayEnergyKwh)
            .put("batteryPowerKw", 0.0)
            .put("batteryPercent", 0.0)
            .put("gridPowerKw", 0.0)
            .put("homeLoadKw", 0.0)
            .put("timestampMillis", now)
            .put("stationId", selected.id)
            .put("plantId", selected.plantId)
            .put("nmiCode", selected.nmiCode)
            .put("timeZone", selected.timeZone)
            .put("money", selected.money)
            .put("powerStr", selected.powerKw.toString())
            .put("dayEnergyStr", selected.dayEnergyKwh.toString())
            .put("plantRecord", org.json.JSONObject()
                .put("id", selected.id)
                .put("stationName", selected.name)
                .put("power", selected.powerKw)
                .put("dayEnergy", selected.dayEnergyKwh)
                .put("state", selected.state))

        // Save immediately. This guarantees that a temporary failure of stationDetail
        // or stationDay never leaves the widget with stale PV data.
        val base = SolisData(
            stationName = selected.name,
            powerKw = selected.powerKw,
            dayEnergyKwh = selected.dayEnergyKwh,
            batteryPowerKw = 0.0,
            batteryPercent = 0.0,
            gridPowerKw = 0.0,
            homeLoadKw = 0.0,
            timestampMillis = now,
            rawJson = baseRaw.toString()
        )
        Cache(context).save(base)

        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Prefer stationDetail because it contains the complete live plant values.
        val detail = runCatching {
            api.stationDetail(selected.id, selected.plantId, selected.nmiCode)
        }.getOrNull()

        if (detail != null) {
            val merged = detail.copy(
                stationName = selected.name,
                dayEnergyKwh = selected.dayEnergyKwh,
                timestampMillis = System.currentTimeMillis(),
                rawJson = detail.rawJson + "\nplantListFallback=available"
            )
            Cache(context).save(merged)
            return merged
        }

        // If stationDetail is unavailable for this plant/account, stationDay can still
        // provide the live values for the current day.
        val dayData = runCatching {
            api.stationDay(
                stationId = selected.id,
                plantId = selected.plantId,
                nmiCode = selected.nmiCode,
                money = selected.money,
                timeZone = selected.timeZone,
                day = day
            )
        }.getOrNull()

        if (dayData != null) {
            val merged = dayData.copy(
                stationName = selected.name,
                dayEnergyKwh = selected.dayEnergyKwh,
                timestampMillis = System.currentTimeMillis(),
                rawJson = dayData.rawJson + "\nplantListFallback=available"
            )
            Cache(context).save(merged)
            return merged
        }

        // Final live-data fallback: inverterDetail exposes battery, home load and grid
        // values independently of stationDetail/stationDay.
        val inverterData = runCatching {
            val inverter = api.inverterList(selected.id).firstOrNull()
            if (inverter != null) api.inverterDetail(inverter.optLong("id", -1L)) else null
        }.getOrNull()

        if (inverterData != null) {
            val merged = inverterData.copy(
                stationName = selected.name,
                powerKw = selected.powerKw,
                dayEnergyKwh = selected.dayEnergyKwh,
                timestampMillis = System.currentTimeMillis(),
                rawJson = org.json.JSONObject(inverterData.rawJson)
                    .put("plantPowerSource", "userStationList")
                    .put("plantDayEnergySource", "userStationList")
                    .toString()
            )
            Cache(context).save(merged)
            return merged
        }

        // Both detailed endpoints failed, but the official plant list still gave us
        // current PV power and today's energy. Return that valid data instead of failing.
        val finalRaw = baseRaw
            .put("detailAvailable", false)
            .put("stationDayAvailable", false)
            .put("note", "Live-PV und Tagesenergie stammen aus userStationList")
        val finalData = base.copy(rawJson = finalRaw.toString())
        Cache(context).save(finalData)
        return finalData
    }

    fun batteryState(power: Double): BatteryState {
        if (abs(power) < 0.03) return BatteryState.IDLE
        val charging = if (BATTERY_POSITIVE_MEANS_CHARGING) power > 0 else power < 0
        return if (charging) BatteryState.CHARGING else BatteryState.DISCHARGING
    }

    fun gridState(power: Double): GridState {
        if (abs(power) < 0.03) return GridState.IDLE
        val importing = if (GRID_POSITIVE_MEANS_IMPORT) power > 0 else power < 0
        return if (importing) GridState.IMPORTING else GridState.EXPORTING
    }

    fun batteryLabel(power: Double): String = when (batteryState(power)) {
        BatteryState.CHARGING -> "Batterie lädt"
        BatteryState.DISCHARGING -> "Batterie entlädt"
        BatteryState.IDLE -> "Batterie ruht"
    }

    fun gridLabel(power: Double): String = when (gridState(power)) {
        GridState.IMPORTING -> "Netzbezug"
        GridState.EXPORTING -> "Einspeisung"
        GridState.IDLE -> "Kein Netzfluss"
    }

    fun supplyLabel(data: SolisData): String {
        if (data.homeLoadKw < 0.03) return "Keine relevante Hauslast"
        val parts = mutableListOf<String>()
        if (data.powerKw > 0.03) parts += "Solar"
        if (batteryState(data.batteryPowerKw) == BatteryState.DISCHARGING) parts += "Batterie"
        if (gridState(data.gridPowerKw) == GridState.IMPORTING) parts += "Netz"
        return when {
            parts.isEmpty() -> "Versorgung nicht eindeutig"
            parts.size == 1 -> "Haus: ${parts[0]}"
            else -> "Haus: ${parts.joinToString(" + ")}"
        }
    }
}
