void assign(int *p, int v) {
    *p = 0;
    do {
        if (v < 0)
            break;
        if (v == 0) continue;
        v = v - 1;
        *p += 1;
    } while (v > 0);
    return;
}

int plus(int a, int b) {
    int c;
    void (*asgn)(int*, int);
    void (**pasgn)(int*, int);
    asgn = &assign;
    pasgn = &asgn;
    (*asgn)(&c, a);
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
    assign(&x, 0);
    y = 1;
    z = plus(x, y);
    return z;
}