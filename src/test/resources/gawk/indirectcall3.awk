function okay(f1, f2, arg)
{
    return @f1(arg)
}

function not_so_hot(f1, f2, arg)
{
    return @f1(arg, @f2(arg)) # line 8: error here
}

function workaround(f1, f2, arg,
                     tmp)
{
   tmp = @f2(arg)
   return @f1(arg, tmp)
}
