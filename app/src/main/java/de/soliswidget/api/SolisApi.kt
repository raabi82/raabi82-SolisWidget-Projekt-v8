package de.soliswidget.api

import android.util.Base64
import de.soliswidget.model.Plant
import de.soliswidget.model.SolisData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SolisApi(
    private val keyId: String,
    private val keySecret: String,
    private val baseUrl: String = "https://www.soliscloud.com:13333"
) {
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()

    private fun gmtDate(): String {
        val f = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        f.timeZone = TimeZone.getTimeZone("GMT")
        return f.format(Date())
    }

    private fun md5Base64(body: ByteArray): String =
        Base64.encodeToString(MessageDigest.getInstance("MD5").digest(body), Base64.NO_WRAP)

    private fun hmacSha1Base64(value: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keySecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun post(path: String, json: JSONObject): JSONObject {
        val bodyBytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        val contentMd5 = md5Base64(bodyBytes)
        val contentType = "application/json"
        val date = gmtDate()
        val signText = "POST\n$contentMd5\n$contentType\n$date\n$path"
        val sign = hmacSha1Base64(signText)

        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Content-Type", contentType)
            .header("Content-MD5", contentMd5)
            .header("Date", date)
            .header("Authorization", "API $keyId:$sign")
            .post(bodyBytes.toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $text")
            val result = JSONObject(text)
            if (!result.optBoolean("success", false) && result.optString("code") != "0") {
                throw IllegalStateException("Solis API ${result.optString("code")}: ${result.optString("msg")}")
            }
            return result
        }
    }

    fun listPlants(): List<Plant> {
        val root = post("/v1/api/userStationList", JSONObject().put("pageNo", 1).put("pageSize", 100))
        val data = root.optJSONObject("data") ?: return emptyList()
        val page = data.optJSONObject("page")
        val records = page?.optJSONArray("records") ?: data.optJSONArray("records") ?: JSONArray()
        return buildList {
            for (i in 0 until records.length()) {
                val o = records.getJSONObject(i)
                add(
                    Plant(
                        id = o.optLong("id"),
                        name = o.optString("stationName", "Solis-Anlage"),
                        powerKw = o.optDouble("power", 0.0),
                        dayEnergyKwh = o.optDouble("dayEnergy", 0.0),
                        state = o.optInt("state", 0),
                        money = o.optString("money", "EUR").ifBlank { "EUR" },
                        timeZone = o.optInt("timeZone", 0),
                        plantId = o.optString("plantId", ""),
                        nmiCode = o.optString("nmiCode", "")
                    )
                )
            }
        }
    }

    private fun identifierBody(stationId: Long, plantId: String, nmiCode: String): JSONObject {
        val body = JSONObject().put("id", stationId)
        if (plantId.isNotBlank()) body.put("plantId", plantId)
        if (nmiCode.isNotBlank()) body.put("nmiCode", nmiCode)
        return body
    }

    fun stationDetail(stationId: Long, plantId: String = "", nmiCode: String = ""): SolisData {
        val root = post("/v1/api/stationDetail", identifierBody(stationId, plantId, nmiCode))
        val dataValue = root.opt("data")
        val o = when (dataValue) {
            is JSONObject -> dataValue
            is JSONArray -> dataValue.optJSONObject(0)
            else -> null
        } ?: throw IllegalStateException(
            "Solis stationDetail: code=${root.optString("code")}, msg=${root.optString("msg")}, " +
                "dataType=${dataValue?.javaClass?.simpleName ?: "null"}, data=${dataValue ?: "null"}"
        )
        return parseDetailObject(o)
    }

    fun stationDay(
        stationId: Long,
        plantId: String,
        nmiCode: String,
        money: String,
        timeZone: Int,
        day: String
    ): SolisData {
        val body = identifierBody(stationId, plantId, nmiCode)
            .put("money", money.ifBlank { "EUR" })
            .put("time", day)
            .put("timeZone", timeZone)

        val root = post("/v1/api/stationDay", body)
        val dataValue = root.opt("data")
        val records = when (dataValue) {
            is JSONArray -> dataValue
            is JSONObject -> JSONArray().put(dataValue)
            else -> JSONArray()
        }
        if (records.length() == 0) {
            throw IllegalStateException(
                "Solis stationDay: code=${root.optString("code")}, msg=${root.optString("msg")}, " +
                    "dataType=${dataValue?.javaClass?.simpleName ?: "null"}, data=${dataValue ?: "null"}, " +
                    "request=${body}"
            )
        }

        var best = records.getJSONObject(0)
        for (i in 1 until records.length()) {
            val candidate = records.optJSONObject(i) ?: continue
            if (candidate.optString("time") > best.optString("time")) best = candidate
        }

        val timestamp = System.currentTimeMillis()
        val raw = JSONObject()
            .put("source", "stationDay")
            .put("stationName", best.optString("stationName", "Solis PV"))
            .put("powerKw", best.optDouble("power", 0.0))
            .put("dayEnergyKwh", 0.0)
            .put("batteryPowerKw", best.optDouble("batteryPower", 0.0))
            .put("batteryPercent", best.optDouble("batteryCapacitySoc", 0.0))
            .put("gridPowerKw", best.optDouble("psum", 0.0))
            .put("homeLoadKw", best.optDouble("familyLoadPower", 0.0))
            .put("timestampMillis", timestamp)
            .put("apiTime", best.optString("time"))
            .put("request", body)
            .put("recordCount", records.length())
            .put("rawRecord", best)

        return SolisData(
            stationName = best.optString("stationName", "Solis PV"),
            powerKw = kw(best.optDouble("power", 0.0)),
            dayEnergyKwh = 0.0,
            batteryPowerKw = kw(best.optDouble("batteryPower", 0.0)),
            batteryPercent = best.optDouble("batteryCapacitySoc", 0.0),
            gridPowerKw = kw(best.optDouble("psum", 0.0)),
            homeLoadKw = kw(best.optDouble("familyLoadPower", 0.0)),
            timestampMillis = timestamp,
            rawJson = raw.toString()
        )
    }

    fun stationDayEnergy(stationId: Long, day: String): Double? {
        val root = post(
            "/v1/api/stationDayEnergyList",
            JSONObject().put("time", day).put("stationIds", stationId.toString())
        )
        val data = root.optJSONObject("data") ?: return null
        val page = data.optJSONObject("page")
        val records = page?.optJSONArray("records") ?: data.optJSONArray("records") ?: JSONArray()
        for (i in 0 until records.length()) {
            val o = records.optJSONObject(i) ?: continue
            if (o.optLong("id", -1L) == stationId) return o.optDouble("energy", 0.0)
        }
        return null
    }

    private fun parseDetailObject(o: JSONObject): SolisData {
        val timestamp = System.currentTimeMillis()
        val raw = JSONObject()
            .put("source", "stationDetail")
            .put("stationName", o.optString("stationName", "Solis PV"))
            .put("powerKw", o.optDouble("power", 0.0))
            .put("dayEnergyKwh", o.optDouble("dayEnergy", 0.0))
            .put("batteryPowerKw", o.optDouble("batteryPower", 0.0))
            .put("batteryPercent", o.optDouble("batteryPercent", 0.0))
            .put("gridPowerKw", o.optDouble("psum", 0.0))
            .put("homeLoadKw", o.optDouble("familyLoadPower", 0.0))
            .put("timestampMillis", timestamp)
            .put("rawRecord", o)

        return SolisData(
            stationName = o.optString("stationName", "Solis PV"),
            powerKw = o.optDouble("power", 0.0),
            dayEnergyKwh = o.optDouble("dayEnergy", 0.0),
            batteryPowerKw = o.optDouble("batteryPower", 0.0),
            batteryPercent = o.optDouble("batteryPercent", 0.0),
            gridPowerKw = o.optDouble("psum", 0.0),
            homeLoadKw = o.optDouble("familyLoadPower", 0.0),
            timestampMillis = timestamp,
            rawJson = raw.toString()
        )
    }
    fun inverterList(stationId: Long): List<JSONObject> {
        val body = JSONObject().put("stationId", stationId).put("pageNo", 1).put("pageSize", 100)
        val root = post("/v1/api/inverterList", body)
        val data = root.optJSONObject("data") ?: return emptyList()
        val page = data.optJSONObject("page")
        val records = page?.optJSONArray("records") ?: data.optJSONArray("records") ?: JSONArray()
        return buildList {
            for (i in 0 until records.length()) records.optJSONObject(i)?.let { add(it) }
        }
    }

    fun inverterDetail(inverterId: Long): SolisData {
        val root = post("/v1/api/inverterDetail", JSONObject().put("id", inverterId))
        val o = root.optJSONObject("data") ?: throw IllegalStateException(
            "Solis inverterDetail: code=${root.optString("code")}, msg=${root.optString("msg")}, data=${root.opt("data") ?: "null"}"
        )
        val timestamp = System.currentTimeMillis()
        val raw = JSONObject()
            .put("source", "inverterDetail")
            .put("stationName", o.optString("stationName", "Solis PV"))
            .put("powerKw", kw(o.optDouble("pac", 0.0)))
            .put("dayEnergyKwh", o.optDouble("eToday", 0.0))
            .put("batteryPowerKw", kw(o.optDouble("batteryPower", 0.0)))
            .put("batteryPercent", o.optDouble("batteryCapacitySoc", 0.0))
            .put("gridPowerKw", kw(o.optDouble("pSum", o.optDouble("psum", 0.0))))
            .put("homeLoadKw", kw(o.optDouble("familyLoadPower", 0.0)))
            .put("timestampMillis", timestamp)
            .put("inverterId", inverterId)
            .put("rawRecord", o)
        return SolisData(
            stationName = o.optString("stationName", "Solis PV"),
            powerKw = kw(o.optDouble("pac", 0.0)),
            dayEnergyKwh = o.optDouble("eToday", 0.0),
            batteryPowerKw = kw(o.optDouble("batteryPower", 0.0)),
            batteryPercent = o.optDouble("batteryCapacitySoc", 0.0),
            gridPowerKw = kw(o.optDouble("pSum", o.optDouble("psum", 0.0))),
            homeLoadKw = kw(o.optDouble("familyLoadPower", 0.0)),
            timestampMillis = timestamp,
            rawJson = raw.toString()
        )
    }

    private fun kw(value: Double): Double = if (kotlin.math.abs(value) >= 100.0) value / 1000.0 else value

}
