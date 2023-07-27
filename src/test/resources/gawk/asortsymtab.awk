BEGIN {
	asort(SYMTAB, arr)
	for (idx in arr) {
		print idx
	}
	asort(FUNCTAB, arr)
	for (idx in arr) {
		print idx
	}
}
