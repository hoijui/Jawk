function set_val(old) {
	old[1] = 42
}
BEGIN {
  a[0] = set_val(a[0])
}
