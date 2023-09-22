CLANG := clang
SRC := bc.ll
bc: $(SRC)
	$(CLANG) $+ -o $@

TEST_IDS := test-comp test-add test-subtraction test-multiply test-divide test-array test-vars test-void
.PHONY: test $(TEST_IDS)

test: $(TEST_IDS)

test-comp: bc
    ./bc -q comp.txt || true
    mv tea.log tea.$@.log || touch tea.$@.log

$(TEST_IDS): test-%: bc
	./bc %.txt || true
	mv tea.log tea.$@.log || touch tea.$@.log