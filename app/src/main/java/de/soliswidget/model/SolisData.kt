package de.soliswidget.model

data class Plant(
    val id: Long,
    val name: String,
    val powerKw: Double,
    val dayEnergyKwh: Double,
    val state: Int,
    val money: String = "EUR",
    val timeZone: Int = 0,
    val plantId: String = "",
    val nmiCode: String = ""
)

data class SolisData(
    val stationName: String,
    val powerKw: Double,
    val dayEnergyKwh: Double,
    val batteryPowerKw: Double,
    val batteryPercent: Double,
    val gridPowerKw: Double,
    val homeLoadKw: Double,
    val timestampMillis: Long,
    val rawJson: String
)

enum class BatteryState { CHARGING, DISCHARGING, IDLE }
enum class GridState { IMPORTING, EXPORTING, IDLE }
