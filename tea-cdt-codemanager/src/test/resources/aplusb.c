#include "assign.h"

int plus(int a, int b) {
    int c = 0;
    int *d = &c;
    d = &c;
    (*d)++;
    void (*asgn)(int*, int) = assign;
    void (**pasgn)(int*, int) = &asgn;
    pasgn = &asgn;
    (****pasgn)(d, a);
    (asgn)(d,a);
    if (c == 0) {
        int i = 0;
        for (; i >= 0; i++) {
            if (i == b)
                break;
            c++;
        }
    } else
        if (b == 0) {
        } else
            while (b > 0) {
                c = c + 1;
                b--;
            }
    return c;
}

int main() {
    int x, y, z;
    (&*assign)(&x, 0);
    y = 1;
    z = (**&plus)(x, y);
    int c;
    c = 1;
    return z;
}