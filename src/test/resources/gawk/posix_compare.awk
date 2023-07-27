function print_str(str,		i, n, chars, result)
{
	n = split(str, chars, "")
	result = ""
	for (i = 1; i <= n; i++) {
		if (chars[i] == "\0")
			result = result "\\0"
		else
			result = result chars[i]
	}

	return result
}

function do_compare(left, comp, right)
{
	if (comp == "<")
		return left < right ? "TRUE" : "FALSE"

	return left > right ? "TRUE" : "FALSE"
}


BEGIN {
	left[1]  = "abc\0z1";  compare[1]  = "<"; right[1]  = "abc\0z2";  expected[1]  = "TRUE"
	left[2]  = "abc\0z2";  compare[2]  = "<"; right[2]  = "abc\0z2";  expected[2]  = "FALSE"
	left[3]  = "abc\0z3";  compare[3]  = "<"; right[3]  = "abc\0z2";  expected[3]  = "FALSE"
	left[4]  = "abc\0z1";  compare[4]  = ">"; right[4]  = "abc\0z2";  expected[4]  = "FALSE"
	left[5]  = "abc\0z2";  compare[5]  = ">"; right[5]  = "abc\0z2";  expected[5]  = "FALSE"
	left[6]  = "abc\0z3";  compare[6]  = ">"; right[6]  = "abc\0z2";  expected[6]  = "TRUE"
	left[7]  = "abc\0z1";  compare[7]  = "<"; right[7]  = "abc\0z21"; expected[7]  = "TRUE"
	left[8]  = "abc\0z2";  compare[8]  = "<"; right[8]  = "abc\0z21"; expected[8]  = "TRUE"
	left[9]  = "abc\0z3";  compare[9]  = "<"; right[9]  = "abc\0z21"; expected[9]  = "FALSE"
	left[10] = "abc\0z11"; compare[10] = ">"; right[10] = "abc\0z2";  expected[10] = "FALSE"
	left[11] = "abc\0z21"; compare[11] = ">"; right[11] = "abc\0z2";  expected[11] = "TRUE"
	left[12] = "abc\0z31"; compare[12] = ">"; right[12] = "abc\0z2";  expected[12] = "TRUE"

	l = 12
	for (i = 1; i <= l; i++) {
		result = do_compare(left[i], compare[i], right[i])
		lstr = print_str(left[i])
		rstr = print_str(right[i])

		printf("\"%s\" %s \"%s\": Expecting %s: Got %s\n",
		       lstr, compare[i], rstr, expected[i], result)
	}
}
