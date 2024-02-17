package com.neuromancer42.tea.absdomain.tests;

import com.neuromancer42.tea.absdomain.misc.ExtMethMarker;
import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExtMethMarkerTest {
    private static final Path workDirPath = Paths.get("test-out");

    private static void setDom(String dir, Map<String, String> domLocMap, ProgramDom dom, String... elems) {
        dom.init();
        for (String elem : elems)
            dom.add(elem);
        dom.save(dir);
        domLocMap.put(dom.getName(), dom.getLocation());
    }

    private static void setRel(String dir, Map<String, String> relLocMap, ProgramRel rel, Object[]... tuples) {
        rel.init();
        for (Object[] tuple : tuples) {
            rel.add(tuple);
        }
        rel.save(dir);
        rel.close();
        relLocMap.put(rel.getName(), rel.getLocation());
    }

    @Test
    @DisplayName("CMemModel processes func-ptrs coccretly")
    public void extMethMarkerTest() throws IOException {
        Path path = workDirPath.resolve("test-extmethmarker");
        Files.createDirectories(path);
        String dir = path.toAbsolutePath().toString();
        Map<String, String> domLocMap = new LinkedHashMap<>();
        ProgramDom domZ = new ProgramDom("Z");
        setDom(dir, domLocMap, domZ, "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        ProgramDom domM = new ProgramDom("M");
        setDom(dir, domLocMap, domM, test_meths);
        Map<String, String> relLocMap = new LinkedHashMap<>();
        ProgramRel relExtMeth = new ProgramRel("ExtMeth", domM);
        Object[][] test_meth_tuples = new Object[test_meths.length][1];
        for (int i = 0; i < test_meths.length; ++i) {
            test_meth_tuples[i][0] = test_meths[i];
        }
        setRel(dir, relLocMap, relExtMeth, test_meth_tuples);

        ExtMethMarker extmeth = new ExtMethMarker(path);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(extmeth, domLocMap, relLocMap);
        Assertions.assertNotNull(output);
        System.out.print(output.getRight().size());
    }

    private static final String[] test_meths = new String[]{
            "memcpy",
            "memmove",
            "strcpy",
            "strncpy",
            "strxfrm",
            "strcat",
            "strncat",
            "memcmp",
            "strcmp",
            "strcoll",
            "strncmp",
            "memchr",
            "memrchr",
            "rawmemchr",
            "strchr",
            "strcspn",
            "strpbrk",
            "strrchr",
            "strspn",
            "strstr",
            "strtok",
            "strtok_r",
            "wcrtomb",
            "mbrtowc",
            "strerror",
            "strlen",
            "tolower",
            "towlower",
            "toupper",
            "log",
            "sin",
            "tan",
            "cos",
            "acos",
            "asin",
            "atan",
            "atan2",
            "pow",
            "sqrt",
            "abs",
            "fabs",
            "ceil",
            "floor",
            "exp",
            "expf",
            "expl",
            "cosh",
            "coshf",
            "coshl",
            "sinh",
            "sinhf",
            "sinhl",
            "log10",
            "log10f",
            "log10l",
            "lgamma",
            "lgammaf",
            "lgammal",
            "erf",
            "erff",
            "erfl",
            "erfc",
            "erfcf",
            "erfcl",
            "round",
            "roundl",
            "roundf",
            "lroundl",
            "lroundf",
            "llround",
            "fmod",
            "fmodf",
            "fmodl",
            "_IO_getc",
            "__errno_location",
            "socket",
            "access",
            "chown",
            "uname",
            "mkdir",
            "mkfifo",
            "setgroups",
            "seteuid",
            "setegid",
            "getgid",
            "getegid",
            "htonl",
            "htons",
            "ntohl",
            "ntohs",
            "pipe",
            "time",
            "ctime",
            "drand48",
            "rand",
            "random",
            "cuserid",
            "getlogin",
            "getlogin_r",
            "getpid",
            "stat",
            "fstat",
            "lstat",
            "strdup",
            "xstrdup",
            "xmlStrdup",
            "g_strdup",
            "waitpid",
            "getrlimit",
            "pthread_create",
            "pthread_getspecific",
            "re_match",
            "re_search",
            "setsockopt",
            "system",
            "setlocale",
            "fopen",
            "lseek",
            "ftell",
            "pclose",
            "getchar",
            "read",
            "fread",
            "write",
            "fwrite",
            "recv",
            "send",
            "nl_langinfo",
            "readlink",
            "open",
            "close",
            "unlink",
            "select",
            "getenv",
            "scanf",
            "sscanf",
            "fgets",
            "fgetc",
            "sprintf",
            "snprintf",
            "vsnprintf",
            "asprintf",
            "vasprintf",
            "atoi",
            "atof",
            "atol",
            "strtod",
            "strtol",
            "strtoul",
            "strtoimax",
            "strtoumax",
            "fork",
            "gettext",
            "ngettext",
            "dgettext",
            "dcgettext",
            "mktime",
            "localtime",
            "gmtime",
            "timegm"
    };
}
