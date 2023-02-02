typedef int * IntPtr;

typedef struct IntList {
    IntPtr this;
    struct IntList * next;
} IntList;

int main() {
    int a = 1;
    IntList foo;
    foo.this = &a;
    IntList bar;
    bar.this = &a;
    foo.next = &bar;
    struct {int this; struct IntList* next;} baz;
    return 0;
}