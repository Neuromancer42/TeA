void assign(int *p, int v) {
    *p = 0;
    do {
        if (v < 0)
            break;
        if (v == 0) continue;
        v = v - 1;
        *p += 1;
    } while (v > 0);
}