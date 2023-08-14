definition(
        name: "Garage Door App",
        namespace: "Petro",
        author: "Matthew Petro",
        description: "Combine a relay, siren, and sensors into a complete garage door solution",
        category: "Convience",
        iconUrl: "",
        iconX2Url: ""
)

preferences {
    section("Devices") {
        input "relay", "capability.switch", title: "Relay that controls the garage door opener", required: true
        input "relayCloseTime", "number", title: "Number of milliseconds to keep relay closed (default: 250, range: 100..1000)?", required: false, defaultValue: 250, range: '100..1000'
        input "openContactSensor", "capability.contactSensor", title: "Contact/tilt sensor that detects door open state", required: true
        input "closedContactSensor", "capability.contactSensor", title: "Contact/tilt sensor that detects door closed state", required: true
    }

    section("Audio alerts - set either an alarm or a tone device") {
        input "alarm", "capability.alarm", title: "Alarm?", required: false
        input "alarmDuration", "number", title: "Number of seconds to sound alarm (default: 10, range: 1..60)?", required: false, defaultValue: 10, range: '1..60'
        input "alarmSound", "bool", title: "Sound alarm?", required: false, defaultValue: true
        input "alarmStrobe", "bool", title: "Flash alarm strobe?", required: false, defaultValue: true
        input "tone", "capability.tone", title: "Tone device?", required: false
    }

    section("Logging") {
        input("debug", "bool", title: "Enable logging?", required: true, defaultValue: false)
        input("descLog", "bool", title: "Enable descriptionText logging", required: true, defaultValue: true)
    }
}

def installed() {
    logDebug "Installed"
    updated()
    def deviceNetworkId = "${app.id}-simulated-garage-door-device"
    def doorDevice = addChildDevice("Petro", "Simulated Garage Door Device", deviceNetworkId, null, [label: "Simulated Garage Door Device"])
    state.data.deviceNetworkId = deviceNetworkId
    subscribe(doorDevice, "door", "garageDoorChangeHandler")
}

def updated() {
    logDebug "Updated"
    subscribe(openContactSensor, "contact", "openSensorHandler")
    subscribe(closedContactSensor, "contact", "closedSensorHandler")
}

def uninstalled() {
    logDebug "Uninstalled"
}

def garageDoorChangeHandler(event) {
    logDebug "garageDoorChangeHandler called: ${event.name} ${event.value}"
    if (event.value == "opening" && actualDoorState() == "closed") {
        pressGarageDoorButton()
    } else if (event.value == "closing" && actualDoorState() == "open") {
        playAudioAlert()
        pressGarageDoorButton()
    }
}

private actualDoorState() {
    if (openContactSensor.currentValue("contact") == "closed") {
        return "open"
    } else if (closedContactSensor.currentValue("contact") == "closed") {
        return "closed"
    } else {
        return "unknown"
    }
}

private pressGarageDoorButton() {
    logDebug "pressGarageDoorButton"
    relay.on()
    pauseExecution(relayCloseTime)
    relay.off()
}

private playAudioAlert() {
  if (null != alarm) {
    if (debug) logDebug("Activating alarm device")
    if (alarmSound && alarmStrobe) {
      alarm.both()
    } else if (alarmSound) {
      alarm.siren()
    } else if (alarmStrobe) {
      alarm.strobe()
    }
    pauseExecution(alarmDuration * 1000)
    if (debug) logDebug("Stopping alarm device")
    alarm.off()
  }
  if (null != tone) {
    if (debug) logDebug("Playing alert tone")
    tone.beep()
  }
}

def openSensorHandler(event) {
    logDebug "openSensorHandler called: ${event.name} ${event.value}"
    def doorDevice = getChildDevice(state.data.deviceNetworkId)
    def doorState = doorDevice.currentValue("door")

    if (event.value == "open" && doorState != "closing") {
        doorDevice.doorChangeHandler("closing")
    } else if (event.value == "closed") {
        doorDevice.doorChangeHandler("open")
    }
}

def closedSensorHandler(event) {
    logDebug "closedSensorHandler called: ${event.name} ${event.value}"
    def doorDevice = getChildDevice(state.data.deviceNetworkId)
    def doorState = doorDevice.currentValue("door")

    if (event.value == "open" && doorState != "opening") {
        doorDevice.doorChangeHandler("opening")
    } else if (event.value == "closed") {
        doorDevice.doorChangeHandler("closed")
    }
}

private logDebug(message) {
    if (debug) log.debug message
}