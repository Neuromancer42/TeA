typedef struct {
    int x, y;
} TPos;

typedef struct {
    TPos xy;
    int z;
} TTPos;

int main() {
    TPos a = { .x = 1, .y = 2};
    int b = a.x;
    TPos * c = &a;
    c->y = b;
    TTPos az = { .xy.x = 1, .xy.y = 2, .z = 3};
    return 0;
}