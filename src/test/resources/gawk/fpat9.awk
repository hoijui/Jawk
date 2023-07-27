#! /bin/gawk  -f

# Modified version of program from Arthur Schwarz <home@slipbits.com>.

BEGIN {                                         # program constants
         FPAT         = "([^,]*)|(\"([^\"]|\"\")+\")" # CSV field separator
         print "FPAT = ", FPAT;
}
{
        print "------------------------------------------------\n"
        print $0;
        printf("%3d:   \n", NF);
        for (i = 1; i <= NF; i++) {
           if (substr($i, 1, 1) == "\"") {
              len = length($i)		# BUG FIX, was length($1)
              $i = substr($i, 2, len - 2);
	     gsub(/""/, "\"", $i)	# embedded "" --> "
           }
           printf("      <%d: %s>\n", i, $i);
        }
        print " ";
}
