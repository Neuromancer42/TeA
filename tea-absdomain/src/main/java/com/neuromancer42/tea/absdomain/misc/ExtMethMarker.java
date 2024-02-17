package com.neuromancer42.tea.absdomain.misc;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeDom;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeRel;
import com.neuromancer42.tea.commons.analyses.annotations.ProduceRel;
import com.neuromancer42.tea.commons.analyses.annotations.TeAAnalysis;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;

import java.nio.file.Path;
import java.util.Map;

@TeAAnalysis(name = "ExtMethMarker")
public class ExtMethMarker extends AbstractAnalysis {

    public static final String name = "ExtMethMarker";
    public final Path workPath;

    public ExtMethMarker(Path path) {
        workPath = path;
    }

    @ConsumeDom(description = "all functions")
    public ProgramDom domM;

    @ConsumeDom(description = "function arity")
    public ProgramDom domZ;

    @ConsumeRel(doms = {"M"}, description = "marked external functions")
    public ProgramRel relExtMeth;

    public void run(Map<String, ProgramDom> inputDoms, Map<String, ProgramRel> inputRels) {

        ProgramDom domM = inputDoms.get("M");
        ProgramDom domZ = inputDoms.get("Z");
        ProgramRel relRetInput = new ProgramRel("retInput", domM);
        ProgramRel relArgInput = new ProgramRel("argInput", domM, domZ);
        ProgramRel[] genRels = new ProgramRel[]{relRetInput, relArgInput};
        for (var rel : genRels) {
            rel.init();
        }

        ProgramRel relExtMeth = inputRels.get("ExtMeth");
        relExtMeth.load();
        relPhase();
        relExtMeth.close();
        for (var rel: genRels) {
            rel.save(getOutDir());
            rel.close();
        }
    }

    @Override
    protected void domPhase() {
        // no new domain generated
    }

    @Override
    protected void relPhase() {
        for (Object[] tuple : relExtMeth.getValTuples()) {
            String name = (String) tuple[0];
            deprecatedMark(name);
            markSourceSink(name);
            markSpecialMeth(name);
        }
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }


    @ProduceRel(doms={"M", "Z"})
    ProgramRel relArgSrcVal;
    
    @ProduceRel(doms={"M", "Z"})
    ProgramRel relArgSrcArr;

    @ProduceRel(doms = {"M", "Z"})
    ProgramRel relArgSrcArrVA;

    @ProduceRel(doms={"M", "Z"})
    ProgramRel relArgDst;

    @ProduceRel(doms = {"M", "Z"})
    ProgramRel relArgDstAlloc;

    @ProduceRel(doms = {"M", "Z"})
    ProgramRel relArgDstVA;

    @ProduceRel(doms = {"M", "Z"})
    ProgramRel relArgBuf;

    @ProduceRel(doms = {"M", "Z"})
    ProgramRel relArgBufVA;

    @ProduceRel(doms = {"M", "Z"})
    ProgramRel relArgStructPtr;

    @ProduceRel(doms={"M", "Z"})
    ProgramRel relArgSize;

    @ProduceRel(doms={"M", "Z"})
    ProgramRel relArgSkip;


    @ProduceRel(doms = {"M"})
    ProgramRel ones; // Const

    @ProduceRel(doms = {"M"})
    ProgramRel int_v; // Const

    @ProduceRel(doms = {"M"})
    ProgramRel relRetTaintInput;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetSizeArg;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetSrcArg;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetTopWithSrcTaint;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetDstArg;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetBufArg;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetAllocConst;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetAllocBuf;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetAllocDst;

    @ProduceRel(doms = {"M"})
    ProgramRel relRetAllocStruct;

    private void processSourceSink(ProgramRel ret_typ, String m, ProgramRel... arg_typs) {
        for (int i = 0; i < arg_typs.length; ++i) {
            if (arg_typs[i] != null) {
//                if (arg_typs[i].getDoms().length != 2)
//                    Messages.fatal("ProgramRel %s: arg rel wrong dimension", arg_typs[i].getName());
                arg_typs[i].add(m, Integer.toString(i));
            }
        }
        if (ret_typ != null) {
//            if (ret_typ.getDoms().length != 1)
//                Messages.fatal("ProgramRel %s: ret rel wrong dimension", ret_typ.getName());
            ret_typ.add(m);
        }
    }

    private void markSourceSink(String m) {
        switch (m) {
//        (* Copy *)
            case "memcpy" -> processSourceSink(relRetDstArg, m, relArgDst, relArgSrcArr, relArgSize);
            case "memmove" -> processSourceSink(relRetDstArg, m, relArgDst, relArgSrcArr, relArgSize);
            case "strcpy" -> processSourceSink(relRetDstArg, m, relArgDst, relArgSrcArr);
            case "strncpy" -> processSourceSink(relRetDstArg, m, relArgDst, relArgSrcArr, relArgSize);
            case "strxfrm" -> processSourceSink(relRetTopWithSrcTaint, m, relArgDst, relArgSrcArr, relArgSize);
//        (* Concatenation *)
            case "strcat" -> processSourceSink(relRetDstArg, m, relArgDst, relArgSrcArr);
//        (* XXX *)
            case "strncat" -> processSourceSink(relRetDstArg, m, relArgDst, relArgSrcArr, relArgSize);
//        (* Comparison *)
//        (* XXX *)
            case "memcmp" -> processSourceSink(ones, m, relArgSrcArr, relArgSrcArr, relArgSize);
            case "strcmp" -> processSourceSink(ones, m, relArgSkip, relArgSkip);
            case "strcoll" -> processSourceSink(ones, m, relArgSkip, relArgSkip);
            case "strncmp" -> processSourceSink(ones, m, relArgSkip, relArgSkip, relArgSkip);
//        (* Searching *)
            case "memchr" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip, relArgSkip);
            case "memrchr" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip, relArgSkip);
            case "rawmemchr" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip);
            case "strchr" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip);
            case "strcspn" -> processSourceSink(int_v, m, relArgSrcArr, relArgSkip, relArgSkip);
//        (* Unlike strchr, this returns the offset of occuring substring *)
            case "strpbrk" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip);
            case "strrchr" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip);
            case "strspn" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip);
            case "strstr" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip);
            case "strtok" -> processSourceSink(relRetSrcArg, m, relArgSrcVal, relArgSkip, relArgSkip);
            case "strtok_r" -> processSourceSink(relRetSrcArg, m, relArgSrcArr, relArgSkip, relArgSkip);
            case "wcrtomb" -> processSourceSink(relRetTopWithSrcTaint, m, relArgDst, relArgSrcArr, relArgSkip);
            case "mbrtowc" -> processSourceSink(relRetTopWithSrcTaint, m, relArgDst, relArgSrcArr, relArgSkip);
//        (* Others *)
//        (* FIXME: Do not assign v_src to the 1st arg. Do assign it to *dst
//        * case "memset" -> process(m, DstArg, dst, v_src, Size) *)
            case "strerror" -> processSourceSink(relRetAllocConst, m, relArgSkip);
            case "strlen" -> processSourceSink(int_v, m, relArgSrcArr);
//        (* Character conversion (<ctype.h>) *)
            case "tolower" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "towlower" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "toupper" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
//        (* Mathmatical (<math.h>) *)
            case "log" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "sin" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "tan" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "cos" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "acos" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "asin" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "atan" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "atan2" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "pow" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal, relArgSrcVal);
            case "sqrt" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "abs" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "fabs" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "ceil" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "floor" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "exp" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "expf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "expl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "cosh" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "coshf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "coshl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "sinh" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "sinhf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "sinhl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "log10" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "log10f" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "log10l" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "lgamma" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "lgammaf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "lgammal" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "erf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "erff" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "erfl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "erfc" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "erfcf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "erfcl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "round" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "roundl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "roundf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "lroundl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "lroundf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "llround" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "fmod" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "fmodf" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "fmodl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
//        (* GNU FUNCTION *)
            case "_IO_getc" -> processSourceSink(relRetTaintInput, m, relArgSkip);
            case "__errno_location" -> processSourceSink(relRetAllocConst, m);
            case "socket" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip);
            case "access" -> processSourceSink(ones, m, relArgSkip, relArgSkip);
            case "chown" -> processSourceSink(ones, m, relArgSkip, relArgSkip, relArgSkip);
            case "uname" -> processSourceSink(ones, m, relArgSkip);
            case "mkdir" -> processSourceSink(ones, m, relArgSkip, relArgSkip);
            case "mkfifo" -> processSourceSink(ones, m, relArgSkip, relArgSkip);
            case "setgroups" -> processSourceSink(ones, m, relArgSkip, relArgSkip);
            case "seteuid" -> processSourceSink(ones, m, relArgSkip);
            case "setegid" -> processSourceSink(ones, m, relArgSkip);
            case "getgid" -> processSourceSink(int_v, m);
            case "getegid" -> processSourceSink(int_v, m);
            case "htonl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "htons" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "ntohl" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "ntohs" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcVal);
            case "pipe" -> processSourceSink(int_v, m, relArgSkip);
            case "time" -> processSourceSink(int_v, m, relArgSkip);
            case "ctime" -> {
                processSourceSink(int_v, m, relArgSkip);
                processSourceSink(relRetAllocConst, m, relArgSkip);
            } // duplicate, which one?
            case "drand48" -> processSourceSink(int_v, m);
            case "rand" -> processSourceSink(int_v, m);
            case "random" -> processSourceSink(int_v, m);
            case "cuserid" -> processSourceSink(int_v, m);
            case "getlogin" -> processSourceSink(int_v, m);
            case "getlogin_r" -> processSourceSink(int_v, m, relArgSkip, relArgSkip);
            case "getpid" -> processSourceSink(int_v, m);
            case "stat" -> processSourceSink(ones, m, relArgSkip, relArgStructPtr);
            case "fstat" -> processSourceSink(ones, m, relArgSkip, relArgStructPtr);
            case "lstat" -> processSourceSink(ones, m, relArgSkip, relArgStructPtr);
            case "strdup" -> processSourceSink(relRetAllocDst, m, relArgSrcArr);
            case "xstrdup" -> processSourceSink(relRetAllocDst, m, relArgSrcArr);
            case "xmlStrdup" -> processSourceSink(relRetAllocDst, m, relArgSrcArr);
            case "g_strdup" -> processSourceSink(relRetAllocDst, m, relArgSrcArr);
            case "waitpid" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip);
            case "getrlimit" -> processSourceSink(int_v, m, relArgSkip, relArgSkip);
            case "pthread_create" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip, relArgSkip);
            case "pthread_getspecific" -> processSourceSink(int_v, m, relArgSkip, relArgSkip);
            case "re_match" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip, relArgSkip, relArgSkip);
            case "re_search" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip, relArgSkip, relArgSkip);
            case "setsockopt" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip, relArgSkip);
            case "system" -> processSourceSink(int_v, m, relArgSkip);
            case "setlocale" -> processSourceSink(int_v, m, relArgSkip, relArgSkip);
//        (* Some int_v can be modified to 'ones' *)
//        (* Linux File IO *)
            case "fopen" -> processSourceSink(int_v, m, relArgSkip, relArgSkip);
            case "lseek" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip);
            case "ftell" -> processSourceSink(int_v, m, relArgSkip);
            case "pclose" -> processSourceSink(int_v, m, relArgSkip);
//            case "_IO_getc" -> process(m, tainted_v, Skip ); // duplicate
            case "getchar" -> processSourceSink(relRetTaintInput, m);
            case "read" -> processSourceSink(relRetSizeArg, m, relArgSkip, relArgBuf, relArgSize);
            case "fread" -> processSourceSink(relRetSizeArg, m, relArgBuf, relArgSkip, relArgSize, relArgSkip);
            case "write" -> processSourceSink(relRetSizeArg, m, relArgSkip, relArgSrcArr, relArgSize);
            case "fwrite" -> processSourceSink(relRetSizeArg, m, relArgSrcArr, relArgSkip, relArgSize, relArgSkip);
            case "recv" -> processSourceSink(relRetSizeArg, m, relArgSkip, relArgBuf, relArgSize, relArgSkip);
            case "send" -> processSourceSink(relRetSizeArg, m, relArgSkip, relArgSrcArr, relArgSize, relArgSkip);
            case "nl_langinfo" -> processSourceSink(int_v, m, relArgSkip);
            case "readlink" -> processSourceSink(int_v, m, relArgSrcArr, relArgDst, relArgSize);
            case "open" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip);
            case "close" -> processSourceSink(int_v, m, relArgSkip);
            case "unlink" -> processSourceSink(int_v, m, relArgSkip);
            case "select" -> processSourceSink(int_v, m, relArgSkip, relArgSkip, relArgSkip, relArgSkip, relArgSkip);
            case "getenv" -> processSourceSink(relRetAllocBuf, m, relArgSkip);
//        (* etc *)
            case "scanf" -> processSourceSink(int_v, m, relArgSkip, relArgBufVA);
            case "sscanf" -> processSourceSink(int_v, m, relArgSrcArr, relArgSkip, relArgDstVA);
            case "fgets" -> processSourceSink(relRetBufArg, m, relArgBuf, relArgSize, relArgSkip);
            case "fgetc" -> processSourceSink(relRetTaintInput, m, relArgSkip);
            case "sprintf" -> processSourceSink(int_v, m, relArgDst, relArgSkip, relArgSrcArrVA);
            case "snprintf" -> processSourceSink(int_v, m, relArgDst, relArgSize, relArgSkip, relArgSrcArrVA);
            case "vsnprintf" -> processSourceSink(int_v, m, relArgDst, relArgSize, relArgSkip, relArgSrcArrVA);
            case "asprintf" -> processSourceSink(int_v, m, relArgDstAlloc, relArgSkip, relArgSrcArrVA);
            case "vasprintf" -> processSourceSink(int_v, m, relArgDstAlloc, relArgSkip, relArgSrcArrVA);
            case "atoi" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr);
            case "atof" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr);
            case "atol" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr);
            case "strtod" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr, relArgSkip);
            case "strtol" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr, relArgSkip, relArgSkip);
            case "strtoul" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr, relArgSkip, relArgSkip);
            case "strtoimax" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr, relArgSkip, relArgSkip);
            case "strtoumax" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr, relArgSkip, relArgSkip);
            case "fork" -> processSourceSink(int_v, m);
            case "gettext" -> processSourceSink(relRetAllocConst, m, relArgSkip);
            case "ngettext" -> processSourceSink(relRetAllocConst, m, relArgSkip, relArgSkip, relArgSkip);
            case "dgettext" -> processSourceSink(relRetAllocConst, m, relArgSkip, relArgSkip);
            case "dcgettext" -> processSourceSink(relRetAllocConst, m, relArgSkip, relArgSkip, relArgSkip);
            case "mktime" -> processSourceSink(int_v, m, relArgSkip);
            case "localtime" -> processSourceSink(relRetAllocStruct, m, relArgSkip);
            case "gmtime" -> processSourceSink(relRetAllocStruct, m, relArgSrcVal);
            case "timegm" -> processSourceSink(relRetTopWithSrcTaint, m, relArgSrcArr);
        }
    }


    @ProduceRel(name="strcpy", doms={"M"})
    ProgramRel relstrcpy;
    @ProduceRel(name="strcat", doms={"M"})
    ProgramRel relstrcat;
    @ProduceRel(name="strncpy", doms={"M"})
    ProgramRel relstrncpy;
    @ProduceRel(name="memcpy", doms={"M"})
    ProgramRel relmemcpy;
    @ProduceRel(name="memmove", doms={"M"})
    ProgramRel relmemmove;
    @ProduceRel(name="strlen", doms={"M"})
    ProgramRel relstrlen;
    @ProduceRel(name="fgets", doms={"M"})
    ProgramRel relfgets;
    @ProduceRel(name="sprintf", doms={"M"})
    ProgramRel relsprintf;
    @ProduceRel(name="scanf", doms={"M"})
    ProgramRel relscanf;
    @ProduceRel(name="getenv", doms={"M"})
    ProgramRel relgetenv;
    @ProduceRel(name="strdup", doms={"M"})
    ProgramRel relstrdup;
    @ProduceRel(name="gettext", doms={"M"})
    ProgramRel relgettext;
    @ProduceRel(name="getpwent", doms={"M"})
    ProgramRel relgetpwent;
    @ProduceRel(name="strchr", doms={"M"})
    ProgramRel relstrchr;
    @ProduceRel(name="strrchr", doms={"M"})
    ProgramRel relstrrchr;

    private void markSpecialMeth(String m) {
        switch (m) {
            case "strcpy" -> relstrcpy.add(m);
            case "strcat" -> relstrcat.add(m);
            case "strncpy" -> relstrncpy.add(m);
            case "memcpy" -> relmemcpy.add(m);
            case "memmove" -> relmemmove.add(m);
            case "strlen" -> relstrlen.add(m);
            case "fgets" -> relfgets.add(m);
            case "sprintf" -> relsprintf.add(m);
            case "scanf" -> relscanf.add(m);
            case "getenv" -> relgetenv.add(m);
            case "strdup" -> relstrdup.add(m);
            case "gettext" -> relgettext.add(m);
            case "getpwent" -> relgetpwent.add(m);
            case "strchr" -> relstrchr.add(m);
            case "strrchr" -> relstrrchr.add(m);
        }
    }

    @ProduceRel(name = "retInput", doms = {"M"}, description = "input by function retval")
    public ProgramRel relRetInput;

    @ProduceRel(name = "argInput", doms = {"M", "Z"}, description = "input by function argument")
    public ProgramRel relArgInput;

    @ProduceRel(name = "mallocFunc", doms = {"M"})
    public ProgramRel relMallocFunc;
    @ProduceRel(name = "callocFunc", doms = {"M"})
    public ProgramRel relCallocFunc;
    @ProduceRel(name = "reallocFunc", doms = {"M"})
    public ProgramRel relReallocFunc;
    @ProduceRel(name = "freeFunc", doms = {"M"})
    public ProgramRel relFreeFunc;
    @ProduceRel(name = "memcpyFunc", doms = {"M"})
    public ProgramRel relMemcpyFunc;
    @ProduceRel(name = "memmoveFunc", doms = {"M"})
    public ProgramRel relMemmoveFunc;
    @ProduceRel(name = "memsetFunc", doms = {"M"})
    public ProgramRel relMemsetFunc;
    @ProduceRel(name = "memchrFunc", doms = {"M"})
    public ProgramRel relMemchrFunc;

    @ProduceRel(name = "allocaFunc", doms = {"M"})
    public ProgramRel relAllocaFunc;

    //    @ProduceRel(name = "strlenFunc", doms = {"M"})
//    public ProgramRel relStrlenFunc;
    @ProduceRel(name = "cmpFunc", doms = {"M"})
    public ProgramRel relCmpFunc;
    //    @ProduceRel(name = "strncmpFunc", doms = {"M"})
//    public ProgramRel relStrncmpFunc;
    @ProduceRel(name = "strcpyFunc", doms = {"M"})
    public ProgramRel relStrcpyFunc;
    @ProduceRel(name = "strncpyFunc", doms = {"M"})
    public ProgramRel relStrncpyFunc;
    @ProduceRel(name = "strcatFunc", doms = {"M"})
    public ProgramRel relStrcatFunc;
    @ProduceRel(name = "strncatFunc", doms = {"M"})
    public ProgramRel relStrncatFunc;
    @ProduceRel(name = "strchrFunc", doms = {"M"})
    public ProgramRel relStrchrFunc;
    @ProduceRel(name = "strrchrFunc", doms = {"M"})
    public ProgramRel relStrrchrFunc;
    @ProduceRel(name = "strstrFunc", doms = {"M"})
    public ProgramRel relStrstrFunc;
    @ProduceRel(name = "strspnFunc", doms = {"M"})
    public ProgramRel relStrspnFunc;
    @ProduceRel(name = "strcspnFunc", doms = {"M"})
    public ProgramRel relStrcspnFunc;
    @ProduceRel(name = "strpbrkFunc", doms = {"M"})
    public ProgramRel relStrpbrkFunc;
    @ProduceRel(name = "strtokFunc", doms = {"M"})
    public ProgramRel relStrtokFunc;

    private void deprecatedMark(String name) {
        if (name.startsWith("llvm.memcpy")) {
            relMemcpyFunc.add(name);
        } else if (name.startsWith("llvm.memmove")) {
            relMemmoveFunc.add(name);
        } else if (name.startsWith("llvm.memset")) {
            relMemsetFunc.add(name);
        } else if (name.contains("strcpy")) {
            relStrcpyFunc.add(name);
        } else if (name.contains("strncpy")) {
            relStrncpyFunc.add(name);
        } else if (name.contains("strcat")) {
            relStrcatFunc.add(name);
        } else if (name.contains("strncat")) {
            relStrncatFunc.add(name);
        } else {
            switch (name) {
                case "rand", "atoi", "strlen" -> relRetInput.add(name);
                case "scanf" -> {
                    for (String z : domZ) {
                        if (!z.equals("0")) {
                            relArgInput.add(name, z);
                        }
                    }
                }
                case "malloc" -> relMallocFunc.add(name);
                case "calloc" -> relCallocFunc.add(name);
                case "realloc" -> relReallocFunc.add(name);
                case "free" -> relFreeFunc.add(name);
                case "memcpy" -> relMemcpyFunc.add(name);
                case "memmove" -> relMemmoveFunc.add(name);
                case "memset" -> relMemsetFunc.add(name);
                case "memchr" -> relMemchrFunc.add(name);
                case "alloca" -> relAllocaFunc.add(name);

                case "memcmp", "strcmp", "strncmp" -> relCmpFunc.add(name); // Note: skip locale settings
                case "strchr" -> relStrchrFunc.add(name);
                case "strrchr" -> relStrrchrFunc.add(name);
                case "strstr" -> relStrstrFunc.add(name);
                case "strspn" -> relStrspnFunc.add(name);
                case "strcspn" -> relStrcspnFunc.add(name);
                case "strpbrk" -> relStrpbrkFunc.add(name);
                case "strtok" -> relStrtokFunc.add(name);
            }
        }
    }
}
