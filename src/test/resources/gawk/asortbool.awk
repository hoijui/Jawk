BEGIN {
	a[1] = "foo"
	a[2] = -45
	a[3] = 45
	a[4][1] = 47
	a[5] = mkbool(1)
	a[6] = mkbool(0)

	asort(a, b, "@val_type_asc")

	j = length(b)
	for (i = 1; i <= j; i++) {
		printf("%d, %s: ", i, typeof(b[i]))
		if (isarray(b[i]))
			print b[i][1]
		else
			print b[i]
	}
}
