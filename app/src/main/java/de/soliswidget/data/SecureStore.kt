package de.soliswidget.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "solis_secrets",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var keyId: String
        get() = prefs.getString("key_id", "") ?: ""
        set(value) = prefs.edit().putString("key_id", value).apply()

    var keySecret: String
        get() = prefs.getString("key_secret", "") ?: ""
        set(value) = prefs.edit().putString("key_secret", value).apply()

    var stationId: Long
        get() = prefs.getLong("station_id", -1L)
        set(value) = prefs.edit().putLong("station_id", value).apply()

    var stationName: String
        get() = prefs.getString("station_name", "") ?: ""
        set(value) = prefs.edit().putString("station_name", value).apply()

    var stationMoney: String
        get() = prefs.getString("station_money", "EUR") ?: "EUR"
        set(value) = prefs.edit().putString("station_money", value).apply()

    var stationTimeZone: Int
        get() = prefs.getInt("station_timezone", 0)
        set(value) = prefs.edit().putInt("station_timezone", value).apply()

    var stationPlantId: String
        get() = prefs.getString("station_plant_id", "") ?: ""
        set(value) = prefs.edit().putString("station_plant_id", value).apply()

    var stationNmiCode: String
        get() = prefs.getString("station_nmi_code", "") ?: ""
        set(value) = prefs.edit().putString("station_nmi_code", value).apply()
}
