#include <stdio.h>
#include "func.h"

int a = 100;

int main(){
    int b = 100;
    int *ptra = &a;
    int *ptrb = &b;
    int aa = (int)ptra;
    int bb = (int)ptrb;
    if(aa == bb){
        true_error(12);
    }
    else{
        false_error(16);
    }
    return 0;
}