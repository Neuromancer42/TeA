#include <stdio.h>
int main(int argc, char** argv) {
    FILE* fptr = fopen("peek.log", "a");
    fprintf(fptr, "%s\n", argv[0]);
    fclose(fptr);
    return argv[1][0];
}