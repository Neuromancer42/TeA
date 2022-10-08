
#include <stdio.h>
#include "func.h"

int rec_num = 0;
int boundary = 100;
int funcB(int x);
int funcC(int x);
int funcA(int x){
    rec_num ++;
    if(rec_num >= boundary) return x;
    return funcB(x) + 1;
}
int funcB(int x){
    rec_num ++;
    if(rec_num >= boundary) return x;
    return funcC(x) + 1;
}
int funcC(int x){
    rec_num ++;
    if(rec_num >= boundary) return x;
    return funcA(x) + 1;
}
int main(){
    int ans = funcA(25) + 75;
    if(ans < 200){
        false_error(27);
    }
    else{
        true_error(30);
    }
    return 0;
}