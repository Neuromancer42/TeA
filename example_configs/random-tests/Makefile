CLANG := clang
instrumented: instrumented.ll instr_funcs.c
	${CLANG} $+ -o $@

TEST_IDS := test0 test1 test2 test3 test4 test5 test6 test7 test8 test9
.PHONY: test $(TEST_IDS)

test: $(TEST_IDS)

$(TEST_IDS): test%: instrumented
	./instrumented || true
	mv tea.log tea.$@.log