package de.soliswidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import de.soliswidget.R
import de.soliswidget.data.Cache
import de.soliswidget.data.SolisRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class SolisWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SolisWidgetReceiver::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) updateOne(context, manager, id)
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.solis_widget_layout)
            val data = Cache(context).load()
            if (data == null) {
                views.setTextViewText(R.id.widget_title, "Solis PV")
                views.setTextViewText(R.id.widget_status, "App öffnen und Anlage auswählen")
                views.setTextViewText(R.id.widget_pv, "PV -- kW")
                views.setTextViewText(R.id.widget_home, "Haus -- kW")
                views.setTextViewText(R.id.widget_battery, "Batterie -- %")
                views.setTextViewText(R.id.widget_grid, "Netz --")
                views.setTextViewText(R.id.widget_supply, "Noch keine Daten")
                views.setTextViewText(R.id.widget_today, "Heute -- kWh")
            } else {
                views.setTextViewText(R.id.widget_title, "Solis · ${data.stationName}")
                views.setTextViewText(R.id.widget_status, "Daten aktuell")
                views.setTextViewText(R.id.widget_pv, "PV ${fmt(data.powerKw)} kW")
                views.setTextViewText(R.id.widget_home, "Haus ${fmt(data.homeLoadKw)} kW")
                views.setTextViewText(R.id.widget_battery, "Batterie ${fmt(data.batteryPercent, 0)} % · ${SolisRepository.batteryLabel(data.batteryPowerKw)}")
                views.setTextViewText(R.id.widget_grid, "Netz ${fmt(abs(data.gridPowerKw))} kW · ${SolisRepository.gridLabel(data.gridPowerKw)}")
                views.setTextViewText(R.id.widget_supply, SolisRepository.supplyLabel(data))
                views.setTextViewText(R.id.widget_today, "Heute ${fmt(data.dayEnergyKwh)} kWh · ${time(data.timestampMillis)}")
            }
            manager.updateAppWidget(id, views)
        }

        private fun fmt(value: Double, decimals: Int = 2): String = String.format(Locale.GERMANY, "%.${decimals}f", value)
        private fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(ms))
    }
}
