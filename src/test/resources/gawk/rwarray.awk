@load "rwarray"

BEGIN {
	while ((getline word) > 0)
		dict[word] = word word

	re_sub = "/typed-regex/"
	dict[re_sub] = @/search me/

	strnum_sub = "strnum-sub"
	split("-2.4", f)
	dict[strnum_sub] = f[1]

	bool_sub = "bool-sub"
	dict[bool_sub] = mkbool(1)

	dict["x"] = "x"

	dict["42"] = 42
	dict["42.42"] = 42.42

	n = asorti(dict, dictindices)
	for (i = 1; i <= n; i++)
		printf("dict[%s] = %s\n", dictindices[i], dict[dictindices[i]]) > "orig.out"
	close("orig.out");

	ret = writea("orig.bin", dict)
	printf "writea() returned %d, expecting 1\n", ret

	ret = reada("orig.bin", dict)
	printf "reada() returned %d, expecting 1\n", ret

	n = asorti(dict, dictindices)
	for (i = 1; i <= n; i++)
		printf("dict[%s] = %s\n", dictindices[i], dict[dictindices[i]]) > "new.out"
	close("new.out");

	os = ""
	if (ENVIRON["AWKLIBPATH"] == "sys$disk:[-]") {
		os = "VMS"
		# return status from system() on VMS can not be used here
	}
	if (os != "VMS") {
		ret = system("cmp orig.out new.out")

		if (ret == 0)
			print "old and new are equal - GOOD"
		else
			print "old and new are not equal - BAD"

		if (ret == 0 && !("KEEPIT" in ENVIRON))
			system("rm -f orig.bin orig.out new.out")
	}

	if (typeof(dict[re_sub]) != "regexp")
		printf("dict[\"%s\"] should be regexp, is %s\n",
			re_sub, typeof(dict[re_sub]));

	if (typeof(dict[strnum_sub]) != "strnum")
		printf("dict[\"%s\"] should be strnum, is %s\n",
			strnum_sub, typeof(dict[strnum_sub]));

	if (typeof(dict[bool_sub]) != "number|bool")
		printf("dict[\"%s\"] should be number|bool, is %s\n",
			bool_sub, typeof(dict[bool_sub]));

	if ((dict[bool_sub] "") != "1")
		printf("dict[\"%s\"] should be 1, is %s\n",
			bool_sub, dict[bool_sub]);
}
