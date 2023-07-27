{
    for (i = 1; i <= NF; i++) {
        switch ($i) {
            case "+" : case "-" :
            case "*" : case "/" :
            case "%" : case "^" :
                printf $i; break
            case /[a-z]/ :
                printf $i; break
            case /[0-9]/ : 
                printf $i; break
            case /[ \t]/ :
                printf $i; break
            default :
                print " wrong character " i " th: "  $i
        }
    }
    print ""
}
