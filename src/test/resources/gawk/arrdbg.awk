function check(x, exptype, mpfr_exptype,   f) {
	f[x]
	printf "array_f subscript [%s]\n", x
	printf "array_f subscript [%s]\n", x > okfile
	printf "array_f subscript [%s]\n", x > mpfr_okfile
	adump(f, -1)
	printf "    array_func: %s_array_func\n", exptype > okfile
	printf "    array_func: %s_array_func\n", mpfr_exptype > mpfr_okfile
}

BEGIN {
	check(3.0, "cint", "str")
	check(-3, "int", "str")
	check("3.0", "str", "str")
	split(" 3", f, "|")	# create a maybe_num value
	check(f[1], "str", "str")
	check("0", "cint", "str")
	check("-1", "int", "str")
}
