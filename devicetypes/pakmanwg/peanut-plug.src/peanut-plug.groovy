/**
 *  Peanut Plug
 *
 *  Copyright 2017 pakmanw@sbcglobal.net
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
 */

metadata {
	definition (name: "Peanut Plug", namespace: "pakmanwg", author: "pakmanw@sbcglobal.net", ocfDeviceType: "oic.d.switch") {
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

		command "reset"
       
		fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04, 0B05",
			outClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0019, 0B04, 0B05"
	}

	// tile definitions
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
		}
		valueTile("power", "device.power") {
			state "default", label:'${currentValue} W'
		}
		valueTile("energy", "device.energy") {
			state "default", label:'${currentValue} kWh'
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'reset kWh', action:"reset"
		}
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main(["switch","power","energy"])
		details(["switch","power","energy","refresh","reset"])
	}
}

def parse(String description) {

	log.debug "description is $description"
	def event = zigbee.getEvent(description)
	if (event) {
		if (event.name == "power") {
			def powerValue
			powerValue = (event.value as Integer)/3.6
			sendEvent(name: "power", value: powerValue)
			def time = (now() - state.time) / 3600000 / 1000
			state.time = now()
			log.debug "powerValues is $state.powerValue"
			state.energyValue = state.energyValue + (time * state.powerValue)
			state.powerValue = powerValue
			// log.debug "energyValue is $state.energyValue"
			sendEvent(name: "energy", value: state.energyValue)
		} else {
			sendEvent(event)
		}
	} else {
		log.warn "DID NOT PARSE MESSAGE for description : $description"
		log.debug zigbee.parseDescriptionAsMap(description)
	}
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
	zigbee.onOffRefresh() +
	zigbee.simpleMeteringPowerRefresh() +
	zigbee.electricMeasurementPowerRefresh() +
	zigbee.onOffConfig(0, reportIntervalMinutes * 60) +
	zigbee.simpleMeteringPowerConfig() +
	zigbee.electricMeasurementPowerConfig()
}

def configure() {
	log.debug "in configure()"
	return configureHealthCheck()
}

def configureHealthCheck() {
	Integer hcIntervalMinutes = 12
	sendEvent(name: "checkInterval", value: hcIntervalMinutes * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
	return refresh()
}

def updated() {
	log.debug "in updated()"
	// updated() doesn't have it's return value processed as hub commands, so we have to send them explicitly
	def cmds = configureHealthCheck()
	cmds.each{ sendHubCommand(new physicalgraph.device.HubAction(it)) }
}

def ping() {
	return zigbee.onOffRefresh()
}

def reset() {
	state.energyValue = 0.0
	state.powerValue = 0.0
	state.time = now()
	sendEvent(name: "energy", value: state.energyValue)
}
