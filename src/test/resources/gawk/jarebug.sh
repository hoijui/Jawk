#! /bin/sh

awk=$1
prog=$2
infile=$3
out=$4

# GLIBC gives us ja_JP.EUC-JP but Mac OS X uses ja_JP.eucJP

cp $infile $out	# set up default

for locale in ja_JP.EUC-JP ja_JP.eucJP
do
	if locale -a 2>/dev/null | grep $locale > /dev/null
	then
		LANG=$locale
		LC_ALL=$locale
		export LANG LC_ALL
		$awk -f $prog $infile  >$out 2>&1 || echo EXIT CODE: $? >> $out
	fi
done
