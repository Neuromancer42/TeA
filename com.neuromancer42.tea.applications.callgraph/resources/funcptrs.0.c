#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int correct(int x) {
    printf("correct\n");
    return x;
}

int faulty(int y) {
    printf("false\n");
    return y;
}

int main(int argc, char* argv[]) {
    int (*call)(int);
    int z, ret;
    z = atoi(argv[1]);
    if (z * z >= 0) {
        call = correct;
    } else {
        call = faulty;
    }
    ret = call(z);
    return ret;
}