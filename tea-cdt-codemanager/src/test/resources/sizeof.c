typedef struct {
    int a;
    int b;
} Pair;

int baz(char a[]) {
    return sizeof a;
}

typedef unsigned int uint;
typedef Pair* PairPtr;
int main() {
    int a = sizeof(long int) + sizeof(Pair) + sizeof(PairPtr) + sizeof(uint);
    Pair foo;
    int b = sizeof(foo);
    int c = sizeof "hello\0world\0";
    char * bar = "hello";
    int d = sizeof bar;
    int e = sizeof 0xEL;
    int f = sizeof 'f';
    char cc[] = "g";
    int g = sizeof(cc);
    int dd[10][10];
    int h = sizeof(dd[0]);
    return h;
}