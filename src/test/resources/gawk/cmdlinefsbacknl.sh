#! /bin/sh

AWK=${AWK:-../gawk}

$AWK -F '\
a' 'BEGIN { printf("FS=<%s>\n", FS) }'

$AWK -v FS='\
a' 'BEGIN { printf("FS=<%s>\n", FS) }'

echo | $AWK '{ printf("FS=<%s>\n", FS) }' FS='\
a' -
