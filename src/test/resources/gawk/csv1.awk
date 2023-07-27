# BEGIN {
# 	FS = ","
# }

{
	printf(" \t%s\t", $0)
	for (i = 1; i <= NF; i++)
		printf("[%s]", $i)
	print ""
}
