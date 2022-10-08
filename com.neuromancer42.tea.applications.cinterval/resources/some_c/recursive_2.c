// traversal of a bin tree
#include <stdio.h>
#include "func.h"

typedef struct{
    int value;
    node *left;
    node *right;
}node;

int a[5];
int i = 0;

void dfsa(node *root){
    if(!root) return;
    a[i++] = root->value;
    dfsb(root->left);
    dfsb(root->right);
}

void dfsb(node *root){
    if(!root) return;
    a[i++] = root->value;
    dfsa(root->left);
    dfsa(root->right);
}

int main(){
    node *tree;
    tree->value = 0;
    node *left;
    left->value = 1;
    tree->left = left;
    node *right;
    right->value = 2;
    tree->right = right;
    dfsa(tree);
    for(int i = 0; i < 3; ++i){
        if(a[i] != i){
            true_error(40);
        }
        else{
            false_error(43);
        }
    }
    return 0;
}