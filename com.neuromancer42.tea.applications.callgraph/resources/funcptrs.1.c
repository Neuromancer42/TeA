#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef int (*FuncPtr)(int);

int correct(int x) {
    printf("correct\n");
    return x;
}

int faulty(int y) {
    printf("false\n");
    return y;
}

void setFuncPtr1(FuncPtr *fptr, FuncPtr f) {
    *fptr = f;
}

void setFuncPtr2(FuncPtr **fpptr, FuncPtr f) {
    setFuncPtr1(*fpptr, f);
}

int foo(int x, FuncPtr ** f) {
    if (x == 0) {
        setFuncPtr2(f, faulty);
    } else {
        setFuncPtr2(f, correct);
    }
    return x;
}


int main(int argc, char* argv[]) {
    FuncPtr call;
    FuncPtr * callp = &call;
    int z, ret;
    z = atoi(argv[1]);
    if (z > 0) {
        z = foo(z, &callp);
    } else {
        z = foo(z - 1, &callp);
    }
    ret = call(z);
    return ret;
}