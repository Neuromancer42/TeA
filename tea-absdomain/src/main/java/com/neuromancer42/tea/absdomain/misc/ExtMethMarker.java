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

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }
}
