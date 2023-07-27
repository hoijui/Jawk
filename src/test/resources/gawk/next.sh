#!/bin/sh

# next.sh --- test next invoked in various ways

if [ "$AWK" = "" ]
then
	echo $0: You must set AWK >&2
	exit 1
fi

# non-fatal 
$AWK '{next}' /dev/null
$AWK 'function f() { next}; {f()}' /dev/null
# fatal
$AWK 'function f() { next}; BEGIN{f()}'
$AWK 'function f() { next}; {f()}; END{f()}' /dev/null
$AWK 'function f() { next}; BEGINFILE{f()}'
$AWK 'function f() { next}; {f()}; ENDFILE{f()}' /dev/null

exit 0	# for make
