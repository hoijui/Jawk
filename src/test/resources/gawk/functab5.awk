@load "filefuncs"

BEGIN {
	PROCINFO["sorted_in"] = "sort"
	for (i in FUNCTAB)
		print i "'"
}

function sort(i1, v1, i2, v2)
{
	return i1 == i2 ? 0 : i1 < i2 ? -1 : +1
}
