/*
 * Tuya TS0601 4-in-1 Soil Sensor
 * Device/Manufacturer: _TZE284_0ints6wl
 *
 * A Hubitat driver for the Tuya TS0601 4-in-1 soil sensor, which measures
 * soil moisture, air temperature, air humidity, and illuminance over Zigbee
 * using the Tuya EF00 private cluster.
 *
 * Features:
 *  - Soil moisture, air temperature, air humidity, illuminance reporting
 *  - Battery state and percentage
 *  - Soil/water medium detection warnings (dp 110, 111)
 *  - Adjustable sample interval (1–24 hours, written to device on save)
 *  - Optional child devices for moisture, temperature, and humidity
 *  - Temperature offset, humidity offset, moisture offset, and unit preferences
 *  - EF00 write acknowledgement handling (0x0B)
 *
 * DP Map:
 *  3   - Soil Moisture (%)
 *  5   - Air Temperature (raw /10 = °C)
 *  14  - Battery State (0=low, 1=medium, 2=high)
 *  101 - Air Humidity (%)
 *  102 - Illuminance (lux)
 *  103 - Soil Sample Interval (seconds)
 *  104 - Soil Calibration offset
 *  105 - Humidity Calibration offset
 *  106 - Lux Calibration offset
 *  107 - Temperature Calibration offset
 *  110 - Soil Warning (0=normal, 1=soil, 2=water)
 *  111 - Water Warning (0=normal, 1=water)
 *
 * Author: CHUV7 with the extreme help of Claude AI
 * Changelog:
 *  2026-03-14 - Initial release
 */

import groovy.transform.Field

metadata {
    definition(
        name: "Tuya TS0601 4-in-1 Soil Sensor",
        namespace: "CHUV7",
        author: "CHUV7",
        importUrl: ""
    ) {
        capability "RelativeHumidityMeasurement"  // air humidity
        capability "TemperatureMeasurement"
        capability "IlluminanceMeasurement"
        capability "Battery"
        capability "Refresh"
        capability "Sensor"
        capability "Configuration"

        attribute "soilMoisture",        "number"   // soil moisture %
        attribute "batteryState",        "string"
        attribute "soilWarning",         "string"   // "normal" | "soil" | "water"
        attribute "waterWarning",        "string"   // "normal" | "water"
        attribute "soilSampleInterval",  "number"
        attribute "soilCalibration",     "number"
        attribute "humidityCalibration", "number"
        attribute "luxCalibration",      "number"
        attribute "tempCalibration",     "number"

        fingerprint profileId: "0104", endpointId: "01",
                    inClusters: "0000,0004,0005,EF00,ED00",
                    outClusters: "0019,000A",
                    model: "TS0601", manufacturer: "_TZE284_0ints6wl"
    }

    preferences {
        input name: "tempOffset",      type: "decimal", title: "Air Temperature Offset (°C)",  defaultValue: 0.0
        input name: "humidityOffset",  type: "decimal", title: "Air Humidity Offset (%)",       defaultValue: 0.0
        input name: "moistureOffset",  type: "decimal", title: "Soil Moisture Offset (%)",      defaultValue: 0.0
        input name: "tempUnit",        type: "enum",    title: "Temperature Unit",
                                       options: ["Celsius", "Fahrenheit"],                      defaultValue: "Celsius"
        input name: "createMoistureChild",   type: "bool", title: "Create child device for Soil Moisture",   defaultValue: true
        input name: "createTempChild",       type: "bool", title: "Create child device for Air Temperature", defaultValue: true
        input name: "createHumidityChild",   type: "bool", title: "Create child device for Air Humidity",    defaultValue: true
        input name: "sampleInterval", type: "enum", title: "Soil Sample Interval (hours)",
                                       options: ["1","2","3","4","5","6","7","8","9","10","11","12",
                                                 "13","14","15","16","17","18","19","20","21","22","23","24"],
                                       defaultValue: "2"
        input name: "logEnable",  type: "bool", title: "Enable Debug Logging",            defaultValue: true
        input name: "txtEnable",  type: "bool", title: "Enable Description Text Logging", defaultValue: true
    }
}

// ── Tuya DP map ──────────────────────────────────────────────────────────────
//
// DP values must exactly match the case labels in the parse() switch block.
//
@Field static final Map TUYA_DP_MAP = [
    3  : "soilMoisture",
    5  : "temperature",
    14 : "batteryState",
    101: "airHumidity",
    102: "illuminance",
    103: "soilSampleInterval",
    104: "soilCalibration",
    105: "humidityCalibration",
    106: "luxCalibration",
    107: "tempCalibration",
    110: "soilWarning",
    111: "waterWarning"
]

@Field static final Map BATTERY_STATE_MAP = [
    0: "low",
    1: "medium",
    2: "high"
]

@Field static final Map BATTERY_PCT_MAP = [
    0: 10,
    1: 50,
    2: 100
]

// DP 110 — inserted into soil vs water vs air (4-in-1 mode detection)
@Field static final Map SOIL_WARNING_MAP = [
    0: "normal",
    1: "soil",
    2: "water"
]

// DP 111 — water detection flag
@Field static final Map WATER_WARNING_MAP = [
    0: "normal",
    1: "water"
]

@Field static final Map CHILD_CONFIG = [
    "moisture": [driver: "Generic Component Humidity Sensor",     label: "Soil Moisture"],
    "temp"    : [driver: "Generic Component Temperature Sensor",  label: "Air Temp"],
    "humidity": [driver: "Generic Component Humidity Sensor",     label: "Air Humidity"]
]

// ── Lifecycle ────────────────────────────────────────────────────────────────
def installed() {
    log.info "${device.displayName} installed"
    initialize()
}

def updated() {
    log.info "${device.displayName} preferences updated"
    if (logEnable) runIn(86400, "logsOff")
    initialize()
    manageChildren()
    setSampleInterval()
}

def configure() {
    log.info "${device.displayName} configure()"
    manageChildren()
    setSampleInterval()
    return [zigbee.command(0xEF00, 0x03, "")]
}

def initialize() {
    sendEvent(name: "batteryState", value: "unknown")
    sendEvent(name: "soilWarning",  value: "unknown")
    sendEvent(name: "waterWarning", value: "unknown")
}

def logsOff() {
    log.warn "${device.displayName} debug logging disabled after 24h"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ── Child device management ───────────────────────────────────────────────────
private void manageChildren() {
    manageChild("moisture", createMoistureChild != false)
    manageChild("temp",     createTempChild     != false)
    manageChild("humidity", createHumidityChild != false)
}

private void manageChild(String suffix, boolean shouldExist) {
    def childDni = "${device.deviceNetworkId}-${suffix}"
    def existing = getChildDevice(childDni)
    def cfg      = CHILD_CONFIG[suffix]

    if (shouldExist && !existing) {
        log.info "${device.displayName} creating child device: ${cfg.label}"
        addChildDevice(
            "hubitat", cfg.driver, childDni,
            [name: "${device.displayName} - ${cfg.label}", isComponent: false]
        )
    } else if (!shouldExist && existing) {
        log.info "${device.displayName} removing child device: ${cfg.label}"
        deleteChildDevice(childDni)
    }
}

private void updateChild(String suffix, String eventName, def value, String unit, String descText) {
    def child = getChildDevice("${device.deviceNetworkId}-${suffix}")
    if (child) {
        child.sendEvent(name: eventName, value: value, unit: unit, descriptionText: descText)
    }
}

// ── Sample interval ───────────────────────────────────────────────────────────
private void setSampleInterval() {
    int hours   = (sampleInterval ?: "2").toInteger()
    int seconds = hours * 3600

    // Tuya EF00 write: dp=103 (0x67), dp_type=0x02 (int32), length=0x0004, value as 4 bytes
    String payload = "00670200" + String.format("%08X", seconds)
    if (logEnable) log.debug "${device.displayName} setSampleInterval() ${hours}h = ${seconds}s payload=${payload}"
    sendHubCommand(new hubitat.device.HubAction(
        zigbee.command(0xEF00, 0x00, payload)[0],
        hubitat.device.Protocol.ZIGBEE
    ))
}

// ── Refresh ───────────────────────────────────────────────────────────────────
def refresh() {
    if (logEnable) log.debug "${device.displayName} refresh() - sending Tuya query command"
    return [zigbee.command(0xEF00, 0x03, "")]
}

void componentRefresh(cd) {
    if (logEnable) log.debug "${device.displayName} componentRefresh() requested by ${cd?.displayName}"
    refresh()
}

// ── Parse ─────────────────────────────────────────────────────────────────────
def parse(String description) {
    if (logEnable) log.debug "${device.displayName} parse() raw: ${description}"

    def descMap = zigbee.parseDescriptionAsMap(description)

    if (descMap?.clusterInt != 0xEF00) {
        if (logEnable) log.debug "${device.displayName} ignoring non-EF00 cluster: ${descMap?.cluster}"
        return
    }

    if (descMap?.command != "01" && descMap?.command != "02" && descMap?.command != "0B") {
        if (logEnable) log.debug "${device.displayName} ignoring EF00 command: ${descMap?.command}"
        return
    }

    // 0x0B = write acknowledgement, no data to parse
    if (descMap?.command == "0B") {
        if (logEnable) log.debug "${device.displayName} EF00 write acknowledged by device"
        return
    }

    def data = descMap?.data
    if (!data || data.size() < 7) {
        if (logEnable) log.debug "${device.displayName} insufficient data length: ${data}"
        return
    }

    // Tuya EF00 frame: [seq_hi, seq_lo, dp, dp_type, len_hi, len_lo, value...]
    def dp     = Integer.parseInt(data[2], 16)
    def fncmd  = getTuyaAttributeValue(data, 4)
    def dpName = TUYA_DP_MAP[dp]

    if (logEnable) log.debug "${device.displayName} dp=${dp} value=${fncmd} -> ${dpName ?: 'UNKNOWN'}"

    switch (dpName) {
        case "soilMoisture":
            handleSoilMoisture(fncmd)
            break
        case "temperature":
            handleTemperature(fncmd)
            break
        case "airHumidity":
            handleAirHumidity(fncmd)
            break
        case "illuminance":
            handleIlluminance(fncmd)
            break
        case "batteryState":
            handleBatteryState(fncmd)
            break
        case "soilSampleInterval":
            int hrs = fncmd / 3600
            if (logEnable) log.debug "${device.displayName} soil sample interval = ${fncmd}s (${hrs}h)"
            sendEvent(name: "soilSampleInterval", value: hrs, unit: "h",
                      descriptionText: "Soil sample interval is ${hrs}h (${fncmd}s)")
            break
        case "soilCalibration":
            if (logEnable) log.debug "${device.displayName} soil calibration = ${fncmd}"
            sendEvent(name: "soilCalibration", value: fncmd,
                      descriptionText: "Soil calibration offset is ${fncmd}")
            break
        case "humidityCalibration":
            if (logEnable) log.debug "${device.displayName} humidity calibration = ${fncmd}"
            sendEvent(name: "humidityCalibration", value: fncmd,
                      descriptionText: "Humidity calibration offset is ${fncmd}")
            break
        case "luxCalibration":
            if (logEnable) log.debug "${device.displayName} lux calibration = ${fncmd}"
            sendEvent(name: "luxCalibration", value: fncmd,
                      descriptionText: "Lux calibration offset is ${fncmd}")
            break
        case "tempCalibration":
            if (logEnable) log.debug "${device.displayName} temp calibration = ${fncmd}"
            sendEvent(name: "tempCalibration", value: fncmd,
                      descriptionText: "Temperature calibration offset is ${fncmd}")
            break
        case "soilWarning":
            handleSoilWarning(fncmd)
            break
        case "waterWarning":
            handleWaterWarning(fncmd)
            break
        default:
            log.warn "${device.displayName} NOT PROCESSED dp=${dp} value=${fncmd} data=${data}"
            break
    }
}

// ── Handlers ──────────────────────────────────────────────────────────────────
private void handleSoilMoisture(int raw) {
    def offset   = moistureOffset ?: 0.0
    def moisture = Math.round((raw + offset) * 10) / 10.0
    moisture     = Math.max(0.0, Math.min(100.0, moisture))

    def descText = "Soil moisture is ${moisture}%"
    if (txtEnable) log.info "${device.displayName} ${descText}"

    sendEvent(name: "soilMoisture", value: moisture, unit: "%", descriptionText: descText)
    updateChild("moisture", "humidity", moisture, "%", descText)
}

private void handleTemperature(int raw) {
    def offset  = tempOffset ?: 0.0
    def tempC   = (raw / 10.0) + offset
    def displayTemp
    def unit

    if (tempUnit == "Fahrenheit") {
        displayTemp = Math.round((tempC * 9.0 / 5.0 + 32.0) * 10) / 10.0
        unit = "°F"
    } else {
        displayTemp = Math.round(tempC * 10) / 10.0
        unit = "°C"
    }

    def descText = "Air temperature is ${displayTemp}${unit}"
    if (txtEnable) log.info "${device.displayName} ${descText}"

    sendEvent(name: "temperature", value: displayTemp, unit: unit, descriptionText: descText)
    updateChild("temp", "temperature", displayTemp, unit, descText)
}

private void handleAirHumidity(int raw) {
    def offset   = humidityOffset ?: 0.0
    def humidity = Math.round((raw + offset) * 10) / 10.0
    humidity     = Math.max(0.0, Math.min(100.0, humidity))

    def descText = "Air humidity is ${humidity}%"
    if (txtEnable) log.info "${device.displayName} ${descText}"

    sendEvent(name: "humidity", value: humidity, unit: "%", descriptionText: descText)
    updateChild("humidity", "humidity", humidity, "%", descText)
}

private void handleIlluminance(int raw) {
    def descText = "Illuminance is ${raw} lux"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: "illuminance", value: raw, unit: "lux", descriptionText: descText)
}

private void handleBatteryState(int raw) {
    def stateStr = BATTERY_STATE_MAP[raw] ?: "unknown"
    def pct      = BATTERY_PCT_MAP[raw]   ?: 0

    if (txtEnable) log.info "${device.displayName} battery state is ${stateStr} (~${pct}%)"
    sendEvent(name: "batteryState", value: stateStr, descriptionText: "Battery state is ${stateStr}")
    sendEvent(name: "battery", value: pct, unit: "%", descriptionText: "Battery is ~${pct}%")
}

private void handleSoilWarning(int raw) {
    def state    = SOIL_WARNING_MAP[raw] ?: "unknown"
    def descText = "Sensor medium detected: ${state}"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: "soilWarning", value: state, descriptionText: descText)
}

private void handleWaterWarning(int raw) {
    def state    = WATER_WARNING_MAP[raw] ?: "unknown"
    def descText = "Water detection: ${state}"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: "waterWarning", value: state, descriptionText: descText)
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private int getTuyaAttributeValue(List<String> data, int startIndex) {
    int lenHi  = Integer.parseInt(data[startIndex],     16)
    int lenLo  = Integer.parseInt(data[startIndex + 1], 16)
    int length = (lenHi << 8) | lenLo

    int value = 0
    for (int i = 0; i < length; i++) {
        value = (value << 8) | Integer.parseInt(data[startIndex + 2 + i], 16)
    }
    return value
}
