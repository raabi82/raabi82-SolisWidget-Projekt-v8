[README.md](https://github.com/user-attachments/files/32956622/README.md)
# raabi82-SolisWidget-Projekt-v8
raabi82/SolisWidget-Projekt-v8
# SolisWidget

Native Android app (Android 8 / API 26+) that reads live data from the SolisCloud Monitoring API and displays it in a Jetpack Glance home-screen widget.

## What it does

- Enter SolisCloud KeyID and KeySecret in the app.
- Load your plants via `userStationList`.
- Select a plant.
- Poll `stationDetail` in the background.
- Show PV power, home load, battery SOC/power, grid power and today's yield.
- Show a textual energy-flow state:
  - battery charging / discharging / idle
  - grid import / export / neutral
  - estimated current source of the home load
- Manual refresh from the app.
- Periodic background refresh via WorkManager.
- Stores API credentials using AndroidX Security Crypto.
- No credentials are hard-coded into the project.

## Important API note

The legacy/user-signature API uses:

`POST https://www.soliscloud.com:13333/v1/api/...`

The request signature is HMAC-SHA1 over:

`POST\nContent-MD5\nContent-Type\nDate\n/v1/api/<endpoint>`

The app uses the JSON request body exactly as sent for the Content-MD5 calculation.

SolisCloud documents `userStationList` and `stationDetail`; `stationDetail` contains `batteryPower`, `batteryPercent`, `psum`, `familyLoadPower`, `dayEnergy`, etc.

## Energy direction

Solis exposes signed values and also positive/negative split fields in its API. The app uses the signed values for a first-pass live interpretation and keeps the raw values visible in the diagnostics screen.

If your particular installation reports the opposite sign convention, change the two direction settings in `SolisRepository.kt`:

- `BATTERY_POSITIVE_MEANS_CHARGING`
- `GRID_POSITIVE_MEANS_IMPORT`

This is deliberately configurable because the exact direction convention can depend on the plant/meter configuration.

## Build without Android Studio

1. Create a GitHub repository.
2. Upload the contents of this folder.
3. Open **Actions**.
4. Run **Build APK**.
5. Download the `solis-widget-debug-apk` artifact.
6. Copy the APK to your Android phone and install it.
7. Open SolisWidget, enter KeyID + KeySecret, load plants, select your plant and add the widget.

The GitHub workflow builds a debug APK. For a Play Store/release build, add your own signing configuration.

## Security

Do not put your real KeySecret into GitHub, source code, README files or issue trackers. Enter it on the device only.


## v5

If `stationDetail` returns `code=0`, `msg=success`, but `data=null`, the app automatically falls back to `/v1/api/stationDay`. The selected plant's `money` and `timeZone` from `userStationList` are stored locally and supplied to `stationDay`.


## v6
- Verwendet die echte Plant-ID-Metadaten aus `userStationList`: numerische `id` sowie, falls vorhanden, `plantId` und `nmiCode`.
- Sendet diese Identifikatoren bei `stationDetail` und `stationDay`.
- Lädt die Tagesenergie zusätzlich über `stationDayEnergyList`.
- Diagnose zeigt Station-ID, Plant-ID, NMI, Zeitzone, Währung und Datenquelle.
- Das Widget greift für die Konfiguration nicht mehr direkt auf den verschlüsselten Speicher zu und zeigt bei fehlenden Daten eine harmlose Meldung statt eines Widget-Fehlers.

## v7
- userStationList is now the guaranteed base for current PV power and today's energy.
- stationDetail and stationDay are optional enrichment; their failure no longer prevents PV updates.
- Widget rendering uses plain text labels for better compatibility with older Android launchers.
