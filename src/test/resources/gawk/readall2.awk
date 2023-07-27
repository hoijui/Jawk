function printarray(n, x,   i) {
	for (i in x) {
		if (isarray(x[i]))
			printarray((n "[" i "]"), x[i])
		else
			printf "%s[%s] = %s\n", n, i, x[i]
	}
}

BEGIN {
	split("", f)
	split("a b c", g)
	print readall(ifile)
	print x, y, z
	print guide::answer
	print f[1]
	print g[1]
	print zebra[0]
	printarray("zebra", zebra)
	print typeof(m)
	printarray("m", m)
	for (i in m)
		print i, m[i]
}
