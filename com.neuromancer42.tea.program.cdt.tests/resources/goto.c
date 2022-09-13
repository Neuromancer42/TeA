int main() {
    int a = 10;
    if (a == 1)
        goto case_1;
    return 0;

    case_1: return 1;
}

void foo(long tmp___1) {
    long n;
    int cNext = 10;
    while (1) {
        while_continue:
        {
            if (tmp___1) {
                n = 1;
                {
                    while (1) {
                      while_continue___1: /* CIL Label */ ;
                      {
                          n ++;
                      }
                      if ((int )cNext != 10) {
                        if (! ((int )cNext != 0)) {
                          goto while_break___1;
                        }
                      } else {
                        goto while_break___1;
                      }
                    }
                    while_break___1: /* CIL Label */ ;
                }
                {
                    foo((long )(- n));
                }
                goto while_continue;
             }
        }
    }
}