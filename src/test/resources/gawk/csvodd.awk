# Show the string contents. Make begin, end, CR and LN visible.
function show(str) {
    gsub("\r", "\\r", str)
    gsub("\n", "\\n", str)
    printf("<%s>", str)
}

# Dump the current record
{
    show($0); show(RT); print ""
    for (k=1; k<=NF; k++) show($k); print ""
}

