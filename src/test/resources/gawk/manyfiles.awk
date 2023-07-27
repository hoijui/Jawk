BEGIN { print TEMPDIR }
{ print $2 > (TEMPDIR "/junk-" $1) }
