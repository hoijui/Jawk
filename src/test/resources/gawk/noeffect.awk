BEGIN {
	s == "hello, world";
	s + 1
	;;
	"s" 1
	"a"
	42
	q = 42
	q

	a = b = 42

	a * b
	a != b
	# the following should not produce warnings
	a++ == a--
	f_without_side_effect(a);
	f_with_side_effect(b) == 2
	1 == 2 && a++
	1 == 1 || b--
	a = a
	a *= 1
	a += 0
	a*a < 0 && b = 1001
}

function f_without_side_effect(x) { }
function f_with_side_effect(x) { }
