#include <stdlib.h>

int main() {
    int* a = (int *) malloc(10*sizeof(int));
    int* b = (int *) alloca(sizeof(int));
}