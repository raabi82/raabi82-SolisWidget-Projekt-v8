package de.soliswidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.*
import de.soliswidget.api.SolisApi
import de.soliswidget.data.Cache
import de.soliswidget.data.SecureStore
import de.soliswidget.data.SolisRepository
import de.soliswidget.model.Plant
import de.soliswidget.worker.SolisUpdateWorker
import de.soliswidget.widget.SolisWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SolisApp() }
        scheduleUpdates()
    }

    private fun scheduleUpdates() {
        val request = PeriodicWorkRequestBuilder<SolisUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "solis_periodic_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    @Composable
    private fun SolisApp() {
        val context = this
        val store = remember { SecureStore(context) }
        val scope = rememberCoroutineScope()

        var keyId by remember { mutableStateOf(store.keyId) }
        var keySecret by remember { mutableStateOf(store.keySecret) }
        var plants by remember { mutableStateOf<List<Plant>>(emptyList()) }
        var selectedId by remember { mutableLongStateOf(store.stationId) }
        var status by remember { mutableStateOf("Bereit") }
        var loading by remember { mutableStateOf(false) }

        MaterialTheme {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("SolisWidget", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "SolisCloud-Daten als Android-Widget",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = keyId,
                    onValueChange = { keyId = it },
                    label = { Text("KeyID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = keySecret,
                    onValueChange = { keySecret = it },
                    label = { Text("KeySecret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            status = "Anlagen werden geladen …"
                            try {
                                store.keyId = keyId.trim()
                                store.keySecret = keySecret
                                plants = withContext(Dispatchers.IO) {
                                    SolisApi(store.keyId, store.keySecret).listPlants()
                                }
                                status = "${plants.size} Anlage(n) gefunden"
                            } catch (e: Exception) {
                                status = "Fehler: ${e.message ?: "unbekannt"}"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading && keyId.isNotBlank() && keySecret.isNotBlank()
                ) {
                    Text("Anlagen laden")
                }

                Spacer(Modifier.height(12.dp))
                Text(status)

                if (plants.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Anlage auswählen", style = MaterialTheme.typography.titleMedium)

                    plants.forEach { plant ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            RadioButton(
                                selected = selectedId == plant.id,
                                onClick = {
                                    selectedId = plant.id
                                    store.stationId = plant.id
                                    store.stationName = plant.name
                                    store.stationMoney = plant.money
                                    store.stationTimeZone = plant.timeZone
                                    store.stationPlantId = plant.plantId
                                    store.stationNmiCode = plant.nmiCode
                                    scope.launch {
                                        loading = true
                                        status = "Aktuelle Daten werden geladen …"
                                        try {
                                            withContext(Dispatchers.IO) {
                                                SolisRepository.fetch(context)
                                            }
                                            SolisWidgetReceiver.updateAll(context)
                                            status = "Anlage gespeichert und Widget aktualisiert"
                                        } catch (e: Exception) {
                                            status = "Fehler: ${e.message ?: "unbekannt"}"
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            )
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(plant.name)
                                Text(
                                    "${plant.powerKw} kW · ${plant.dayEnergyKwh} kWh heute",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            status = "Aktualisiere …"
                            try {
                                withContext(Dispatchers.IO) {
                                    SolisRepository.fetch(context)
                                }
                                SolisWidgetReceiver.updateAll(context)
                                status = "Widget aktualisiert"
                            } catch (e: Exception) {
                                status = "Fehler: ${e.message ?: "unbekannt"}"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading && selectedId > 0
                ) {
                    Text("Jetzt aktualisieren")
                }

                Spacer(Modifier.height(24.dp))

                Cache(context).load()?.let { data ->
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("Diagnose", style = MaterialTheme.typography.titleMedium)
                    Text("PV: ${data.powerKw} kW")
                    Text("Hauslast: ${data.homeLoadKw} kW")
                    Text("Batterie: ${data.batteryPowerKw} kW / ${data.batteryPercent} %")
                    Text("Netz: ${data.gridPowerKw} kW")
                    Text("Batterie: ${SolisRepository.batteryLabel(data.batteryPowerKw)}")
                    Text("Netz: ${SolisRepository.gridLabel(data.gridPowerKw)}")
                    Text("Versorgung: ${SolisRepository.supplyLabel(data)}")
                    Text("Anlagen-ID (id): ${store.stationId}")
                    Text("Plant-ID: ${store.stationPlantId.ifBlank { "von userStationList nicht geliefert" }}")
                    Text("NMI: ${store.stationNmiCode.ifBlank { "nicht vorhanden / nur Australien" }}")
                    Text("Zeitzone: ${store.stationTimeZone} · Währung: ${store.stationMoney}")
                    Text("Quelle: ${runCatching { org.json.JSONObject(data.rawJson).optString("source") }.getOrDefault("-")}")
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Die Richtung von Batterie- und Netzleistung kann je nach Anlage/Meter-Konfiguration unterschiedlich sein. Die Rohwerte sind deshalb in der Diagnose sichtbar.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
