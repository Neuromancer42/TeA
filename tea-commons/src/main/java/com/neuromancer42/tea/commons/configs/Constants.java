package com.neuromancer42.tea.commons.configs;

import java.util.List;

public class Constants {
    public static final int BDD_VERBOSE = 1;

    private Constants() {}

    public static final String ENV_DEBUG = "TEA_DEBUG";

    public static final String OPT_SRC = "tea.source";
    public static final String OPT_SRC_CMD = "tea.source.cmd";

    public static final String OPT_SQZ = "squeeze";
    public static final String OPT_HOST = "host";
    public static final String OPT_PORT = "port";
    public static final String OPT_WORK_DIR = "workdir";
    public static final String OPT_BUILD_DIR = "builddir";
    public static final String DEFAULT_ROOT_DIR = "test-out";

    public static final String NAME_CORE = "core";
    public static final String NAME_PROJ = "project";

    public static final String MSG_FAIL = "FAIL";
    public static final String MSG_SUCC = "SUCCESS";

    public static final String LABEL_IF = "if";
    public static final String LABEL_THEN = "then";
    public static final String LABEL_ELSE = "else";
    public static final String LABEL_PHI = "phi";
    public static final String LABEL_BREAK = "break";
    public static final String LABEL_CONT = "continue";

    public static final String OP_INCR = "++";
    public static final String OP_DECR = "--";
    public static final String OP_ID = "0+";
    public static final String OP_NEG = "0-";
    public static final String OP_NOT = "!";

    public static final String OP_ADD = "+";
    public static final String OP_SUB = "-";
    public static final String OP_MUL = "*";
    public static final String OP_DIV = "div";
    public static final String OP_REM = "mod";
    public static final String OP_AND = "&&";
    public static final String OP_OR = "||";
    public static final String OP_EQ = "==";
    public static final String OP_NE = "!=";
    public static final String OP_LT = "<";
    public static final String OP_LE = "<=";
    public static final String OP_BIT = "bit";

    public static final String UNKNOWN = "unknown";
    public static final String NULL = "nullptr";

    public static final String TYPE_INT = "int";
    public static final String TYPE_CHAR = "char";
    public static final String TYPE_VOID = "void";

    public static final String PREFIX_DUMMY = "dummy-";

    public static final int WIDTH_VOID = 0;
    public static final int WIDTH_CHAR = 1;
    public static final int WIDTH_SHORT = 2;
    public static final int WIDTH_INT = 4;
    public static final int WIDTH_LONG = 8;
    public static final int WIDTH_LONG_LONG = 16;
    public static final int WIDTH_FLOAT = 4;
    public static final int WIDTH_DOUBLE = 8;
    public static final int WIDTH_LONG_DOUBLE = 16;
    public static final Integer WIDTH_ADDR = 8;
}
