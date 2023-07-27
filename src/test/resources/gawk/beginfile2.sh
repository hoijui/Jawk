#!/bin/sh

# beginfile2.sh --- test BEGINFILE/ENDFILE/getline/nextfile/exit combinations

#AWK="../gawk"
AWKPROG="beginfile2.in"
SCRIPT=`basename $0`

if [ "$AWK" = "" ]
then
        echo $0: You must set AWK >&2
        exit 1
fi

echo "--Test 1a--"
prog=`$AWK ' /#TEST1#/, /#TEST2#/' $AWKPROG`
$AWK "$prog" $AWKPROG
echo "--Test 1b--"
$AWK "$prog" $AWKPROG file/does/not/exist

echo "--Test 2--"
prog=`$AWK ' /#TEST2#/, /#TEST3#/' $AWKPROG`
$AWK "$prog" $AWKPROG file/does/not/exist

echo "--Test 3--"
prog=`$AWK ' /#TEST3#/, /#TEST4#/' $AWKPROG`
$AWK -vsrc=$SCRIPT "$prog" $AWKPROG

echo "--Test 4--"
prog=`$AWK ' /#TEST4#/, /#TEST5#/' $AWKPROG`
$AWK -vsrc=$SCRIPT "$prog" $AWKPROG

echo "--Test 5--"
prog=`$AWK ' /#TEST5#/, /#TEST6#/' $AWKPROG`
$AWK "$prog" $AWKPROG

echo "--Test 6--"
prog=`$AWK ' /#TEST6#/, /#TEST7#/' $AWKPROG`
$AWK "$prog" $AWKPROG

echo "--Test 7--"
prog=`$AWK ' /#TEST7#/, /#TEST8#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

echo "--Test 8--"
prog=`$AWK ' /#TEST8#/, /#TEST9#/' $AWKPROG`
$AWK "$prog" $AWKPROG

echo "--Test 9a--"
prog=`$AWK ' /#TEST9#/, /#TEST10#/' $AWKPROG`
$AWK "$prog" file/does/not/exist $AWKPROG
echo "--Test 9b--"
$AWK -vskip=1 "$prog" file/does/not/exist $AWKPROG

echo "--Test 10--"
prog=`$AWK ' /#TEST10#/, /#TEST11#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

echo "--Test 11--"
prog=`$AWK ' /#TEST11#/, /#TEST12#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

echo "--Test 12--"
prog=`$AWK ' /#TEST12#/, /#TEST13#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

echo "--Test 13--"
prog=`$AWK ' /#TEST13#/, /#TEST14#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

echo "--Test 14--"
prog=`$AWK ' /#TEST14#/, /#TEST15#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

echo "--Test 15--"
prog=`$AWK ' /#TEST15#/, /#TEST16#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

echo "--Test 16--"
prog=`$AWK ' /#TEST16#/, /#TEST17#/' $AWKPROG`
$AWK "$prog" $AWKPROG $SCRIPT

