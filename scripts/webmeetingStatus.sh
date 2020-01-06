#!/bin/zsh

usage="Usage: $(basename $0) [-h][-r name@computer:script]
    -f - format of response
    -r - indicates to run the script remotely
    -h - help
"

remote=
while [ $# -gt 0 ]; do
    case "$1" in
        -h|-\?)
            echo "$usage"
            exit 0
        ;;
        -r)
            if [ -z "${2:-}" ]; then
                echo "Error: option $1 expects a parameter" 1>&2
                exit 1
            fi
            remote="$2"
            shift
        ;;
        -*)
             echo "Error: unexpected option: $1" 1>&2
             exit 1
        ;;
        *)
            break
        ;;
    esac
    shift
done

local_meeting_check() {
  meetingCount=0
  webMeetingName=""
  response="{\"meeting\": null}"

  declare -a arr=("zoom.us" "GoToMeeting")
  for processName in "${arr[@]}"
  do
    newMeetingCount=$(ps aux|more | grep "${processName}" | grep -v grep | wc -l)
    meetingCount=$((meetingCount + newMeetingCount))
    webMeetingName="${processName}"
  done

  # Check for Chrome using the Camera.  Chrome always has it open once,
  # so to see a web meeting means Chrome should be listed twice. This is a pretty
  # long running flow.
  declare -a arr=("Google")
  for processName in "${arr[@]}"
  do
    newMeetingCount=$(lsof -n | grep -i "VDC" | grep -i "${processName}" | wc -l)
    if [ "${newMeetingCount}" -gt "1" ]; then
      meetingCount=$((meetingCount + 1))
      webMeetingName="${processName}"
    fi
  done

  if [ "${meetingCount}" -gt "0" ]; then
    osascript -e "display notification \"Currently in a ${webMeetingName} web meeting.\" with title \"In Web Meeting\""
    response="${webMeetingName}"
  else
    response="none"
  fi

  echo ${response}
}

meeting_response="none"
# Check if running locally or remotely.  If running remote ssh otherwise just run
if [ -z "${remote:-}" ]; then
  meeting_response=$(local_meeting_check)
else
  server=$(echo ${remote} | cut -d ":" -f 1)
  command=$(echo ${remote} | cut -d ":" -f 2)
  meeting_response=$(ssh -o ConnectTimeout=1 ${server} ${command} 2>&1)
  if [ $? -ne 0 ]; then
    meeting_response="none"
  fi
fi

## If there is an error response, assumed there is no meeting (off)
if [ "${meeting_response}" = "none" ]; then
  >&2 echo "${meeting_response}"
  exit 1
else
  echo "${meeting_response}"
  exit 0
fi


