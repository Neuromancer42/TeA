package com.neuromancer42.tea.codemanager.cdt;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.ValueGraph;
import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.ProduceDom;
import com.neuromancer42.tea.commons.analyses.annotations.ProduceRel;
import com.neuromancer42.tea.commons.analyses.annotations.TeAAnalysis;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.util.IndexMap;
import com.neuromancer42.tea.commons.util.ProcessExecutor;
import com.neuromancer42.tea.commons.util.StringUtil;
import com.neuromancer42.tea.core.analysis.Trgt;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.c.*;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContentProvider;
import org.eclipse.core.runtime.CoreException;
import org.neuromancer42.tea.ir.CFG;
import org.neuromancer42.tea.ir.Expr;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@TeAAnalysis(name = "cmanager")
public class CDTCManager extends AbstractAnalysis {
    // TODO eliminate domE, domI, domA
    @ProduceDom(description = "functions")
    public ProgramDom domM;

    @ProduceDom(description = "program points")
    public ProgramDom domP;

    @ProduceDom(description = "evaluations")
    public ProgramDom domE;

    @ProduceDom(description = "registers")
    public ProgramDom domV;

    @ProduceDom(description = "fields")
    public ProgramDom domF;

    @ProduceDom(description = "invocations")
    public ProgramDom domI;

    @ProduceDom(description = "cardinals")
    public ProgramDom domZ;

    @ProduceDom(description = "constants")
    public ProgramDom domC; // temporarily, use String to represent constants
    @ProduceDom(description = "types")
    public ProgramDom domT;
    @ProduceDom(description = "allocations")
    public ProgramDom domA;

    @ProduceDom(description = "operators")
    public ProgramDom domOP;

    @ProduceRel(doms = {"T", "F", "T"}, description = "field type of structs")
    public ProgramRel relStructFldType;
    @ProduceRel(doms = {"T", "T", "C"}, description = "content type and size of arrays")
    public ProgramRel relArrContentType;
    @ProduceRel(doms = {"T", "C"}, description = "type width")
    public ProgramRel relTypeWidth;

    @ProduceRel(doms = { "M", "P" }, description = "MPentry(meth,point)")
    public ProgramRel relMPentry;
    @ProduceRel(doms = {"M", "P" }, description = "MPexit(meth,point)")
    public ProgramRel relMPexit;
    @ProduceRel(doms = {"P", "P" }, description = "PPdirect(prev,post):unconditional edges")
    public ProgramRel relPPdirect;
    @ProduceRel(doms = {"P", "P", "V"}, description = "PPtrue(prev,post,cond):if-true edges")
    public ProgramRel relPPtrue;
    @ProduceRel(doms = {"P", "P", "V"}, description = "PPfalse(prev,post,cond):if-false edges")
    public ProgramRel relPPfalse;

    @ProduceRel(doms = {"P", "V", "E"}, description = "Peval(point,dest,eval):compute eval and store into dest")
    public ProgramRel relPeval;
    @ProduceRel(doms = {"P", "V"}, description = "Pload(point,r):load something into reg r")
    public ProgramRel relPload;
    @ProduceRel(doms = {"P", "V"}, description = "Pstore(point,r):store value of reg r to somewhere")
    public ProgramRel relPstore;
    @ProduceRel(doms = {"P", "V"}, description = "Palloca(point,r):alloc a address and assign to r")
    public ProgramRel relPalloca;
    @ProduceRel(doms = {"P", "I"}, description = "Pinvk(point,invk):invoke at this point")
    public ProgramRel relPinvk;
    @ProduceRel(doms = {"P"}, description = "mark no-op or unhandled point")
    public ProgramRel relPnoop;

    @ProduceRel(doms = {"V", "A", "T"}, description = "Alloca(reg,variable,type):allocate with address assigned to reg")
    public ProgramRel relAlloca;
    @ProduceRel(doms = {"V", "A", "T"}, description = "GlobalAlloca(reg,variable,type):allocate for global vars")
    public ProgramRel relGlobalAlloca;

    @ProduceRel(doms = {"V", "V"}, description = "LoadPtr(dst,src):dst = *src")
    public ProgramRel relLoadPtr;
    @ProduceRel(doms = {"V", "V"}, description = "StorePtr(dst,src):*dst = src")
    public ProgramRel relStorePtr;
    @ProduceRel(doms = {"V", "V", "F"}, description = "LoadFld(dst,src,field):dst=src->field")
    public ProgramRel relLoadFld;
    @ProduceRel(doms = {"V", "F", "V"}, description = "StoreFld(dst:V,field:F,src:V):dst->field=src")
    public ProgramRel relStoreFld;
    @ProduceRel(doms = {"V", "V", "V"}, description = "LoadArr(dst,src,idx):dst=src[idx]")
    public ProgramRel relLoadArr;
    @ProduceRel(doms = {"V", "V", "V"}, description = "StoreArr(dst,idx,src):dst[idx]=src")
    public ProgramRel relStoreArr;

    @ProduceRel(doms = {"I", "Z", "V"}, description = "IinvkArg(invk,i,v):v is the i-th argument of invocation")
    public ProgramRel relIinvkArg;
    @ProduceRel(doms = {"I", "V"}, description = "IinvkRet(invk,v):v is the ret-val of invocation")
    public ProgramRel relIinvkRet;
    @ProduceRel(doms = {"I", "V"}, description = "IndirectCall(invk,v):call with function pointer v")
    public ProgramRel relIndirectCall;
    @ProduceRel(doms = {"I", "M"}, description = "StaticCall(invk,meth)")
    public ProgramRel relStaticCall;

    @ProduceRel(doms = {"M"}, description = "mark external functions")
    public ProgramRel relExtMeth;
    @ProduceRel(name = "funcRef", doms = {"M", "V"}, description = "FuncRef(meth,name):function name as a function pointer")
    public ProgramRel relFuncRef;
    @ProduceRel(doms = {"M", "Z", "V"}, description = "MmethArg(meth,i,v):v is the i-th argument of function")
    public ProgramRel relMmethArg;
    @ProduceRel(doms = {"M", "V"}, description = "MmethRet(meth,v):v is the ret-val of function")
    public ProgramRel relMmethRet;
    @ProduceRel(name = "entryM", doms = {"M"}, description = "mark the main function")
    public ProgramRel relEntryM;

    @ProduceRel(doms = {"V", "C"}, description = "mark constants")
    public ProgramRel relVvalue;
    //public final ProgramRel relHvalue;

    // evaluations
    @ProduceRel(doms = {"V", "C"}, description = "mark constant expressions")
    public ProgramRel relEconst;
    @ProduceRel(doms = {"V", "OP", "V"}, description = "mark unary expressions")
    public ProgramRel relEunary;
    @ProduceRel(doms = {"V", "OP", "V", "V"}, description = "mark binary expressions")
    public ProgramRel relEbinop;
    @ProduceRel(doms = {"V", "V"}, description = "size of vla array")
    public ProgramRel relEsizeof;
    @ProduceRel(doms = {"V", "T", "V"}, description = "mark cast of primitive values")
    public ProgramRel relEprimcast;
    @ProduceRel(doms = {"V", "T", "V"}, description = "mark cast of pointers")
    public ProgramRel relEptrcast;
    @ProduceRel(name = "PtrOP", doms = {"OP"}, description = "operators for pointer computations")
    public ProgramRel relPtrOP;
    @ProduceRel(name = "NotPtrOP", doms = {"OP"}, description = "operators not for pointer computations")
    public ProgramRel relNotPtrOP;

    private IASTTranslationUnit translationUnit = null;
    private CFGBuilder builder;

    private final File sourceFile;
    private final String compileCmd;
    private Path workPath;
    private static Path dummySysrootPath;

    public List<ProgramDom> getProducedDoms() {
        return List.of(
                domM, domP, domE, domV, domF, domI, domZ, domC, domT, domA, domOP
        );
    }

    public List<ProgramRel> getProducedRels() {
        return List.of(
                relStructFldType, relArrContentType, relTypeWidth,
                relMPentry, relMPexit, relPPdirect, relPPtrue, relPPfalse,
                relPeval, relPload, relPstore, relPalloca, relPinvk, relPnoop,
                relAlloca, relGlobalAlloca, relLoadPtr, relStorePtr, relLoadFld, relStoreFld, relLoadArr, relStoreArr,
                relIinvkArg, relIinvkRet, relIndirectCall, relStaticCall,
                relExtMeth, relFuncRef, relMmethArg, relMmethRet, relEntryM,
                relVvalue,
                relEconst, relEunary, relEbinop, relEsizeof, relEprimcast, relEptrcast, relPtrOP, relNotPtrOP
        );
    }

    public CDTCManager(Path workPath, String fileName, String command) {
        compileCmd = command;
        sourceFile = new File(fileName);
        this.workPath = workPath;
        if (!sourceFile.isFile()) {
            Messages.fatal("CParser: the referenced path %s is not a source file", sourceFile.toString());
        }
        List<String> includePaths = new ArrayList<>();
        Map<String, String> definedSymbols = new LinkedHashMap<>();
        boolean isInclude = false;
        boolean isDefine = false;
        for (String cmdPart : command.split(" ")) {
            if (cmdPart.isBlank()) {
                continue;
            }
            if (isInclude) {
                if (cmdPart.startsWith("\"") && cmdPart.endsWith("\"")) {
                    cmdPart = cmdPart.substring(1, cmdPart.length() - 1);
                }
                for (String includePath : cmdPart.split(File.pathSeparator))  {
                    Messages.log("CParser: add include path %s", includePath);
                    includePaths.add(includePath);
                }
                isInclude = false;
            } else if (cmdPart.equals("-I")) {
                isInclude = true;
            } else if (cmdPart.startsWith("-I")) {
                cmdPart = cmdPart.substring(2);
                if (cmdPart.startsWith("\"") && cmdPart.endsWith("\"")) {
                    cmdPart = cmdPart.substring(1, cmdPart.length() - 1);
                }
                for (String includePath : cmdPart.split(File.pathSeparator)) {
                    Messages.log("CParser: add include path %s", includePath);
                    includePaths.add(includePath);
                }
            } else if (isDefine) {
                if (cmdPart.startsWith("\"") && cmdPart.endsWith("\"")) {
                    cmdPart = cmdPart.substring(1, cmdPart.length() - 1);
                }
                String[] pair = cmdPart.split("=");
                String symbol = pair[0];
                String value = "";
                if (pair.length > 1)
                    value = pair[1];
                Messages.log("CParser: add defined symbol %s=%s", symbol, value);
                definedSymbols.put(symbol, value);
                isDefine = false;
            } else if (cmdPart.equals("-D")) {
                isDefine = true;
            } else if (cmdPart.startsWith("-D")) {
                cmdPart = cmdPart.substring(2);
                if (cmdPart.startsWith("\"") && cmdPart.endsWith("\"")) {
                    cmdPart = cmdPart.substring(1, cmdPart.length() - 1);
                }
                String[] pair = cmdPart.split("=");
                String symbol = pair[0];
                String value = "";
                if (pair.length > 1)
                    value = pair[1];
                Messages.log("CParser: add defined symbol %s=%s", symbol, value);
                definedSymbols.put(symbol, value);
            }
        }

        String ppFilename = preprocess(command, fileName, includePaths, definedSymbols);

        FileContent fileContent = FileContent.createForExternalFileLocation(ppFilename);
        IScannerInfo scannerInfo = new ScannerInfo(definedSymbols, includePaths.toArray(new String[0]));
        IParserLogService log = new DefaultLogService();
        IncludeFileContentProvider includeContents = InternalFileContentProvider.getSavedFilesProvider();
        int opts = 0;

        try {
            // Note: special handling for invoking asm parser
            definedSymbols.putIfAbsent("__GNUC__", "11");
            definedSymbols.putIfAbsent("__GNUC_MINOR__", "3");
            // Note: special handling of some types
            definedSymbols.putIfAbsent("size_t", "unsigned long");
            definedSymbols.putIfAbsent("off_t", "long");
            definedSymbols.putIfAbsent("off32_t", "int");
            definedSymbols.putIfAbsent("off64_t", "long");
            definedSymbols.putIfAbsent("int64_t", "long");
            definedSymbols.putIfAbsent("uint64_t", "unsigned long");
            definedSymbols.putIfAbsent("int32_t", "int");
            definedSymbols.putIfAbsent("uint32_t", "unsigned int");
            definedSymbols.putIfAbsent("int16_t", "short");
            definedSymbols.putIfAbsent("uint16_t", "unsigned short");
            definedSymbols.putIfAbsent("int8_t", "char");
            definedSymbols.putIfAbsent("uint8_t", "unsigned char");
            translationUnit = GCCLanguage.getDefault().getASTTranslationUnit(fileContent, scannerInfo, includeContents, null, opts, log);
        } catch (CoreException e) {
            Messages.error("CParser: failed to crete parser for file %s, exit.", ppFilename);
            Messages.fatal(e);
            assert false;
        }
    }

    private static final String incDirective = "tea_include";
    public static void setDummySysroot(Path path) {
        try {
            dummySysrootPath = Files.createDirectories(path.resolve("dummy-sysroot"));
            String[] headers = {"stdio.h", "stdlib.h", "stddef.h", "stdarg.h", "stdint.h",
                    "time.h", "limits.h", "string.h", "ctype.h", "wchar.h", "fcntl.h", "signal.h", "errno.h",
                    "sys/types.h", "sys/stat.h", "sys/sysmacros.h", "io.h", "libio.h", "assert.h", "unistd.h",
                    "bits/types.h", "libintl.h", "locale.h", "getopt.h", "iconv.h", "dirent.h", "pthread.h",
                    "nl_types.h", "sys/statfs.h", "pcre.h", "langinfo.h", "fnmatch.h", "strings.h"
            };
            for (String header : headers) {
                String dummyInclude = String.format("%s <%s>", incDirective, header);
                List<String> lines = List.of(dummyInclude);
                Path headerPath = dummySysrootPath.resolve(header);
                if (!headerPath.getParent().toFile().isDirectory()) {
                    Files.createDirectories(headerPath.getParent());
                }
                Files.write(headerPath, lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Messages.error("CParser: failed to set dummy headers");
            Messages.fatal(e);
        }
    }

    private String preprocess(String command, String filename, List<String> includes, Map<String, String> defines) {
        String[] commands = command.split(" ");
        List<String> ppCmd = new ArrayList<>();
        ppCmd.add(commands[0]);
        ppCmd.add("-E");
        ppCmd.add(String.format("-D%s=#include", incDirective));
        ppCmd.add("-I" + dummySysrootPath.toAbsolutePath());
        for (String inc : includes) {
            ppCmd.add("-I" + Paths.get(inc).toAbsolutePath());
        }
        for (Map.Entry<String, String> def : defines.entrySet()) {
            ppCmd.add("-D" + def.getKey() + "=" + def.getValue());
        }
        ppCmd.add(Paths.get(filename).toAbsolutePath().toString());

        try {
            ProcessExecutor.simpleExecute(workPath, false, ppCmd, "preprocessed.c");
        } catch (IOException | InterruptedException e) {
            Messages.error("CParser: failed to preprocess source file");
            Messages.fatal(e);
            assert false;
        }
        Path ppPath = workPath.resolve("preprocessed.c");
        Messages.log("CParser: dumping preprocessed file to %s", ppPath);
        return ppPath.toAbsolutePath().toString();
    }

    public void init() {
        domM = new ProgramDom("M");
        domP = new ProgramDom("P");
        domE = new ProgramDom("E");
        domV = new ProgramDom("V");
        domF = new ProgramDom("F");
        domI = new ProgramDom("I");
        domZ = new ProgramDom("Z");
        domC = new ProgramDom("C");
        domT = new ProgramDom("T");
        domA = new ProgramDom("A");
        domOP = new ProgramDom("OP");

        // type hierarchy
        relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        relArrContentType = new ProgramRel("ArrContentType", domT, domT, domC);
        relTypeWidth = new ProgramRel("TypeWidth", domT, domC);

        // control flow relations
        relMPentry = new ProgramRel("MPentry", domM, domP);
        relMPexit = new ProgramRel("MPexit", domM, domP);
        relPPdirect = new ProgramRel("PPdirect", domP, domP);
        relPPtrue = new ProgramRel("PPtrue", domP, domP, domV);
        relPPfalse = new ProgramRel("PPfalse", domP, domP, domV);

        // statements
        relPeval = new ProgramRel("Peval", domP, domV, domE);
        relPload = new ProgramRel("Pload", domP, domV);
        relPstore = new ProgramRel("Pstore", domP, domV);
        relPalloca = new ProgramRel("Palloc", domP, domV);
        relPinvk = new ProgramRel("Pinvk", domP, domI);
        relPnoop = new ProgramRel("Pnoop", domP);
        relAlloca = new ProgramRel("Alloca", domV, domA, domT);
        relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA, domT);
        relLoadPtr = new ProgramRel("LoadPtr", domV, domV);
        relStorePtr = new ProgramRel("StorePtr", domV, domV);
        relLoadFld = new ProgramRel("LoadFld", domV, domV, domF);
        relStoreFld = new ProgramRel("StoreFld", domV, domF, domV);
        relLoadArr = new ProgramRel("LoadArr", domV, domV, domV);
        relStoreArr = new ProgramRel("StoreArr", domV, domV);

        // invocations
        relIinvkArg = new ProgramRel("IinvkArg", domI, domZ, domV);
        relIinvkRet = new ProgramRel("IinvkRet", domI, domV);
        relIndirectCall = new ProgramRel("IndirectCall", domI, domV);
        relStaticCall = new ProgramRel("StaticCall", domI, domM);

        // methods
        relExtMeth = new ProgramRel("ExtMeth", domM);
        relFuncRef = new ProgramRel("funcRef", domM, domV);
        relMmethArg = new ProgramRel("MmethArg", domM, domZ, domV);
        relMmethRet = new ProgramRel("MmethRet", domM, domV);
        relEntryM = new ProgramRel("entryM", domM);

        // values
        relVvalue = new ProgramRel("Vvalue", domV, domC);

        // expressions
        relEconst = new ProgramRel("Econst", domV, domC);
        relEunary = new ProgramRel("Eunary", domV, domOP, domV);
        relEbinop = new ProgramRel("Ebinop", domV, domOP, domV, domV);
        relEsizeof = new ProgramRel("Esizeof", domV, domV);
        relEprimcast = new ProgramRel("Eprimcast", domV, domT, domV);
        relEptrcast = new ProgramRel("Eptrcast", domV, domT, domV);
        relPtrOP = new ProgramRel("PtrOP", domOP);
        relNotPtrOP = new ProgramRel("NotPtrOP", domOP);
    }

    public void run() {
        init();
        openDomains();
        domPhase();
        saveDomains();

        openRelations();
        relPhase();
        saveRelations();
    }

    private void openDomains() {
        for (ProgramDom dom : getProducedDoms()) {
            dom.init();
        }
    }
    private void saveDomains() {
        for (ProgramDom dom : getProducedDoms()) {
            dom.save(workPath.toString());
        }
    }

    private void openRelations() {
        for (ProgramRel rel : getProducedRels()) {
            rel.init();
        }
    }

    private void saveRelations() {
        for (ProgramRel rel : getProducedRels()) {
            rel.save(workPath.toString());
            rel.close();
        }
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }

    @Override
    protected void domPhase() {
        builder = new CFGBuilder(translationUnit);
        builder.build();

        int numRegs = builder.getRegisters().size();
        for (int i = 0; i < numRegs; ++i) {
            domV.add(builder.util.regToRepr(i));
        }
        for (var c : builder.getSimpleConstants().values()) {
            domC.add(c);
        }
        domC.add(Constants.UNKNOWN);
        domC.add(Constants.NULL);
        for (Integer a : List.of(0,1,2,4,8,16,32)) {
            domC.add(a.toString());
        }
        for (IType t : builder.getTypes()) {
            Integer width = builder.util.typeWidth(t);
            if (width != null) {
                domC.add(width.toString());
            }
        }
        domA.add(Constants.NULL);
        for (int refReg : builder.getGlobalRefs()) {
            domA.add(builder.getAllocaForRef(refReg).getVariable());
        }
        int maxNumArg = 0;
        // Implicitly-used operator for array-access
        for (String op : List.of(Constants.OP_ADD, Constants.OP_MUL, Constants.OP_LE, Constants.OP_NE, Constants.OP_EQ, Constants.OP_AND, Constants.OP_OR)) {
            domOP.add(op);
        }

        for (IFunction meth : builder.getFuncs()) {
            // TODO: to more locatable representation?
            domM.add(builder.util.methToRepr(meth));
            int numMargs = meth.getParameters().length;
            if (numMargs > maxNumArg)
                maxNumArg = numMargs;
            for (int refReg : builder.getMethodVars(meth)) {
                domA.add(builder.getAllocaForRef(refReg).getVariable());
            }
            ValueGraph<CFG.CFGNode, Integer> cfg = builder.getIntraCFG(meth);
            if (cfg == null) {
                continue;
            }
            for (CFG.CFGNode node : cfg.nodes()) {
                // TODO: separate index and ir-code
                domP.add(builder.util.cfgnodeToRepr(node));
                if (node.hasEval()) {
                    Expr.Expression e = node.getEval().getExpr();
                    domE.add(builder.util.exprToRepr(e));
                    if (e.hasUnary())
                        domOP.add(e.getUnary().getOperator());
                    else if (e.hasBinary())
                        domOP.add(e.getBinary().getOperator());
                }
                if (node.hasInvk()) {
                    CFG.Invoke invk = node.getInvk();
                    domI.add(builder.util.invkToRepr(invk));
                    int numIargs = invk.getActualArgCount();
                    if (numIargs > maxNumArg)
                        maxNumArg = numIargs;
                }
            }
        }
        for (int i = 0; i < maxNumArg; ++i) {
            domZ.add(Integer.toString(i));
        }
        for (IType type : builder.getTypes()) {
            domT.add(builder.util.typeToRepr(type));
        }
        domT.add(builder.util.typeToRepr(CBasicType.VOID));
        domT.add(builder.util.typeToRepr(CPointerType.VOID_POINTER));
        for (IField field : builder.getFields()) {
            domF.add(builder.util.fieldToRepr(field));
        }
    }

    @Override
    protected void relPhase() {
        for (String op : domOP) {
            if (List.of(Constants.OP_ADD, Constants.OP_SUB, Constants.OP_INCR, Constants.OP_DECR, Constants.OP_ID).contains(op))
                relPtrOP.add(op);
            else
                relNotPtrOP.add(op);
        }
        for (IType type : builder.getTypes()) {
            if (type instanceof IArrayType) {
                IArrayType baseType = (IArrayType) type;
                IType contentType = baseType.getType();
                String sizeStr = "unknown";
                if (baseType.getSize() != null && baseType.getSize().numberValue() != null) {
                    Number size = baseType.getSize().numberValue();
                    sizeStr = String.valueOf(size);
                } else {
                    // TODO: vla size?
                }
                relArrContentType.add(builder.util.typeToRepr(baseType), builder.util.typeToRepr(contentType), sizeStr);
            } else if (type instanceof ICompositeType) {
                ICompositeType baseType = (ICompositeType) type;
                for (IField f : baseType.getFields()) {
                    IType fType = f.getType();
                    relStructFldType.add(builder.util.typeToRepr(baseType), builder.util.fieldToRepr(f), builder.util.typeToRepr(fType));
                }
            }
            String typeRepr = builder.util.typeToRepr(type);
            Integer width = builder.util.typeWidth(type);
            if (width == null)
                relTypeWidth.add(typeRepr, Constants.UNKNOWN);
            else
                relTypeWidth.add(typeRepr, width.toString());
        }
        for (IFunction func : builder.getFuncs()) {
            int refReg = builder.getRefReg(func);
            relFuncRef.add(builder.util.methToRepr(func), builder.util.regToRepr(refReg));
        }
        for (int refReg : builder.getGlobalRefs()) {
            CFG.Alloca alloca = builder.getAllocaForRef(refReg);
            String variable = alloca.getVariable();
            String type = alloca.getType();
            relGlobalAlloca.add(builder.util.regToRepr(refReg), variable, type);
        }
        for (var entry : builder.getSimpleConstants().entrySet()) {
            int reg = entry.getKey();
            String c = entry.getValue();
            if (reg >= 0) {
                relVvalue.add(builder.util.regToRepr(reg), c);
            }
        }
        for (IFunction meth : builder.getFuncs()) {
            if (meth.getName().contentEquals("main")) {
                Messages.debug("CParser: find entry method %s[%s]", meth.getClass().getSimpleName(), meth);
                relEntryM.add(builder.util.methToRepr(meth));
            }
            ValueGraph<CFG.CFGNode, Integer> cfg = builder.getIntraCFG(meth);
            if (cfg == null) {
                Messages.debug("CParser: external function %s[%s]", meth.getClass().getSimpleName(), meth);
                relExtMeth.add(builder.util.methToRepr(meth));
                continue;
            }
            relMPentry.add(builder.util.methToRepr(meth), builder.util.cfgnodeToRepr(builder.getEntryNode(meth)));
            int[] mArgRegs = builder.getFuncArgs(meth);
            for (int i = 0; i < mArgRegs.length; ++i) {
                relMmethArg.add(builder.util.methToRepr(meth), Integer.toString(i), builder.util.regToRepr(mArgRegs[i]));
            }
            for (EndpointPair<CFG.CFGNode> edge : cfg.edges()) {
                CFG.CFGNode p = edge.source();
                CFG.CFGNode q = edge.target();
                int cond = cfg.edgeValueOrDefault(edge, -1);
                if (cond >= 0) {
                    int condReg = cond / 2;
                    if (cond % 2 == 1) {
                        relPPtrue.add(builder.util.cfgnodeToRepr(p), builder.util.cfgnodeToRepr(q), builder.util.regToRepr(condReg));
                    } else {
                        relPPfalse.add(builder.util.cfgnodeToRepr(p), builder.util.cfgnodeToRepr(q), builder.util.regToRepr(condReg));
                    }
                }  else {
                    relPPdirect.add(builder.util.cfgnodeToRepr(p), builder.util.cfgnodeToRepr(q));
                }
            }
            for (CFG.CFGNode p : cfg.nodes()) {
//                if (!(p.hasEval() || p.hasEntry() || p.hasLoad() || p.hasAlloc() || p.hasStore() || p.hasInvk())) {
//                    relPnoop.add(CDTUtil.cfgnodeToRepr(p));
//                }
                String pRepr = builder.util.cfgnodeToRepr(p);
                if (p.hasReturn()) {
                    relMPexit.add(builder.util.methToRepr(meth), pRepr);
                    relPnoop.add(pRepr);
                    if (p.getReturn().hasFormalRet()) {
                        String retRegRepr = p.getReturn().getFormalRet();
                        relMmethRet.add(builder.util.methToRepr(meth), retRegRepr);
                    }
//                } else if (p.hasCond()) {
//                    // TODO: fix edge value
//                    String condRegRepr = p.getCond().getCondReg();
//                    assert (cfg.outDegree(p) == 2);
//                    CFG.CFGNode[] outNodes = cfg.successors(p).toArray(new CFG.CFGNode[0]);
//                    CFG.CFGNode qTrue = outNodes[0];
//                    relPPtrue.add(pRepr, CDTUtil.cfgnodeToRepr(qTrue), condRegRepr);
//                    CFG.CFGNode qFalse = outNodes[1];
//                    relPPfalse.add(pRepr, CDTUtil.cfgnodeToRepr(qFalse), condRegRepr);
                } else if (p.hasInvk()) {
                    CFG.Invoke invk = p.getInvk();
                    String invkRepr = builder.util.invkToRepr(invk);
                    relPinvk.add(pRepr, invkRepr);
                    if (invk.hasActualRet()) {
                        relIinvkRet.add(invkRepr, invk.getActualRet());
                    } else {
                        Messages.debug("CParser: invocation has no ret-val [%s]", TextFormat.shortDebugString(invk));
                    }
                    if (invk.hasStaticRef()) {
                        String staticRef = invk.getStaticRef();
                        relStaticCall.add(invkRepr, staticRef);
                    } else {
                        relIndirectCall.add(invkRepr, invk.getFuncPtr());
                    }

                    for (int i = 0; i < invk.getActualArgCount(); ++i) {
                        relIinvkArg.add(invkRepr, Integer.toString(i), invk.getActualArg(i));
                    }
                } else if (p.hasEval()) {
                    String vRepr = p.getEval().getResultReg();
                    Expr.Expression e = p.getEval().getExpr();
                    if (e.hasGep()) {
                        Expr.GepExpr gepExpr = e.getGep();
                        String uRepr = gepExpr.getBasePtr();
                        if (gepExpr.hasIndex()) {
                            Messages.debug("CParser: get offset address %s = [%s]", vRepr, TextFormat.shortDebugString(gepExpr));
                            relPload.add(pRepr, vRepr);
                            relLoadArr.add(vRepr, uRepr, gepExpr.getIndex());
                        } else if (gepExpr.hasField()) {
                            Messages.debug("CParser: get field address #%s = [%s]", p.getEval().getResultReg(), TextFormat.shortDebugString(gepExpr));
                            relPload.add(pRepr, vRepr);
                            relLoadFld.add(vRepr, uRepr, gepExpr.getField());
                        }
                    } else {
                        relPeval.add(pRepr, vRepr, builder.util.exprToRepr(e));
                        if (e.hasLiteral()) {
                            relEconst.add(vRepr, e.getLiteral().getLiteral());
                        } else if (e.hasUnary()) {
                            Expr.UnaryExpr uExpr = e.getUnary();
                            relEunary.add(vRepr, uExpr.getOperator(), uExpr.getOprand());
                        } else if (e.hasBinary()) {
                            Expr.BinaryExpr bExpr = e.getBinary();
                            relEbinop.add(vRepr, bExpr.getOperator(), bExpr.getOprand1(), bExpr.getOprand2());
                        } else if (e.hasSizeof()) {
                            Expr.SizeOfExpr sizeof = e.getSizeof();
                            relEsizeof.add(vRepr, sizeof.getRef());
                        } else if (e.hasCast()) {
                            String type = e.getType();
                            if (type.endsWith("*")) {
                                String ptType = type.substring(0, type.length() - 1);
                                relEptrcast.add(vRepr, ptType, e.getCast().getInner());
                            } else {
                                relEprimcast.add(vRepr, type, e.getCast().getInner());
                            }
                        } else {
                            // Note: unhandled expr goes to unknown
                            relEconst.add(vRepr, Constants.UNKNOWN);
                        }
                    }
                } else if (p.hasLoad()) {
                    String vRepr = p.getLoad().getReg();
                    relPload.add(pRepr, vRepr);
                    String uRepr = p.getLoad().getAddr();
                    relLoadPtr.add(vRepr, uRepr);
                } else if (p.hasStore()) {
                    String vRepr = p.getStore().getReg();
                    relPstore.add(pRepr, vRepr);
                    String uRepr = p.getStore().getAddr();
                    relStorePtr.add(uRepr, vRepr);
                } else if (p.hasAlloca()) {
                    String vRepr = p.getAlloca().getReg();
                    relPalloca.add(pRepr, vRepr);
                    String variable = p.getAlloca().getVariable();
                    String type = p.getAlloca().getType();
                    relAlloca.add(vRepr, variable, type);
                } else {
                    if (!(p.hasGoto() || p.hasLabel() || p.hasCond() || p.hasEntry()))
                        Messages.warn("CParser: mark unknown program point as no-op (%s)", pRepr);
                    // TODO fix pnoop in other analyses
                    relPnoop.add(pRepr);
                }
            }
        }
    }

    public IASTTranslationUnit getTranslationUnit() {
        return translationUnit;
    }

    private CInstrument instr;


    public void setInstrument() {
        String target = null;
        List<String> splitted = new ArrayList<>(List.of(compileCmd.split(" ")));
        if (splitted.contains("-o")) {
            for (int i = splitted.indexOf("-o") + 1; i < splitted.size(); ++i) {
                if (!splitted.get(i).isBlank()) {
                    target = splitted.get(i);
                    break;
                }
            }
        } else {
            for (String part : splitted) {
                if (part.startsWith("-o")) {
                    target = part.substring(2);
                    break;
                }
            }
        }
        assert compileCmd.contains(sourceFile.getName());
        for (int i = 0; i < splitted.size(); ++i) {
            if (splitted.get(i).contains(sourceFile.getName())) {
                splitted.set(i, "instrumented.c");
            }
        }
        if (target == null) {
            splitted.add("-oinstrumented");
            target = "instrumented";
        }
        String newCommand = StringUtil.join(splitted, " ");
        try {
            Path path = Files.createDirectories(workPath.resolve("instr"));
            instr = new CInstrument(path, newCommand, target, "instrumented.c");
        } catch (IOException e) {
            Messages.error("CParser: failed to create working directory for instrumentor");
            Messages.fatal(e);
        }
    }

    public CInstrument getInstrument() {
        if (instr == null) {
            Messages.fatal("CParser: set instrument first!");
            assert false;
        }
        return instr;
    }

    public static final String[] observableRels = {
            "ci_IM(I,M):possible function call resolutions",
            "ci_reachableM(M):reachable methods",
            "ci_PHval(P,H,U):value of stack objects"
    };

    public class CInstrument {
        private final CASTTranslationUnit instrTU = (CASTTranslationUnit) translationUnit.copy(IASTNode.CopyStyle.withLocations);

        private final Path instrWorkDirPath;
        private final String compileCmd;
        private final String target;
        private final String source;

        public CInstrument(Path path, String cmd, String target, String source) {
            this.instrWorkDirPath = path;
            this.compileCmd = cmd;
            this.target = target;
            this.source = source;
        }
        //    private final ASTModificationStore modStore;
        private final Map<IASTNode, IASTNode> modMap = new LinkedHashMap<>();

        private final IndexMap<IASTNode> instrPositions = new IndexMap<>();

        private int genInstrumentId(IASTNode astNode) {
            if (instrPositions.contains(astNode)) {
                Messages.error("CInstrument: position [%s](line#%d) already instrumented", astNode.getRawSignature(), astNode.getFileLocation().getStartingLineNumber());
                return -1;
            }
            instrPositions.add(astNode.getOriginalNode());
            return instrPositions.indexOf(astNode.getOriginalNode());
        }

        public int instrumentBeforeInvoke(CFG.Invoke invk) {
            if (invk.hasFuncPtr()) {
                IASTFunctionCallExpression callExpr = (IASTFunctionCallExpression) builder.getInvkExpr(invk);
                if (callExpr == null) {
                    Messages.error("CInstrument: cannot find original expression of invk {%s}", TextFormat.shortDebugString(invk));
                    return -1;
                }
                IASTExpression fNameExpr = callExpr.getFunctionNameExpression();
                int instrId = genInstrumentId(fNameExpr);
                if (instrId == -1) return -1;
                IASTExpression newFNameExpr = wrapPeekExpr(Trace.BEFORE_INVOKE, instrId, fNameExpr);
                modMap.put(fNameExpr.getOriginalNode(), newFNameExpr);
                Messages.debug("CInstrument: instrumenting function name expression [%s]#%d (original: %d)", new ASTWriter().write(fNameExpr.getOriginalNode()), fNameExpr.hashCode(), fNameExpr.getOriginalNode().hashCode());
                return instrId;
            }
            return -1;
        }

        public int instrumentBeforeInvoke(String invkRepr) {
            if (invkRepr == null)
                return -1;
            CFG.Invoke invk = builder.util.reprToInvk(invkRepr);
            if (invk == null)
                return -1;
            return instrumentBeforeInvoke(invk);
        }

        public int instrumentBeforeInvoke(int iId) {
            return instrumentBeforeInvoke(domI.get(iId));
        }

        public int instrumentEnterMethod(IFunction meth) {
            Messages.debug("CInstrument: trying to instrument when entering [%s]", meth);
            for (IASTDeclaration decl: instrTU.getDeclarations()) {
                if (decl instanceof IASTFunctionDefinition) {
                    IASTFunctionDefinition fDef = (IASTFunctionDefinition) decl;
                    if (fDef.getDeclarator().getName().resolveBinding().getName().equals(meth.getName())) {
                        IASTCompoundStatement fBody = (IASTCompoundStatement) fDef.getBody();
                        int instrId = genInstrumentId(fBody);
                        IASTCompoundStatement newBody = peekVarEnterBlock(instrId, meth.getNameCharArray(), fBody);
                        modMap.put(fBody.getOriginalNode(), newBody);
                        Messages.debug("CInstrument: instrumenting body of [%s]#%d (original: %d)", meth, fBody.hashCode(), fBody.getOriginalNode().hashCode());
                        return instrId;
                    }
                }
            }
            return -1;
        }

        public int instrumentEnterMethod(String fRepr) {
            if (fRepr == null || !domM.contains(fRepr))
                return -1;
            IFunction func = builder.getFunc(fRepr);
            return instrumentEnterMethod(func);
        }

        public int instrumentEnterMethod(int mId) {
            if (mId < 0 || mId >= domM.size()) {
                return -1;
            }
            return instrumentEnterMethod(domM.get(mId));
        }

        private IASTCompoundStatement peekVarEnterBlock(int instrId, char[] nameCharArray, IASTCompoundStatement fBody) {
            IASTName fName = new CASTName("peek".toCharArray());
            IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

            IASTLiteralExpression typeIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(Trace.ENTER_METHOD));
            IASTLiteralExpression instrIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(instrId));
            IASTName vName = new CASTName(nameCharArray);
            IASTIdExpression vNameExpr = CNodeFactory.getDefault().newIdExpression(vName);

            IASTInitializerClause[] argList = new IASTInitializerClause[]{typeIdExpr, instrIdExpr, vNameExpr};
            IASTFunctionCallExpression peekExpr = CNodeFactory.getDefault().newFunctionCallExpression(fNameExpr, argList);
            IASTExpressionStatement peekStat = CNodeFactory.getDefault().newExpressionStatement(peekExpr);

            IASTCompoundStatement newBody = CNodeFactory.getDefault().newCompoundStatement();
            newBody.addStatement(peekStat);
            for (IASTStatement stat: fBody.getStatements()) {
                newBody.addStatement(stat.copy(IASTNode.CopyStyle.withLocations));
            }
            return newBody;
        }

        /*
         * Note: utilizing implicit conversion to and from (void*)
         */
        private IASTExpression wrapPeekExpr(int typeId, int instrId, IASTExpression expr) {
            IASTName fName = new CASTName("peek".toCharArray());
            IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

            IASTLiteralExpression typeIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(typeId));
            IASTLiteralExpression instrIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(instrId));
            IASTInitializerClause[] argList = new IASTInitializerClause[]{typeIdExpr, instrIdExpr, expr.copy(IASTNode.CopyStyle.withLocations)};

            IASTFunctionCallExpression callExpr = CNodeFactory.getDefault().newFunctionCallExpression(fNameExpr, argList);
            IASTExpressionList exprList = CNodeFactory.getDefault().newExpressionList();
            exprList.addExpression(callExpr);
            exprList.addExpression(expr.copy(IASTNode.CopyStyle.withLocations));
            IASTExpression wrapExpr = CNodeFactory.getDefault().newUnaryExpression(IASTUnaryExpression.op_bracketedPrimary, exprList);
            return wrapExpr;
        }

        // TODO: check variable is visible in current point
        public int instrumentBeforeExpr(CFG.CFGNode cfgNode, String memObj) {
            if (!cfgNode.hasEval()) {
                Messages.error("CParser: Instrumenting non-expression position");
                return -1;
            }
            CFG.Evaluation eval = cfgNode.getEval();

            String[] typePair = memObj.split(":");
            if (typePair.length != 2) {
                Messages.error("CParser: malformed mem object {%s}", memObj);
            }
            String type = typePair[0];
            String accessPath = typePair[1];
            if (accessPath.contains("#"))
                accessPath = accessPath.substring(0, accessPath.indexOf("#"));

            if (!type.equals(Constants.TYPE_INT))
                return -1;

            Messages.debug("CParser: peek value of variable {%s} at {%s}", accessPath, TextFormat.shortDebugString(cfgNode));

            Expr.Expression expr = eval.getExpr();
            IASTExpression origExpr = builder.getExpression(expr);
            if (origExpr == null)
                return -1;
            IASTExpression newExpr = peekStackBeforeExpression(accessPath, origExpr);
            if (newExpr == null)
                return -1;

            Messages.debug("CParser: instrumented expr {%s}", (new ASTWriter()).write(newExpr));
            //        ASTModification mod = new ASTModification(ASTModification.ModificationKind.REPLACE, origExpr, newExpr, );
            //        modStore.storeModification(null, mod);
            modMap.put(origExpr.getOriginalNode(), newExpr);

            return instrPositions.indexOf(origExpr.getOriginalNode());
        }

        public void instrumentPeekStack(String accessPath, IASTExpression origExpr) {
            IASTExpression newExpr = peekStackBeforeExpression(accessPath, origExpr);
            if (newExpr == null)
                return;
            Messages.debug("CParser: instrumented expr {%s}", (new ASTWriter()).write(newExpr));
            //        ASTModification mod = new ASTModification(ASTModification.ModificationKind.REPLACE, origExpr, newExpr, );
            //        modStore.storeModification(null, mod);
            modMap.put(origExpr.getOriginalNode(), newExpr);
        }

        public CASTTranslationUnit instrumented() {
            //        ChangeGenerator changeGenerator = new ChangeGenerator(modStore, new NodeCommentMap());
            //        changeGenerator.generateChange(tu);
            instrTU.accept(new InstrVisitor());
            //        tu.addDeclaration(genPeekVarFunction());
            return instrTU;
        }

        public void dumpInstrumented(Path newFilePath) {
            try {
                List<String> lines = new ArrayList<>();
                lines.add("""
                        #include <stdio.h>
                        void* peek (int type, int id, void *ptr) {
                                FILE *fptr = fopen("peek.log", "a");
                                fprintf(fptr, "%d\t%d\t%ld\\n", type, id, (long) ptr);
                                fclose(fptr);
                                return ptr;
                        }
                        """);
                for (IASTPreprocessorIncludeStatement incl : translationUnit.getIncludeDirectives()) {
                    lines.add(incl.toString());
                }
                try {
                    lines.add(new ASTWriter().write(instrumented()));
                } catch (RuntimeException e) {
                    Messages.error("CInstrument: error while generating instrumented file");
                    Messages.fatal(e);
                }
                Files.write(newFilePath, lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                Messages.error("CInstrument: failed to dump instrumented source file");
                Messages.fatal(e);
            }
            Messages.log("CInstrument: dump insturmneted c source file to " + newFilePath);
        }

        public IASTExpression peekStackBeforeExpression(String accessPath, IASTExpression origExpr) {
            // TODO: manual added filter, remove it in future
            if (accessPath.contains("*") || accessPath.contains("["))
                return null;
            IASTName fName = new CASTName("peek".toCharArray());
            IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

            IASTName vName = new CASTName(accessPath.toCharArray());
            IASTIdExpression vNameExpr = CNodeFactory.getDefault().newIdExpression(vName);

            instrPositions.add(origExpr.getOriginalNode());
            int peekId = instrPositions.indexOf(origExpr.getOriginalNode());
            IASTLiteralExpression typeIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(Trace.BEFORE_EXPR));
            IASTLiteralExpression peekIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(peekId));
            IASTInitializerClause[] argList = new IASTInitializerClause[]{typeIdExpr, peekIdExpr, vNameExpr};

            IASTFunctionCallExpression callExpr = CNodeFactory.getDefault().newFunctionCallExpression(fNameExpr, argList);

            IASTExpressionList instrList = CNodeFactory.getDefault().newExpressionList();
            instrList.addExpression(callExpr);
            instrList.addExpression(origExpr.copy(IASTNode.CopyStyle.withLocations));

            return CNodeFactory.getDefault().newUnaryExpression(IASTUnaryExpression.op_bracketedPrimary, instrList);
        }

        @Deprecated
        private IASTDeclaration genPeekVarFunction() {
            IASTName fName = new CASTName("peek".toCharArray());
            IASTFunctionDeclarator fDtor = CNodeFactory.getDefault().newFunctionDeclarator(fName);
            IASTDeclSpecifier fDeclSpec = CNodeFactory.getDefault().newSimpleDeclSpecifier();
            IASTSimpleDeclaration fDecl = CNodeFactory.getDefault().newSimpleDeclaration(fDeclSpec);
            fDecl.addDeclarator(fDtor);
            Messages.debug("CParser: add declaretion: %s", new ASTWriter().write(fDecl));
            return fDecl;
        }

        public String getName() {
            return "CInstrument";
        }

        private boolean built = false;
        public Set<Trgt.Tuple> test(List<String> argList) {
            if (!built) {
                compile();
                built = true;
            }

            List<String> peekLines = runInstrumentedAndPeek(argList.toArray(new String[0]));

            List<Trace> traces = new ArrayList<>();

            for (String line : peekLines) {
                String[] words = line.split("\t");
                int id = Integer.parseInt(words[0]);
                long[] content = new long[words.length - 1];
                for (int i = 0; i < content.length; ++i) {
                    content[i] = Long.parseLong(words[i + 1]);
                }
                traces.add(new Trace(id, content));

            }
            Set<Trgt.Tuple> triggerd = new LinkedHashSet<>();
            triggerd.addAll(processTraceCIIM(traces));
            triggerd.addAll(processTraceReachableM(traces));
            triggerd.addAll(processTracePHval(traces));
            return triggerd;
        }

        private int testTime = 0;
        public List<String> runInstrumentedAndPeek(String ... argList) {
            List<String> executeCmd = new ArrayList<>();
            Path targetPath = instrWorkDirPath.resolve(target);
            if (!Files.isExecutable(targetPath)) {
                Messages.error("CInstrument: target executable %s does not exist, skip", targetPath.toString());
                return new ArrayList<>();
            }
            executeCmd.add(targetPath.toAbsolutePath().toString());
            executeCmd.addAll(List.of(argList));
            try {
                Path peekLog = instrWorkDirPath.resolve("peek.log");
                Files.deleteIfExists(peekLog);
                int retval = ProcessExecutor.simpleExecute(instrWorkDirPath, true, executeCmd, String.format("test-%03d.out", testTime));
                List<String> peekLines = Files.readAllLines(peekLog);
                Files.move(peekLog, peekLog.resolveSibling(String.format("peek-%03d.log", testTime)), StandardCopyOption.REPLACE_EXISTING);
                testTime++;
                return peekLines;
            } catch (InterruptedException | IOException e) {
                Messages.error("CInstrument: failed to execute cmd {%s}, skip: %s", StringUtil.join(executeCmd, " "), e.toString());
            }
            return new ArrayList<>();
        }

        public void compile() {
            try {
                Path instrFile = instrWorkDirPath.resolve(source);
                dumpInstrumented(instrFile);
                List<String> compileCmdList = List.of(compileCmd.split(" "));
                ProcessExecutor.simpleExecute(instrWorkDirPath, false, compileCmdList, null);
            } catch (IOException | InterruptedException e) {
                Messages.error("CInstrument: failed to execute instrumenting commands");
                Messages.fatal(e);
            }
        }

        public class InstrVisitor extends ASTVisitor {
            public InstrVisitor() {
                this.shouldVisitExpressions = true;
                this.shouldVisitInitializers = true;
                this.shouldVisitDeclarations = true;
            }

            @Override
            public int visit(IASTInitializer initializer) {
                if (initializer instanceof IASTEqualsInitializer) {
                    IASTEqualsInitializer eqInit = (IASTEqualsInitializer) initializer;
                    IASTInitializerClause initCls = eqInit.getInitializerClause();
                    IASTNode origNode = initCls.getOriginalNode();
                    if (modMap.containsKey(origNode)) {
                        IASTNode newInitCls = modMap.get(origNode);
                        eqInit.setInitializerClause((IASTInitializerClause) newInitCls);
                        newInitCls.setParent(eqInit);
                    }
                }
                return super.visit(initializer);
            }

            @Override
            public int visit(IASTExpression expression) {
                if (expression.getParent() instanceof IASTIfStatement
                        && ((IASTIfStatement) expression.getParent()).getConditionExpression().equals(expression)) {
                    IASTNode origNode = expression.getOriginalNode();
                    Messages.debug("CInstrument: visiting if-cond-expr [%s]#%d (original: %d)", new ASTWriter().write(origNode), expression.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNode = modMap.get(origNode);
                        IASTIfStatement ifStat = (IASTIfStatement) expression.getParent();
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNode));
                        ifStat.setConditionExpression((IASTExpression) newNode);
                    }
                } else if (expression.getParent() instanceof IASTWhileStatement
                        && ((IASTWhileStatement) expression.getParent()).getCondition().equals(expression)) {
                    IASTNode origNode = expression.getOriginalNode();
                    Messages.debug("CInstrument: visiting while-cond-expr [%s]#%d (original: %d)", new ASTWriter().write(origNode), expression.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNode = modMap.get(origNode);
                        IASTWhileStatement whileStat = (IASTWhileStatement) expression.getParent();
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNode));
                        whileStat.setCondition((IASTExpression) newNode);
                    }
                } else if (expression.getParent() instanceof IASTDoStatement
                        && ((IASTDoStatement) expression.getParent()).getCondition().equals(expression)) {
                    IASTNode origNode = expression.getOriginalNode();
                    Messages.debug("CInstrument: visiting dowhile-cond-expr [%s]#%d (original: %d)", new ASTWriter().write(origNode), expression.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNode = modMap.get(origNode);
                        IASTDoStatement doStat = (IASTDoStatement) expression.getParent();
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNode));
                        doStat.setCondition((IASTExpression) newNode);

                    }
                } else if (expression.getParent() instanceof IASTForStatement
                        && ((IASTForStatement) expression.getParent()).getConditionExpression().equals(expression)) {
                    IASTNode origNode = expression.getOriginalNode();
                    Messages.debug("CInstrument: visiting for-cond-expr [%s]#%d (original: %d)", new ASTWriter().write(origNode), expression.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNode = modMap.get(origNode);
                        IASTForStatement doStat = (IASTForStatement) expression.getParent();
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNode));
                        doStat.setConditionExpression((IASTExpression) newNode);
                    }
                } else if (expression.getParent() instanceof IASTBinaryExpression
                        && ((IASTBinaryExpression) expression.getParent()).getOperator() == IASTBinaryExpression.op_assign
                        && ((IASTBinaryExpression) expression.getParent()).getOperand1().equals(expression)) {
                    IASTNode origNode = expression.getOriginalNode();
                    Messages.debug("CInstrument: visiting assign-dst expr [%s]#%d (original: %d)", new ASTWriter().write(origNode), expression.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNode = modMap.get(origNode);
                        IASTBinaryExpression assignExpr = (IASTBinaryExpression) expression.getParent();
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNode));
                        assignExpr.setOperand1((IASTExpression) newNode);
                    }
                } else if (expression.getParent() instanceof IASTBinaryExpression
                        && ((IASTBinaryExpression) expression.getParent()).getOperator() == IASTBinaryExpression.op_assign
                        && ((IASTBinaryExpression) expression.getParent()).getOperand2().equals(expression)) {
                    IASTNode origNode = expression.getOriginalNode();
                    Messages.debug("CInstrument: visiting assign-src expr [%s]#%d (original: %d)", new ASTWriter().write(origNode), expression.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNode = modMap.get(origNode);
                        IASTBinaryExpression assignExpr = (IASTBinaryExpression) expression.getParent();
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNode));
                        assignExpr.setOperand2((IASTExpression) newNode);
                    }
                }
//                if (expression instanceof IASTBinaryExpression) {
//                    IASTBinaryExpression binExpr = (IASTBinaryExpression) expression;
//                    if (binExpr.getOperator() == IASTBinaryExpression.op_assign && modMap.containsKey(origNode)) {
//                        IASTNode newRhs = modMap.get(origNode);
//                        binExpr.setOperand2((IASTExpression) newRhs);
//                        newRhs.setParent(binExpr);
//                    }
//                    if (binExpr.getOperator() == IASTBinaryExpression.op_divide && modMap.containsKey(origNode)) {
//                        IASTNode newDivider = modMap.get(origNode);
//                        binExpr.setOperand2((IASTExpression) newDivider);
//                        newDivider.setParent(binExpr);
//                    }
//                }
                if (expression instanceof IASTFunctionCallExpression) {
                    IASTFunctionCallExpression callExpr = (IASTFunctionCallExpression) expression;
                    IASTExpression fNameExpr = callExpr.getFunctionNameExpression();
                    IASTNode origNode = fNameExpr.getOriginalNode();
                    Messages.debug("CInstrument: visiting function name expression [%s]#%d (original: %d)", new ASTWriter().write(origNode), fNameExpr.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNameExpr = modMap.get(origNode);
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNameExpr));
                        callExpr.setFunctionNameExpression((IASTExpression) newNameExpr);
                    }
                }
                return super.visit(expression);
            }

            @Override
            public int visit(IASTDeclaration declaration) {
                if (declaration instanceof IASTFunctionDefinition) {
                    IASTFunctionDefinition fDef = (IASTFunctionDefinition) declaration;
                    IASTStatement fBody  = fDef.getBody();
                    IASTNode origNode = fBody.getOriginalNode();
                    Messages.debug("CInstrument: visiting body of [%s]#%d (original: %d)", fDef.getDeclarator().getName(), fBody.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newBody = modMap.get(origNode);
                        Messages.debug("CInstrument: instrumented function body");
                        fDef.setBody((IASTStatement) newBody);
                    }
                }
                return super.visit(declaration);
            }
        }

        public boolean instrument(Trgt.Tuple tuple) {
            if (builder == null) {
                Messages.fatal("CInstrument: build cfg first before instrumenting");
                assert false;
            }
            boolean res = switch (tuple.getRelName()) {
                case "ci_IM" -> instrumentCIIM(tuple.getAttribute(0), tuple.getAttribute(1));
                case "ci_reachableM" -> instrumentReachableM(tuple.getAttribute(0));
                case "ci_PHval" -> instrumentPHval(tuple.getAttribute(0), tuple.getAttribute(1), tuple.getAttribute(2));
                default -> false;
            };
            if (res)
                Messages.debug("CInstrument: instrumented tuple: %s", TextFormat.shortDebugString(tuple));
            return res;
        }

        private final Map<Long, String> invkInstrMap = new HashMap<>();
        private final Map<Long, String> methInstrMap = new HashMap<>();
        private final Map<Long, Triple<String, String, Set<String>>> phValInstrMap = new HashMap<>();

        private boolean instrumentCIIM(String ... attrs) {
            if (attrs.length != 2)
                return false;
            String invkRepr = attrs[0];
            String methRepr = attrs[1];
            int iInstrId = instrumentBeforeInvoke(invkRepr);
            if (iInstrId < 0)
                return false;
            invkInstrMap.put((long) iInstrId, invkRepr);
            int mInstrId = instrumentEnterMethod(methRepr);
            if (mInstrId < 0)
                return false;
            methInstrMap.put((long) mInstrId, methRepr);
            return true;
        }

        private List<Trgt.Tuple> processTraceCIIM(List<Trace> traces) {
            List<Trgt.Tuple> provedTuples = new ArrayList<>();
            Map<Long, String> methAddrMap = new LinkedHashMap<>();
            for (Trace trace: traces) {
                if (trace.getType() == Trace.ENTER_METHOD) {
                    long instrId = trace.getContent(0);
                    String methRepr = methInstrMap.get(instrId);
                    long methAddr = trace.getContent(1);
                    methAddrMap.put(methAddr, methRepr);
                }
            }
            for (Trace trace: traces) {
                if (trace.getType() == Trace.BEFORE_INVOKE) {
                    long instrId = trace.getContent(0);
                    long methAddr = trace.getContent(1);
                    String invkRepr = invkInstrMap.get(instrId);
                    String methRepr = methAddrMap.get(methAddr);
                    provedTuples.add(Trgt.Tuple.newBuilder()
                            .setRelName("ci_IM")
                            .addAllAttribute(List.of(invkRepr, methRepr))
                            .build()
                    );
                }
            }
            return provedTuples;
        }

        private boolean instrumentReachableM(String ... attrs) {
            if (attrs.length != 1)
                return false;
            String methRepr = attrs[0];
            int mInstrId = instrumentEnterMethod(methRepr);
            if (mInstrId < 0)
                return false;
            methInstrMap.put((long) mInstrId, methRepr);
            return true;
        }

        private List<Trgt.Tuple> processTraceReachableM(List<Trace> traces) {
            List<Trgt.Tuple> provedTuples = new ArrayList<>();
            for (Trace trace : traces) {
                if (trace.getType() == Trace.ENTER_METHOD) {
                    long instrId = trace.getContent(0);
                    String methRepr = methInstrMap.get(instrId);
                    provedTuples.add(Trgt.Tuple.newBuilder()
                            .setRelName("ci_reachableM")
                            .addAttribute(methRepr)
                            .build()
                    );
                }
            }
            return provedTuples;
        }

        private boolean instrumentPHval(String ... attrs) {
            if (attrs.length != 3)
                return false;
            String pRepr = attrs[0];
            String objRepr = attrs[1];
            String itvRepr = attrs[2];
            CFG.CFGNode p = builder.util.reprToCfgNode(pRepr);
            if (p == null)
                return false;
            int instrId = instrumentBeforeExpr(p, objRepr);
            if (instrId < 0)
                return false;
            phValInstrMap.computeIfAbsent((long) instrId, k -> new ImmutableTriple<>(pRepr, objRepr, new LinkedHashSet<>()))
                    .getRight().add(itvRepr);
            return true;
        }

        private List<Trgt.Tuple> processTracePHval(List<Trace> traces) {
            List<Trgt.Tuple> provedTuples = new ArrayList<>();
            for (Trace trace : traces) {
                if (trace.getType() == Trace.BEFORE_EXPR) {
                    long instrId = trace.getContent(0);
                    long val = trace.getContent(1);
                    Triple<String, String, Set<String>> phvals = phValInstrMap.get(instrId);
                    String pRepr = phvals.getLeft();
                    String objRepr = phvals.getMiddle();
                    for (String itvRepr : phvals.getRight()) {
                        if (itvRepr.startsWith("Itv:{")) {
                            int v = Integer.parseInt(itvRepr.substring(5, itvRepr.length()-1));
                            if (val == v) {
                                provedTuples.add(Trgt.Tuple.newBuilder().setRelName("ci_PHval")
                                        .addAttribute(pRepr).addAttribute(objRepr)
                                        .addAttribute(itvRepr).build()
                                );
                            }
                        } else if (itvRepr.startsWith("Itv:[")) {
                            int commaIdx = itvRepr.indexOf(",");
                            int l = Integer.parseInt(itvRepr.substring(5, commaIdx));
                            int r = Integer.parseInt(itvRepr.substring(commaIdx + 1, itvRepr.length()-1));
                            if (val >= l && val <= r) {
                                provedTuples.add(Trgt.Tuple.newBuilder().setRelName("ci_PHval")
                                        .addAttribute(pRepr).addAttribute(objRepr)
                                        .addAttribute(itvRepr).build()
                                );
                            }
                        }
                    }
                }
            }
            return provedTuples;
        }

        class Trace {
            public static final int BEFORE_INVOKE = 1;
            public static final int ENTER_METHOD = 2;
            public static final int BEFORE_EXPR = 3;
            private final int typeId;
            private final long[] contents;
            public Trace(int id, long ... contents) {
                typeId = id;
                this.contents = contents;
            }

            public int getType() {
                return typeId;
            }

            public long getContent(int i) {
                if (i >= contents.length) {
                    Messages.error("Trace %d: index %d out of content length %d, return -1", hashCode(), i, contents.length);
                    return -1;
                } else {
                    return contents[i];
                }
            }
        }
    }
}
