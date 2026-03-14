# Tuya TS0601 4-in-1 Soil Sensor — Hubitat Driver

A Hubitat Elevation driver for the Tuya TS0601 4-in-1 soil sensor (`_TZE284_0ints6wl`). Communicates over Zigbee using the Tuya EF00 private cluster.

---

## Supported Device

| Model | Manufacturer Code |
|-------|-------------------|
| TS0601 | `_TZE284_0ints6wl` |

The device measures four things simultaneously:
- Soil moisture (when probe is inserted into soil)
- Air temperature
- Air humidity
- Illuminance (lux)

---

## Installation

1. In Hubitat, go to **Drivers Code → New Driver**
2. Paste the contents of `Tuya_TS0601_4in1_SoilSensor.groovy` and click **Save**
3. Pair your sensor to Hubitat as usual — it should auto-match via the fingerprint
4. If it doesn't auto-match, go to the device page and manually select **Tuya TS0601 4-in-1 Soil Sensor** from the driver dropdown
5. Click **Configure** to initialise the device and push the default sample interval

---

## Attributes

| Attribute | Type | Unit | Description |
|-----------|------|------|-------------|
| `soilMoisture` | number | % | Moisture level at the probe tip |
| `temperature` | number | °C / °F | Air temperature |
| `humidity` | number | % | Air humidity |
| `illuminance` | number | lux | Light level |
| `battery` | number | % | Approximate battery percentage |
| `batteryState` | string | — | `low` / `medium` / `high` |
| `soilWarning` | string | — | Medium detected at probe: `normal` / `soil` / `water` |
| `waterWarning` | string | — | Water detection flag: `normal` / `water` |
| `soilSampleInterval` | number | h | Current sample interval reported by device |
| `soilCalibration` | number | — | Soil calibration offset (device-reported) |
| `humidityCalibration` | number | — | Humidity calibration offset (device-reported) |
| `luxCalibration` | number | — | Lux calibration offset (device-reported) |
| `tempCalibration` | number | — | Temperature calibration offset (device-reported) |

---

## Preferences

| Setting | Description | Default |
|---------|-------------|---------|
| Air Temperature Offset | Added to raw temperature reading | 0.0 |
| Air Humidity Offset | Added to raw humidity reading | 0.0 |
| Soil Moisture Offset | Added to raw moisture reading | 0.0 |
| Temperature Unit | `Celsius` or `Fahrenheit` | Celsius |
| Soil Sample Interval | How often the device samples and reports (1–24 hours) | 2 hours |
| Create child device for Soil Moisture | Creates a virtual humidity sensor child device | true |
| Create child device for Air Temperature | Creates a virtual temperature sensor child device | true |
| Create child device for Air Humidity | Creates a virtual humidity sensor child device | true |
| Enable Debug Logging | Logs raw frames and parsed DP values (auto-disables after 24h) | true |
| Enable Description Text Logging | Logs human-readable attribute updates | true |

---

## Child Devices

When enabled, child devices are created as standard Hubitat virtual sensors using built-in Generic Component drivers. This lets you use soil moisture, air temperature, or air humidity in apps that only work with standard capability devices (e.g. Simple Automation Rules, Google Home, Alexa).

Child devices can be enabled or disabled individually in preferences. They are created or removed automatically when you save preferences.

---

## Sample Interval

The sample interval preference (1–24 hours) is written directly to the device via a Tuya EF00 write command whenever you save preferences or press **Configure**. The `soilSampleInterval` attribute will update to confirm the new value on the next device wakeup.

> **Note:** This is a battery-powered sleepy Zigbee end device. It spends most of its time asleep and only wakes on its own schedule. The **Refresh** button sends a status query but will only get a response if the device happens to be awake at that moment.

---

## Soil & Water Warnings

DPs 110 and 111 are medium-detection flags the device uses to indicate what the probe is inserted into. These are useful for understanding the context of soil moisture readings:

- `soilWarning = water` and `waterWarning = water` — probe is submerged in water; soil moisture will read ~100% and should be disregarded
- `soilWarning = soil` — probe is inserted into soil; readings are valid
- `soilWarning = normal` — probe is in open air

---

## DP Map

| DP | Name | Description |
|----|------|-------------|
| 3 | Soil Moisture | Raw value = % |
| 5 | Temperature | Raw value / 10 = °C |
| 14 | Battery State | 0=low, 1=medium, 2=high |
| 101 | Air Humidity | Raw value = % |
| 102 | Illuminance | Raw value = lux |
| 103 | Soil Sample Interval | Raw value = seconds |
| 104 | Soil Calibration | Offset value |
| 105 | Humidity Calibration | Offset value |
| 106 | Lux Calibration | Offset value |
| 107 | Temperature Calibration | Offset value |
| 110 | Soil Warning | 0=normal, 1=soil, 2=water |
| 111 | Water Warning | 0=normal, 1=water |

---

## Changelog

| Date | Notes |
|------|-------|
| 2026-03-14 | Initial release |

---

## License

MIT — free to use, modify, and distribute.
