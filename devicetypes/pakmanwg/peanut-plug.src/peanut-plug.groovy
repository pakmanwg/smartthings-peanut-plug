/**
 *  Peanut Plug
 *
 *  Copyright 2020 pakmanw@sbcglobal.net
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Peanut Plug
 *
 *  Author: pakmanw@sbcglobal.net
 *
 *  Change Log
 *  2017-09-17 - v01.01 Created
 *  2018-03-01 - v01.02 fix power accuracy issue
 *  2018-12-23 - v01.03 merging jamesham change to get the calibrated attr from peanut plug,
 *                      add support for new smartthings app
 *  2019-01-17 - v01.04 merging jamesham retain state code
 *  2019-09-27 - v01.05 update fingerprint from transman
 *  2019-09-30 - v01.06 new interface, add active power settings
 *  2019-10-01 - v01.07 add cost per kwh
 *  2019-10-02 - v01.08 add reset time
 *  2019-10-03 - v01.09 two decimal points for values
 *  2020-02-23 - v01.10 fix energy value not update issues
 *  2020-03-31 - v01.11 fix decimal place display problem in some devices
 */

import physicalgraph.zigbee.zcl.DataType

metadata {
  definition (name: "Peanut Plug", namespace: "pakmanwg", author: "pakmanw@sbcglobal.net", ocfDeviceType: "oic.d.switch",
    vid: "generic-switch-power-energy") {
    capability "Energy Meter"
    capability "Actuator"
    capability "Switch"
    capability "Power Meter"
    capability "Polling"
    capability "Refresh"
    capability "Configuration"
    capability "Sensor"
    capability "Light"
    capability "Health Check"
    capability "Voltage Measurement"

    attribute "current", "number"
    attribute "switchStatus", "string"

    command "reset"

    fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0B04, 0B05",
      outClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0019, 0B04, 0B05",
      manufacturer: "Securifi Ltd."
  }

  // tile definitions
  tiles(scale: 2) {
    multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true) {
      tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
        attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc"
        attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
      }
      tileAttribute ("device.switchStatus", key: "SECONDARY_CONTROL") {
        attributeState "switchStatus", label:'${currentValue}'
      }
    }
    standardTile("refresh", "device.refresh", width: 2, height: 2) {
      state "refresh", label:'Refresh', action: "refresh", icon:"st.secondary.refresh-icon"
    }
    standardTile("reset", "device.reset", width: 2, height: 2) {
      state "reset", label:'Reset', action: "reset", icon:"st.secondary.refresh-icon"
    }
    valueTile("power", "device.power", width: 2, height: 2) {
      state "default", label:'${currentValue} W', backgroundColor: "#cccccc"
    }
    valueTile("energy", "device.energy", width: 2, height: 2) {
      state "default", label:'${currentValue} kWh', backgroundColor: "#cccccc"
    }
    valueTile("voltage", "device.voltage", width: 2, height: 2) {
      state "default", label:'${currentValue} V', backgroundColor: "#cccccc"
    }
    valueTile("current", "device.current", width: 2, height: 2) {
      state "default", label:'${currentValue} A', backgroundColor: "#cccccc"
    }
    valueTile("history", "device.history", decoration:"flat", width: 6, height: 3) {
      state "history", label:'${currentValue}'
    }

    main(["switch"])
    details(["switch", "power", "energy", "refresh", "voltage", "current", "reset"])
  }

  preferences {
    input name: "retainState", type: "bool", title: "Retain State?", description: "Retain state on power loss?", required: false, displayDuringSetup: false,
      defaultValue: true
    // configParams?.each {
    //   getOptionsInput(it)
    // }
    input "energyPrice", "number", title: "\$/kWh Cost:", description: "Electric Cost per Kwh in cent", range: "0..*", defaultValue: 15
    input "inactivePower", "decimal", title: "Reporting inactive when power is less than or equal to:", range: "0..*", defaultValue: 0
    // ["Power", "Energy", "Voltage", "Current"].each {
    //   getBoolInput("display${it}", "Display ${it} Activity", true)
    // }
  }
}

private getOptionsInput(param) {
  if (param.prefName) {
    input "${param.prefName}", "enum",
      title: "${param.name}:",
      defaultValue: "${param.val}",
      required: false,
      displayDuringSetup: true,
      options: param.options?.collect { name, val -> name }
  }
}

private getBoolInput(name, title, defaultVal) {
  input "${name}", "bool",
    title: "${title}?",
    defaultValue: defaultVal,
    required: false
}

def installed() {
  reset()
  configure()
  refresh()
}

def off() {
  zigbee.off()
}

def on() {
  zigbee.on()
}

def refresh() {
  Integer reportIntervalMinutes = 5
  setRetainState() +
  zigbee.onOffRefresh() +
  zigbee.simpleMeteringPowerRefresh() +
  zigbee.electricMeasurementPowerRefresh() +
  zigbee.onOffConfig(1, 600) +
  zigbee.simpleMeteringPowerConfig(1, 600) +
  zigbee.electricMeasurementPowerConfig(1, 600) +
  voltageMeasurementRefresh() +
  voltageMeasurementConfig(reportIntervalMinutes * 60, 600) +
  currentMeasurementRefresh() +
  currentMeasurementConfig(reportIntervalMinutes * 60, 600) +
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0600) +
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0601) +
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0602) +
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0603) +
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0604) +
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0605)
}

def currentMeasurementConfig(minReportTime=1, maxReportTime=600, reportableChange=0x0030) {
  zigbee.configureReporting(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0508, DataType.UINT16, minReportTime, maxReportTime, reportableChange)
}

def currentMeasurementRefresh() {
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0508);
}

def voltageMeasurementConfig(minReportTime=1, maxReportTime=600, reportableChange=0x0018) {
  zigbee.configureReporting(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0505, DataType.UINT16, minReportTime, maxReportTime, reportableChange)
}

def voltageMeasurementRefresh() {
  zigbee.readAttribute(zigbee.ELECTRICAL_MEASUREMENT_CLUSTER, 0x0505);
}

def getCurrentMultiplier() {
  if (state.currentMultiplier && state.currentDivisor) {
    return (state.currentMultiplier / state.currentDivisor)
  } else {
    return 0.001831
  }
}

def getVoltageMultiplier() {
  if (state.voltageMultiplier && state.voltageDivisor) {
    return (state.voltageMultiplier / state.voltageDivisor)
  } else {
    return 0.0045777
  }
}

def getPowerMultiplier() {
  if (state.powerMultiplier && state.powerDivisor) {
    return (state.powerMultiplier / state.powerDivisor)
  } else {
    return 0.277
  }
}

def configure() {
  log.debug "in configure()"
  return configureHealthCheck() + setRetainState()
}

def configureHealthCheck() {
  Integer hcIntervalMinutes = 12
  sendEvent(name: "checkInterval", value: hcIntervalMinutes * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
  return refresh()
}

def updated() {
  log.debug "in updated()"
  // updated() doesn't have it's return value processed as hub commands, so we have to send them explicitly
  def cmds = configureHealthCheck() + setRetainState()
  cmds.each{ sendHubCommand(new physicalgraph.device.HubAction(it)) }
}

def ping() {
  return zigbee.onOffRefresh()
}

def setRetainState() {
  log.debug "Setting Retain State: $retainState"
  if (retainState == null || retainState) {
    if (retainState == null) {
      log.warn "retainState is null, defaulting to 'true' behavior"
    }
    return zigbee.writeAttribute(0x0003, 0x0000, DataType.UINT16, 0x0000)
  } else {
    return zigbee.writeAttribute(0x0003, 0x0000, DataType.UINT16, 0x1111)
  }
}

def reset() {
  state.energyValue = 0.0
  state.powerValue = 0.0
  state.voltage = 0.0
  state.current = 0.0
  state.costValue = 0.0
  state.time = now()
  sendEvent(name: "energy", value: (String.format("%.2f", state.energyValue)))
  sendEvent(name: "cost", value: (String.format("%.2f", state.costValue)))
  state.resetTime = new Date().format('MM/dd/yy hh:mm a', location.timeZone)
}

def refreshHistory() {
  def history = ""
  def items = [:]

  items["energyDuration"] = "Energy - Duration"
  items["energyCost"] = "Energy - Cost"
  ["power", "voltage", "current"].each {
    items["${it}Low"] = "${it.capitalize()} - Low"
    items["${it}High"] = "${it.capitalize()} - High"
  }
  items.each { attrName, caption ->
    def attr = device.currentState("${attrName}")
    def val = attr?.value ?: ""
    def unit = attr?.unit ?: ""
    history += "${caption}: ${val} ${unit}\n"
  }
  sendEvent(createEventMap("history", history, false))
}

// Configuration Parameters
private getConfigParams() {
  return [
    powerValueChangeParam,
    powerPercentageChangeParam,
    powerReportIntervalParam,
    energyReportIntervalParam,
    voltageReportIntervalParam,
    electricityReportIntervalParam
  ]
}

private getPowerValueChangeParam() {
  return createConfigParamMap(151, "Power Report Value Change", 2, getPowerValueOptions(), "powerValueChange")
}

private getPowerPercentageChangeParam() {
  return createConfigParamMap(152, "Power Report Percentage Change", 1, getPercentageOptions(10, "No Reports"), "powerPercentageChange")
}

private getPowerReportIntervalParam() {
  return createConfigParamMap(171, "Power Reporting Interval", 4, getIntervalOptions(30, "No Reports"), "powerReportingInterval")
}

private getEnergyReportIntervalParam() {
  return createConfigParamMap(172, "Energy Reporting Interval", 4, getIntervalOptions(300, "No Reports"), "energyReportingInterval")
}

private getVoltageReportIntervalParam() {
  return createConfigParamMap(173, "Voltage Reporting Interval", 4, getIntervalOptions(0, "No Reports"), "voltageReportingInterval")
}

private getElectricityReportIntervalParam() {
  return createConfigParamMap(174, "Electrical Current Reporting Interval", 4, getIntervalOptions(0, "No Reports"), "electricityReportingInterval")
}

private createConfigParamMap(num, name, size, options, prefName, val=null) {
  if (val == null) {
    val = (settings?."${prefName}" ?: findDefaultOptionName(options))
  }
  return [
    num: num,
    name: name,
    size: size,
    options: options,
    prefName: prefName,
    val: val
  ]
}

// Settings
private getEnergyPriceSetting() {
  return safeToDec(settings?.energyPrice, 0.12)
}

private getInactivePowerSetting() {
  return safeToDec(settings?.inactivePower, 0)
}

private getDebugOutputSetting() {
  return settings?.debugOutput != false
}

private getMinimumReportingInterval() {
  def minVal = (60 * 60 * 24 * 7)
  [powerReportIntervalParam, energyReportIntervalParam, voltageReportIntervalParam, electricityReportIntervalParam].each {
    def val = convertOptionSettingToInt(it.options, it.val)
    if (val && val < minVal) {
      minVal = val
    }
  }
  return minVal
}

private getIntervalOptions(defaultVal=null, zeroName=null) {
  def options = [:]
  if (zeroName) {
    options["${zeroName}"] = 0
  }
  options << getIntervalOptionsRange("Second", 1, [5,10,15,30,45])
  options << getIntervalOptionsRange("Minute", 60, [1,2,3,4,5,10,15,30,45])
  options << getIntervalOptionsRange("Hour", (60 * 60), [1,2,3,6,9,12,18])
  options << getIntervalOptionsRange("Day", (60 * 60 * 24), [1,3,5])
  options << getIntervalOptionsRange("Week", (60 * 60 * 24 * 7), [1,2])
  return setDefaultOption(options, defaultVal)
}

private getIntervalOptionsRange(name, multiplier, range) {
  def options = [:]
  range?.each {
    options["${it} ${name}${it == 1 ? '' : 's'}"] = (it * multiplier)
  }
  return options
}

private getPowerValueOptions() {
  def options = [:]
  [0,1,2,3,4,5,10,25,50,75,100,150,200,250,300,400,500,750,1000,1250,1500,1750,2000,2500,3000,3500,4000,4500,5000,6000,7000,8000,9000,10000,12500,15000].each {
    if (it == 0) {
      options["No Reports"] = it
    }
    else {
      options["${it} Watts"] = it
    }
  }
  return setDefaultOption(options, 50)
}

private getPercentageOptions(defaultVal=null, zeroName=null) {
  def options = [:]
  if (zeroName) {
    options["${zeroName}"] = 0
  }
  for (int i = 1; i <= 5; i += 1) {
    options["${i}%"] = i
  }
  for (int i = 10; i <= 100; i += 5) {
    options["${i}%"] = i
  }
  return setDefaultOption(options, defaultVal)
}

private convertOptionSettingToInt(options, settingVal) {
  return safeToInt(options?.find { name, val -> "${settingVal}" == name }?.value, 0)
}

private setDefaultOption(options, defaultVal) {
  def name = options.find { key, val -> val == defaultVal }?.key
  if (name != null) {
    return changeOptionName(options, defaultVal, "${name}${defaultOptionSuffix}")
  }
  else {
    return options
  }
}

private changeOptionName(options, optionVal, newName) {
  def result = [:]
  options?.each { name, val ->
    if (val == optionVal) {
      name = "${newName}"
    }
    result["${name}"] = val
  }
  return result
}

private findDefaultOptionName(options) {
  def option = options?.find { name, val ->
    name?.contains("${defaultOptionSuffix}")
  }
  return option?.key ?: ""
}

private getDefaultOptionSuffix() {
  return "   (Default)"
}

private createEventMap(name, value, displayed=null, desc=null, unit=null) {
  desc = desc ?: "${name} is ${value}"

  def eventMap = [
    name: name,
    value: value,
    displayed: (displayed == null ? ("${getAttrVal(name)}" != "${value}") : displayed)
  ]

  if (unit) {
    eventMap.unit = unit
    desc = "${desc} ${unit}"
  }

  if (desc && eventMap.displayed) {
    logDebug desc
    eventMap.descriptionText = "${device.displayName} - ${desc}"
  }
  else {
    logTrace "Creating Event: ${eventMap}"
  }
  return eventMap
}

private getAttrVal(attrName) {
  try {
    return device?.currentValue("${attrName}")
  }
  catch (ex) {
    logTrace "$ex"
    return null
  }
}

private safeToInt(val, defaultVal=0) {
  return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private safeToDec(val, defaultVal=0) {
  return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}

private roundTwoPlaces(val) {
  return Math.round(safeToDec(val) * 100) / 100
}

private convertToLocalTimeString(dt) {
  def timeZoneId = location?.timeZone?.ID
  if (timeZoneId) {
    return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
  }
  else {
    return "$dt"
  }
}

private isDuplicateCommand(lastExecuted, allowedMil) {
  !lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}

def parse(String description) {

  // log.debug "description is $description"
  def event = zigbee.getEvent(description)
  if (event) {
    if (event.name == "power") {
      def powerValue = (event.value as Integer) * getPowerMultiplier()
      sendEvent(name: "power", value: (String.format("%.2f", powerValue)))
      def time = (now() - state.time) / 3600000 / 1000
      state.time = now()
      // log.debug "powerValues is $state.powerValue"
      state.energyValue = state.energyValue + (time * powerValue)
      state.powerValue = powerValue
      // log.debug "energyValue is $state.energyValue"
      def localCostPerKwh = 15
      if (energyPrice) {
        localCostPerKwh = energyPrice as Integer
      }
      sendEvent(name: "energy", value: (String.format("%.2f", state.energyValue)))
      state.costValue = roundTwoPlaces(state.energyValue * localCostPerKwh / 100)
      sendEvent(name: "cost", value: (String.format("%.2f", state.costValue)))
      if (inactivePowerSetting == null) {
        inactivePowerSetting = 0
      }
      if (state.resetTime == null) {
        state.resetTime = new Date().format('MM/dd/yy hh:mm a', location.timeZone)
      }
      def costStr = (String.format("%.2f", state.costValue))
      def switchStatusS = (powerValue > inactivePowerSetting) ? "Active | Cost \$$state.costValue since $state.resetTime" :
        "Inactive | Cost \$$costStr since $state.resetTime"
      // log.debug "$switchStatusS"
      sendEvent(name: "switchStatus", value: switchStatusS, displayed: false)
      // refreshHistory
    } else {
      sendEvent(event)
    }
  } else if (description?.startsWith("read attr -")) {
    def descMap = zigbee.parseDescriptionAsMap(description)
    // log.debug "Desc Map: $descMap"
    if (descMap.clusterInt == zigbee.ELECTRICAL_MEASUREMENT_CLUSTER) {
      def intVal = Integer.parseInt(descMap.value,16)
      if (descMap.attrInt == 0x0600) {
        // log.debug "ACVoltageMultiplier $intVal"
        state.voltageMultiplier = intVal
      } else if (descMap.attrInt == 0x0601) {
        // log.debug "ACVoltageDivisor $intVal"
        state.voltageDivisor = intVal
      } else if (descMap.attrInt == 0x0602) {
        // log.debug "ACCurrentMultiplier $intVal"
        state.currentMultiplier = intVal
      } else if (descMap.attrInt == 0x0603) {
        // log.debug "ACCurrentDivisor $intVal"
        state.currentDivisor = intVal
      } else if (descMap.attrInt == 0x0604) {
        // log.debug "ACPowerMultiplier $intVal"
        state.powerMultiplier = intVal
      } else if (descMap.attrInt == 0x0605) {
        // log.debug "ACPowerDivisor $intVal"
        state.powerDivisor = intVal
      } else if (descMap.attrInt == 0x0505) {
        def voltageValue = roundTwoPlaces(intVal * getVoltageMultiplier())
        // log.debug "Voltage ${voltageValue}"
        state.voltage = $voltageValue
        sendEvent(name: "voltage", value: (String.format("%.2f", voltageValue)))
      } else if (descMap.attrInt == 0x0508) {
        def currentValue = roundTwoPlaces(intVal * getCurrentMultiplier())
        // log.debug "Current ${currentValue}"
        state.current = $currentValue
        sendEvent(name: "current", value: (String.format("%.2f", currentValue)))
      }
    } else {
      log.warn "Not an electrical measurement"
    }
  } else {
    log.warn "DID NOT PARSE MESSAGE for description : $description"
    log.debug zigbee.parseDescriptionAsMap(description)
  }
}

