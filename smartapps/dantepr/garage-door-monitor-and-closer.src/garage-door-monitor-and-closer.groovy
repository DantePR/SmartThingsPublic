/**
 *  Copyright 2015 SmartThings
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
 *  Garage Door Monitor - monitors if door is left open and sends a text, can also choose to close the door 
 *
 *  Author: Ray Perez
 */
definition(
    name: "Garage Door Monitor and Closer",
    namespace: "dantepr",
    author: "Ray Perez",
    description: "Monitor your garage door and get a text message if it is open too long, and trigger a closing action",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png"
)

preferences {
    //log.trace "preferences()"
	section("When the garage door is open...") {
		input "garageDoor", "capability.garageDoorControl", title: "Which?"
        input "closeTheDoor", "bool", title: "Automaticaly close the door?", required: false
	}
	section("For too long...") {
		input "maxOpenTime", "number", title: "Minutes?"
	}
    section("When things QuietDown ...") {
		input "motion1", "capability.motionSensor", title: "Where?", multiple: true
	}
   
	section("Text me at (optional, sends a push notification if not specified)...") {
        input("recipients", "contact", title: "Notify", description: "Send notifications to") {
            input "phone", "phone", title: "Phone number?", required: false
        }
	}
}

def installed()
{
	log.trace "installed()"
    subscribe(garageDoor, "door", garageDoorContact)
    //subscribe(motion1, "motion", motionInGarage)
    state.openDoorNotificationSent = false
    state.lastMotion = null
}

def updated()
{
   log.trace "update()"
	unsubscribe()
    state.lastMotion = null
    state.openDoorNotificationSent = false
    subscribe(garageDoor, "door", garageDoorContact)
     //subscribe(motion1, "motion", motionInGarage)
}
def garageDoorContact(evt)
{   state.openDoorNotificationSent = false
	log.info "garageDoorContact, $evt.name: $evt.value"
	if (evt.value == "open") {
       log.info "Scheduling doorOpenCheck"
		//schedule("0 * * * * ?", "doorOpenCheck")
        runEvery1Minute("doorOpenCheck")
	}
	else {
       log.info "Un-Scheduling doorOpenCheck"
       
		unschedule("doorOpenCheck")
	}
}

def doorOpenCheck()
{
   log.debug "doorOpenCheck 1"
	final thresholdMinutes = maxOpenTime
    log.debug "thresholdMinutes ${thresholdMinutes}"
	if (thresholdMinutes) {
		def currentState = garageDoor.currentState("door")
        
		log.debug "doorOpenCheck 2"
        def motionState = motion1.currentState("motion")
        String strMotionState = motionState.value.toString()
        String strDoorState = currentState.value.toString()
            log.debug "strDoorState ${strDoorState}"
            log.debug "strMotionState ${strMotionState}"
            
		if (strDoorState.equalsIgnoreCase("open")  && strMotionState.equalsIgnoreCase("[inactive]")) {
            log.debug "CurrentStateTime ${currentState.date.time}"
            log.debug "MotionStateTime ${motionState.date.time}"
            def elapsedMotion = now() - motionState.date.time
            def elapsedDoorState = now() - currentState.date.time
            def threshold = thresholdMinutes * 60 *1000
            
			log.debug "open for ${now() - currentState.date.time}, openDoorNotificationSent: ${state.openDoorNotificationSent}"
			if ((!state.openDoorNotificationSent && elapsedDoorState > threshold)  && (elapsedMotion  >  threshold)) {
				def msg = "${garageDoor.displayName} was been open for ${thresholdMinutes} minutes"
				log.info msg
				sendTextMessage()
                closeDoor()
				state.openDoorNotificationSent = true
			}  
		}
		
	}
}

def sendTextMessage() {
	log.debug "$garageDoor was open too long, texting phone"

	//updateSmsHistory()
	//def openMinutes = maxOpenTime * (state.smsHistory?.size() ?: 1)
	def msg = "Your ${garageDoor.displayName} has been open for more than ${maxOpenTime} minutes!"
    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    }
    else {
        if (phone) {
            sendSms(phone, msg)
        } else {
            sendPush msg
        }
    }
}

private closeDoor()
{   
      def currentState = garageDoor.currentState("door")
      String strDoorState = currentState.value.toString()
      log.debug "cheking door before closing , its ${strDoorState} "
	if (strDoorState.equalsIgnoreCase("open") && closeTheDoor) {
		log.debug "closing door"
		garageDoor.close()
	}
}
def updateSmsHistory() {
	if (!state.smsHistory) state.smsHistory = []

	if(state.smsHistory.size() > 9) {
		log.debug "SmsHistory is too big, reducing size"
		state.smsHistory = state.smsHistory[-9..-1]
	}
	state.smsHistory << [sentDate: new Date().toSystemFormat()]
}

def clearSmsHistory() {
	state.smsHistory = null
}

def clearStatus() {
	state.status = null
}