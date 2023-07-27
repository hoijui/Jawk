BEGIN {
	exit9 = "echo red; exit 9"
	while ((exit9 | getline x) > 0)
		print x
	printf "close(%s) returned %s\n", exit9, close(exit9)

	# run it again, but don't reap the exit status
	while ((exit9 | getline x) > 0)
		print x

	exit0 = "echo blue; exit 0"
	while ((exit0 | getline x) > 0)
		print x
	# reap status out of order
	printf "close(%s) returned %s\n", exit0, close(exit0)

	# check that we got the correct status from the previously
	# exited process
	printf "close(%s) returned %s\n", exit9, close(exit9)
}
