void foo() {
    return;
}

void bar() {
    return;
}

void baz() {
    return;
}

typedef void (*FuncPtr)();
typedef struct { FuncPtr f; } FuncStruct;

int main() {
    FuncPtr a[2]= { foo };
    a[1] = &bar;
    a[1]();
    FuncStruct b = { baz };
    b.f();
    return 0;
}