int main() {
	int a[10] = {1};
	int* b = a;
	b[1] = 2;
	int x = b[2];
	return x;
}