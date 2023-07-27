@load "ordchr"

BEGIN {
	o = "ord"		# check stack for indirect call of ext function
	l = "length"		# check bad args for function of 1 argument
	m = "match"		# check bad args for function of 3-4 argument
	s = "systime"		# check bad args for function of 0 arguments

	switch (test) {
	case 0:
		print "indirect, "  @o("A")
		break
	case 1:
		print @l()
		break
	case 2:
		print @l("a", "b")
		break
	case 3:
		print @m(@/foo/)
		break
	case 4:
		print @m(@/foo/, "bar", a, b)
		break
	case 5:
		print @s("xxx")
		break
	}
}
