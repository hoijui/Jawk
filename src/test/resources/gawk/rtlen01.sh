#! /bin/sh

AWK=${AWK:-../gawk}

$AWK 'BEGIN {printf "0"; exit}' | $AWK 'BEGIN {RS=""}; {print length(RT)}'
$AWK 'BEGIN {printf "0\n"; exit}' | $AWK 'BEGIN {RS=""}; {print length(RT)}'
$AWK 'BEGIN {printf "0\n\n"; exit}' | $AWK 'BEGIN {RS=""}; {print length(RT)}'

