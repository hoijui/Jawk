# Invoker can customize sort command if necessary.
BEGIN {
	if (!SORT) SORT = "LC_ALL=C sort"
}

# Overdocumented Test Case for FUNCTAB

@namespace "ns"

ns1 = 1				# ns::ns1
ns::ns2 = 2			# ns::ns2
awk::defined_in_ns3 = 3		# defined_in_ns3
@namespace "awk"
awkspace4 = 4			# awkspace4
awk::awkspace5 = 5		# awkspace5

BEGIN {
	list = "awk::defined_in_ns3 awk::awkspace5 ns1 ns::ns1 ns::ns2 awkspace4 awkspace5" 	# list
	n = split(list, test)								# n, test
	for (i = 1; i <= n; i++) {							# i
		var = test[i]								# var
		sub(/awk::/, "", var)	# no 'awk::' in SYMTAB or SYMTAB

		yesno = (test[i] in FUNCTAB) ? "Yes" : "No "				# yesno
		printf("%s %s in FUNCTAB\n", yesno, test[i])
		yesno = (test[i] in PROCINFO["identifiers"]) ? "Yes" : "No "
		printf("%s %s in PROCINFO[\"identifiers\"]\n", yesno, test[i])

		yesno = (var in SYMTAB) ? "Yes" : "No "
		printf("%s %s in SYMTAB\n", yesno, var)
		yesno = (var in PROCINFO["identifiers"]) ? "Yes" :"No "
		printf("%s %s in PROCINFO[\"identifiers\"]\n", yesno, var)
		printf("\n")
	}
	print "------------------------------"
	for (i in PROCINFO["identifiers"])
		print i | awk::SORT
	close(awk::SORT)

	exit 0
}
