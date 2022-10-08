#include <stdio.h>
#include "func.h"

int main(){
    int a;
    int b;
    int *ptra = &a;
    int *ptrb = &b;
    int **ptraa = &ptra;
    int **ptrbb = &ptrb;
    if(a < b){
        ptrb = &a;
        ptra = &b;
    }
    int c = **ptraa;
    int d = **ptrbb;
    if(c < d){
        true_error(19);
    }
    else{
        false_error(22);
    }
    return 0;
}