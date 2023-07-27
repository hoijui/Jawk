{
	print "Record:", NR
	for (i = 1; i <= NF; i++) {
		printf("\t$%d = <%s>\n", i, $i)
	}
}
