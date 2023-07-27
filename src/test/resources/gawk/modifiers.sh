#! /bin/sh

AWK=${AWK:-../gawk}
for modifier in h l L j t z
do
	$AWK -v let=$modifier --posix --lint 'BEGIN { printf "%" let "u\n", 12 }'
done
exit 0
