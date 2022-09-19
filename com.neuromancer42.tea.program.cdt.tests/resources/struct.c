typedef struct {
    int x, y;
} TPos;

int main() {
    TPos a = { .x = 1, .y = 2};
    int b = a.x;
    TPos * c = &a;
    c->y = b;
    return 0;
}