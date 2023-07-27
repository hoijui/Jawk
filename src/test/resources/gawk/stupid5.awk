BEGIN {
   print typeof(x)
   f(x)
}

function f(x) {
   y = x
   print typeof(x)
}
