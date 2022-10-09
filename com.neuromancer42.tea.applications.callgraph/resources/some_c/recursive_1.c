// quick sort from dsa
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

int SelectPivot(int left, int right){
    return (left + right) / 2;
}

void swap(int Array[], int pos0, int pos1){
    int tmp = Array[pos0];
    Array[pos0] = Array[pos1];
    Array[pos1] = tmp;
}

int Partition(int Array[], int left, int right){
    int l = left;
    int r = right;
    int tmp = Array[r];
    while(l != r){
        while(Array[l] <= tmp && r > l) l++;
        if(l < r){
            Array[r] = Array[l];
            r--;
        }
        while(Array[r] >= tmp && r > l) r--;
        if(l < r){
            Array[l] = Array[r];
            l++;
        }
    }
    Array[l] = tmp;
    return l;
}

void QuickSort(int Array[], int left, int right){
    if(right < left) return;
    int pivot = SelectPivot(left, right);
    swap(Array, pivot, right);
    pivot = Partition(Array, left, right);
    QuickSort(Array, left, pivot - 1);
    QuickSort(Array, pivot + 1, right);
}

int main(){
    int a[10] = {1, 4, 2, 8, 5, 7, 3, 6, 9, 0};
    QuickSort(a, 0, 9);
    int x = pow(a[0], a[5]);
    int y = pow(a[9], a[5]);
    if(x < y){
        false_error(90);
    }
    else{
        true_error(93);
    }
    return 0;
}