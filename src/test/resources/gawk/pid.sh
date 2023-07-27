#! /bin/sh
AWK=${AWK-../gawk}
exec $AWK -v "ok_pid=$$" -v "ok_ppid=$1" -f pid.awk 2>/dev/null
