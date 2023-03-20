int main() {
  int indx;
  int arrays[5];
  int old_ary[10];
  int old_count = 10;
  indx = 1;
  {
#line 171
  while (1) {
    while_continue: /* CIL Label */ ;
#line 171
    if (! (indx < old_count)) {
#line 171
      goto while_break;
    }
#line 172
    *(arrays + indx) = *(old_ary + indx);
#line 171
    indx ++;
  }
  while_break: /* CIL Label */ ;
  }
  return 0;
  }