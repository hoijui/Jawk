BEGIN {
	print "Initialize strong regex"
	rgx2 = rgx1 = @/[abc]/
	print "Test gsub on strong regex"
	printf("rgx%d = '%s'\ttypeof(rgx%d) = '%s'\n", 1, rgx1, 1, typeof(rgx1))
	printf("rgx%d = '%s'\ttypeof(rgx%d) = '%s'\n", 2, rgx2, 2, typeof(rgx2))
	print "Test gsub() a strong regex"
	gsub(/b/, "e", rgx2)
	printf("rgx%d = '%s'\ttypeof(rgx%d) = '%s'\n", 1, rgx1, 1, typeof(rgx1))
	printf("rgx%d = '%s'\ttypeof(rgx%d) = '%s'\n", 2, rgx2, 2, typeof(rgx2))

	print "Test value not found in regex"
	gsub(/x/, "y", rgx1)	# should not change
	printf("rgx%d = '%s'\ttypeof(rgx%d) = '%s'\n", 1, rgx1, 1, typeof(rgx1))

	print "Test gsub on numbers"
	v2 = v1 = 12345
	printf("v%d = '%s'\ttypeof(v%d) = '%s'\n", 1, v1, 1, typeof(v1))
	printf("v%d = '%s'\ttypeof(v%d) = '%s'\n", 2, v2, 2, typeof(v2))
	gsub(/3/, "x", v2)
	printf("v%d = '%s'\ttypeof(v%d) = '%s'\n", 1, v1, 1, typeof(v1))
	printf("v%d = '%s'\ttypeof(v%d) = '%s'\n", 2, v2, 2, typeof(v2))
	print "Test value not found in number"
	gsub(/9/, "x", v1)
	printf("v%d = '%s'\ttypeof(v%d) = '%s'\n", 1, v1, 1, typeof(v1))

	print "Test gensub on regex"
	a = b = @/abc/
	c = gensub(/b/, "x", "g", a)
	printf("a = @/%s/\ttypeof(a) = '%s'\n", a, typeof(a))
	printf("c = \"%s\"\ttypeof(c) = '%s'\n", c, typeof(c))
	print "Test value not found in regex"
	c = gensub(/q/, "x", "g", b)
	printf("b = @/%s/\ttypeof(b) = '%s'\n", b, typeof(b))
	printf("c = \"%s\"\ttypeof(c) = '%s'\n", c, typeof(c))

	print "Test gensub on numbers"
	a = b = 12345
	c = gensub(/3/, "x", "g", a)
	printf("a = \"%s\"\ttypeof(a) = '%s'\n", a, typeof(a))
	printf("b = \"%s\"\ttypeof(b) = '%s'\n", b, typeof(b))
	printf("c = \"%s\"\ttypeof(c) = '%s'\n", c, typeof(c))
	print "Test value not found in number"
	c = gensub(/9/, "x", "g", b)
	printf("b = \"%s\"\ttypeof(b) = '%s'\n", b, typeof(b))
	printf("c = \"%s\"\ttypeof(c) = '%s'\n", c, typeof(c))
	print typeof(c), c
}
