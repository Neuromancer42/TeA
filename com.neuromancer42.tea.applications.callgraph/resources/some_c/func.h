#include <stdio.h>

void true_error(int line){
    printf("Error in line: %d\n", line);
}

void false_error(int line){
    printf("No error in line: %d\n", line);
}