#include <stdio.h>
#include "func.h"

int neg(int a){
    return ~a + 1;
}

int add(int a, int b){
    return a + b;
}

int sub(int a, int b){
    return add(a, neg(b));
}

int mul(int a, int b){
    int ans = 0;
    if(b < 0){
        for(int i = 0; i < neg(b); ++i) ans = add(ans, a);
        return neg(ans);
    }
    else{
        for(int i = 0; i < b; ++i) ans = add(ans, a);
        return ans;
    }
}

int div(int a, int b){
    if(b == 0){
        true_error(30);
        return 0x7fffffff;
    }
    return a / b;
}

int pow(int a, int b){
    if(b < 0){
        true_error(38);
        return 0;
    }
    if(b == 0) return 1;
    return mul(a, pow(a, sub(b, 1)));
}

int main(){
    int a = 100;
    int b = 10;
    a = sub(a, a);
    b = mul(a, b);
    if(a > b){
        true_error(51);
    }
    else{
        false_error(54);
    }
    return 0;
}