CLANG := clang
bc: instrumented.ll instr_funcs.c
	${CLANG} $+ -o $@

TEST_IDS := test-comp test-add test-subtraction test-multiply test-divide test-array test-vars test-void test8 test9
.PHONY: test $(TEST_IDS)

test: $(TEST_IDS)

test-comp: bc
    ./bc -q comp.txt || true
    mv tea.log tea.$@.log

$(TEST_IDS): test-%: bc
	./instrumented %.txt || true
	mv tea.log tea.$@.log