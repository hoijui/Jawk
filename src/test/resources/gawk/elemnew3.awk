BEGIN {
	f(a["b"])
	print typeof(a["b"])
}

function f(x)
{
	return x
}
