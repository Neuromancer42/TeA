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

void swap(int Array[], int pos0, int pos1){
    int tmp = Array[pos0];
    Array[pos0] = Array[pos1];
    Array[pos1] = tmp;
}

int main(){
    int a[3];
    a[0] = 0;
    if(a[0] < a[2]){
        a[0] = add(a[0], neg(a[2]));
        swap(a, 0, 2);
    }
    if(a[0] < a[2]){
        true_error(59);
    }
    else{
        false_error(62);
    }
    return 0;
}