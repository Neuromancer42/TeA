void assign(int *p, int v) {
    *p = v;
    return;
}

int plus(int a, int b) {
    int c;
    void (*asgn)(int*, int);
    void (**pasgn)(int*, int);
    asgn = &assign;
    pasgn = &asgn;
    (*asgn)(&c, a);
    if (c == 0)
        asgn(&c, b);
    else
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