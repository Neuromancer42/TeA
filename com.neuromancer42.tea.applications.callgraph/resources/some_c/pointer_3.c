#include <stdio.h>
#include "func.h"

int main(){
    int a[10];
    int *ptra[10];
    int **ptraa[10];
    int ***ptraaa[10];
    for(int i = 0; i < 10; ++i){
        a[i] = i;
        ptra[i] = &a[i];
        ptraa[i] = &ptra[i];
        ptraaa[i] = &ptraa[i];
    }
    for(int i = 0; i < 10; ++i){
        ptra[i] = &a[(i + 1) % 10];
    }
    int sum = 0;
    for(int i = 0; i < 10; ++i){
        sum += ***ptraaa[i];
    }
    if(sum != 45){
        for(int i = 1; i < 10; ++i){
            ptraa[i] = &ptra[i];
        }
    }
    for(int i = 0; i < 10; ++i){
        sum += ***ptraaa[i];
    }
    if(sum){
        false_error(31);
    }
    else{
        true_error(34);
    }
    return 0;
}