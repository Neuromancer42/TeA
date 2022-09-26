#include "stdio.h"

int main() {
    int a = 0;
    scanf("%d", &a);
    int b;
    if (a > 0)
        b = 10;
    else
        b = -10;
    int c = b + b;
    return c;
}