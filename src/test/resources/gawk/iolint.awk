BEGIN {
	LINT = 1

	# `%.*s' used for input file and for output file
	print "hi" > "f1"
	fflush("f1")
	getline x < "f1"
	print close("f1")
	print close("f1")
	fflush()

	# `%.*s' used for input file and input pipe
	# `%.*s' used for input file and two-way pipe
	# `%.*s' used for input pipe and two-way pipe
	getline data3 < "echo hello"
	"echo hello" |& getline data2
	"echo hello" | getline data

	print data, data2, data3

	print close("echo hello")
	print close("echo hello")
	print close("echo hello")
	fflush()

	# `%.*s' used for input file and output pipe
	getline x < "cat"
	print "foo" | "cat"
	print close("cat")
	print close("cat")
	fflush()

	# unnecessary mixing of `>' and `>>' for file `%.*s'
	print "foo" >  "f2"
	print "bar" >> "f2"
	print close("f2")
	print close("f2")	# -1 expected here
	fflush()

	# `%.*s' used for input pipe and output file
	"echo hello" | getline junk
	print "hello" > "echo hello"
	print close("echo hello")
	print close("echo hello")
	fflush()

	# `%.*s' used for output file and output pipe
	# `%.*s' used for output file and two-way pipe
	# `%.*s' used for output pipe and two-way pipe
	# Not doing |& due to race condition and signals. sigh
	cat = "cat"
	print "/bin/cat \"$@\"" > "cat"
	print "hello" | "cat"
	print close("cat")
	print close("cat")
	fflush()

	# 11/2021: Use a nice trick to avoid race conditions in
	# child processes. Thanks to Miguel Pineiro Jr. <mpj@pineiro.cc>.
	#
	# `%.*s' used for input pipe and output pipe
	pipecmd = "eval $CMD_TO_RUN"

	ENVIRON["CMD_TO_RUN"] = "echo hello"
	pipecmd | getline junk 
	ENVIRON["CMD_TO_RUN"] = "read junk"
	print "hello" | pipecmd

	print close(pipecmd)
	print close(pipecmd)
	fflush()

	# `%.*s' used for output file and output pipe"
	BINMODE = 2
	print "junk" > "cksum"	# cksum is more portable than md5sum
	print "hello" | "cksum"
	print close("cksum")
	print close("cksum")
	fflush()
}
