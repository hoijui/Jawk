#! /bin/sh

AWK=${AWK:-../gawk}

$AWK 'BEGIN {printf "0\n\n\n1\n\n\n\n\n2\n\n"; exit}' | $AWK 'BEGIN {RS=""}; {print length(RT)}'
