
BEGIN{

	A[ "any index" ] = 1		# any number

	for ( i in A ) {

		v = A[ i ]

		gensub( /^/, "1", 1, v )

		#gsub( /^dfsdfs/, "1", v )

		#sub( /^/, "1", v )

		if ( typeof( A[ i ] ) == "unknown" )

			print "ERROR: A[ " i " ] == " A[ i ] "'" }

	v = A[ "any index" ]

	print typeof( v )

 }



# GNU Awk 5.1.0, API: 3.0 (GNU MPFR 3.1.5, GNU MP 6.1.2)
# Copyright (C) 1989, 1991-2020 Free Software Foundation.
#
# Windows 10x64
#
# here is the typeof() error reproducing script
# it is actual for: gensub(), gsub() and sub() built-ins
#
# please pay attention that: v = A[ i ] is doesn't matter
# you may apply built-ins at A[ i ] directly with the same
# result
#
# with Respect
#
# Denis Shirokov 					(2021.9.5)




