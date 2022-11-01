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

void setFuncPtr3(FuncPtr **fpptr, FuncPtr *fTrue, FuncPtr * fFalse, int flag) {
    if (flag == 0) {
        setFuncPtr2(fpptr, *fFalse);
    } else {
        setFuncPtr2(fpptr, *fTrue);
    }
}

int foo(int x, FuncPtr ** f) {
    FuncPtr f1 = correct;
    FuncPtr f0 = faulty;
    if (x == 0) {
        setFuncPtr3(f, &f0, &f1, x);
    } else {
        setFuncPtr3(f, &f1, &f0, x);
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