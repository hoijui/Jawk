BEGIN {
	for (i in SYMTAB)
		print i | "sort"
	close("sort")
	print "----"
	SYMTAB["foofoo"]["q"] = "q"
}
