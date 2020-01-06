#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GCAL_APP="${DIR}/../target/gcal-meeting-1.0-jar-with-dependencies.jar"
_verbose=1

get_emoji() {
  STATUS="$1"
  EMOJI=":calendar:"

  case "$STATUS" in 
    *Amtrak*) EMOJI=":mountain_railway:";;
    *Lunch*) EMOJI=":burrito:" ;;
    *Kid*) EMOJI=":children_crossing:" ;;
  esac  

  echo $EMOJI
}

log() {
  if [[ $_verbose -eq 1 ]]; then
    echo "$@"
  fi
}


## check verbose

while getopts "v" OPTION
do
  case $OPTION in
    v) _verbose=1
       log "Verbose mode on"
       ;;
  esac
done



emoji=""
meetingDetail=$(java -jar $GCAL_APP)


log "Meeting Detail: ${meetingDetail}"
if [[ $meetingDetail == *"ERROR:"* ]]; then
  exit
elif [[ $meetingDetail == "none" ]]; then
  emoji=""
  meetingDetail=""

  ## Check IP to see if we're in San Francisco or Sacramento
  myIp=$(curl -s http://ip.dnsexit.com | tail -1 | sed -e 's/[[:space:]]//g')
  log "My Ip: {$myIp}"
  case "$myIp" in
    *1.1.1.1*) emoji=":us_sac:";;
    *2.2.2.2*) emoji=":us_sf:";;
  esac
  
  ## Check time of day and idle time to see if it's 2nd shift
  idleTimeSec=$((`ioreg -c IOHIDSystem | sed -e '/HIDIdleTime/ !{ d' -e 't' -e '}' -e 's/.* = //g' -e 'q'` / 1000000000))
  hourOfDay=`date +%H`
  hourOfDay=$(echo $hourOfDay | sed 's/^0*//')
  
  log "Hour of day ${hourOfDay} and idleTimeSec ${idleTimeSec}"
  if [[ $hourOfDay -ge 20 ]] && [[ $idleTimeSec -lt 300 ]]; then
    emoji=":night_with_stars:"
    meetingDetail="2nd Shift"
  fi
  
else
  emoji=$(get_emoji "$meetingDetail")
fi

# echo "meetingDetail: ${meetingDetail}"
# echo "emoji: ${emoji}"

call_result=$(curl -s -X POST -F "token=${extoleSlackApiKey}" -F "profile={\"status_text\":\"${meetingDetail}\",\"status_emoji\":\"${emoji}\"}" "https://slack.com/api/users.profile.set")
echo ${call_result}
