int a[2];

int main() {
    int * y, (x) = {0};
    long w = {x + 11, x + 12};
    int b[20] = {x + 1, x + 2};
    int c[20] = {[1] = x + 3};
    int z[][1] = {[1] = {x}};
    x[a] = z[0][0];
    y = 1 + a;
    x = *(1+y);
    return x;
}