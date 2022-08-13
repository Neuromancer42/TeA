int plus(int a, int b) {
    int c;
    c = a;
    if (c == 0)
        c = b;
    else
        if (b == 0)
            c = a;
        else
            while (b > 0) {
                c = c + 1;
                b--;
            }
    return c;
}

int main() {
    int x, y, z;
    x = 0;
    y = 1;
    z = plus(x, y);
    return z;
}