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
import com.neuromancer42.tea.commons.util.StringUtil;
import com.neuromancer42.tea.core.analysis.Trgt;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTName;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.parser.c.CNodeFactory;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
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

    @ProduceRel(doms = {"T", "F", "T"}, description = "field type of structs")
    public ProgramRel relStructFldType;
    @ProduceRel(doms = {"T", "T", "C"}, description = "content type and size of arrays")
    public ProgramRel relArrContentType;

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
    @ProduceRel(doms = {"P", "V"}, description = "Pload(point,r):update reg r")
    public ProgramRel relPload;
    @ProduceRel(doms = {"P", "V"}, description = "Pstore(point,r):store value to where r points to")
    public ProgramRel relPstore;
    @ProduceRel(doms = {"P", "V"}, description = "Palloc(point,r):alloc a address and assign to r")
    public ProgramRel relPalloc;
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

    private IASTTranslationUnit translationUnit = null;
    private CFGBuilder builder;

    private final File sourceFile;
    private Path workPath;

    public List<ProgramDom> getProducedDoms() {
        return List.of(
                domM, domP, domE, domV, domF, domI, domZ, domC, domT, domA
        );
    }

    public List<ProgramRel> getProducedRels() {
        return List.of(
                relStructFldType, relArrContentType,
                relMPentry, relMPexit, relPPdirect, relPPtrue, relPPfalse,
                relPeval, relPload, relPstore, relPalloc, relPinvk, relPnoop,
                relAlloca, relGlobalAlloca, relLoadPtr, relStorePtr, relLoadFld, relStoreFld, relLoadArr, relStoreArr,
                relIinvkArg, relIinvkRet, relIndirectCall, relStaticCall,
                relExtMeth, relFuncRef, relMmethArg, relMmethRet, relEntryM,
                relVvalue
        );
    }

    public CDTCManager(Path workPath, String fileName, Map<String, String> definedSymbols, List<String> includePaths) {
        sourceFile = new File(fileName);
        this.workPath = workPath;
        if (!sourceFile.isFile()) {
            Messages.fatal("CParser: the referenced path %s is not a source file", sourceFile.toString());
        }
        FileContent fileContent = FileContent.createForExternalFileLocation(fileName);
        IScannerInfo scannerInfo = new ScannerInfo(definedSymbols, includePaths.toArray(new String[0]));
        IParserLogService log = new DefaultLogService();
        IncludeFileContentProvider includeContents = IncludeFileContentProvider.getSavedFilesProvider();
        int opts = 0;

        try {
            translationUnit = GCCLanguage.getDefault().getASTTranslationUnit(fileContent, scannerInfo, includeContents, null, opts, log);
        } catch (CoreException e) {
            Messages.error("CParser: failed to crete parser for file %s, exit.", fileName);
            Messages.fatal(e);
            assert false;
        }
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

        // type hierarchy
        relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        relArrContentType = new ProgramRel("ArrFieldType", domT, domT, domC);

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
        relPalloc = new ProgramRel("Palloc", domP, domV);
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
            domV.add(CDTUtil.regToRepr(i));
        }
        for (var c : builder.getSimpleConstants().values()) {
            domC.add(c);
        }
        for (int refReg : builder.getGlobalRefs()) {
            domA.add(builder.getAllocaForRef(refReg).getVariable());
        }
        int maxNumArg = 0;
        for (IFunction meth : builder.getFuncs()) {
            // TODO: to more locatable representation?
            domM.add(CDTUtil.methToRepr(meth));
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
                domP.add(CDTUtil.cfgnodeToRepr(node));
                if (node.hasEval()) {
                    Expr.Expression e = node.getEval().getExpr();
                    domE.add(CDTUtil.exprToRepr(e));
                }
                if (node.hasInvk()) {
                    CFG.Invoke invk = node.getInvk();
                    domI.add(CDTUtil.invkToRepr(invk));
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
            // TODO transform to raw types ?
            domT.add(CDTUtil.typeToRepr(type));
        }
        for (IField field : builder.getFields()) {
            domF.add(CDTUtil.fieldToRepr(field));
        }
    }

    @Override
    protected void relPhase() {
        for (IType type : builder.getTypes()) {
            if (type instanceof IArrayType) {
                IArrayType baseType = (IArrayType) type;
                IType contentType = baseType.getType();
                String sizeStr = "unknown";
                Number size = baseType.getSize().numberValue();
                if (size != null) {
                    sizeStr = String.valueOf(size);
                }
                relArrContentType.add(CDTUtil.typeToRepr(baseType), CDTUtil.typeToRepr(contentType), sizeStr);
            } else if (type instanceof ICompositeType) {
                ICompositeType baseType = (ICompositeType) type;
                for (IField f : baseType.getFields()) {
                    IType fType = f.getType();
                    relStructFldType.add(CDTUtil.typeToRepr(baseType), CDTUtil.fieldToRepr(f), CDTUtil.typeToRepr(fType));
                }
            }
        }
        for (IFunction func : builder.getFuncs()) {
            int refReg = builder.getRefReg(func);
            relFuncRef.add(CDTUtil.methToRepr(func), CDTUtil.regToRepr(refReg));
        }
        for (int refReg : builder.getGlobalRefs()) {
            CFG.Alloca alloca = builder.getAllocaForRef(refReg);
            String variable = alloca.getVariable();
            String type = alloca.getType();
            relGlobalAlloca.add(CDTUtil.regToRepr(refReg), variable, type);
        }
        for (var entry : builder.getSimpleConstants().entrySet()) {
            int reg = entry.getKey();
            String c = entry.getValue();
            if (reg >= 0) {
                relVvalue.add(CDTUtil.regToRepr(reg), c);
            }
        }
        for (IFunction meth : builder.getFuncs()) {
            if (meth.getName().contentEquals("main")) {
                Messages.debug("CParser: find entry method %s[%s]", meth.getClass().getSimpleName(), meth);
                relEntryM.add(CDTUtil.methToRepr(meth));
            }
            ValueGraph<CFG.CFGNode, Integer> cfg = builder.getIntraCFG(meth);
            if (cfg == null) {
                Messages.debug("CParser: external function %s[%s]", meth.getClass().getSimpleName(), meth);
                relExtMeth.add(CDTUtil.methToRepr(meth));
                continue;
            }
            relMPentry.add(CDTUtil.methToRepr(meth), CDTUtil.cfgnodeToRepr(builder.getEntryNode(meth)));
            int[] mArgRegs = builder.getFuncArgs(meth);
            for (int i = 0; i < mArgRegs.length; ++i) {
                relMmethArg.add(CDTUtil.methToRepr(meth), Integer.toString(i), CDTUtil.regToRepr(mArgRegs[i]));
            }
            for (EndpointPair<CFG.CFGNode> edge : cfg.edges()) {
                CFG.CFGNode p = edge.source();
                CFG.CFGNode q = edge.target();
                int cond = cfg.edgeValueOrDefault(edge, -1);
                if (cond >= 0) {
                    int condReg = cond / 2;
                    if (cond % 2 == 1) {
                        relPPtrue.add(CDTUtil.cfgnodeToRepr(p), CDTUtil.cfgnodeToRepr(q), CDTUtil.regToRepr(condReg));
                    } else {
                        relPPfalse.add(CDTUtil.cfgnodeToRepr(p), CDTUtil.cfgnodeToRepr(q), CDTUtil.regToRepr(condReg));
                    }
                }  else {
                    relPPdirect.add(CDTUtil.cfgnodeToRepr(p), CDTUtil.cfgnodeToRepr(q));
                }
            }
            for (CFG.CFGNode p : cfg.nodes()) {
//                if (!(p.hasEval() || p.hasEntry() || p.hasLoad() || p.hasAlloc() || p.hasStore() || p.hasInvk())) {
//                    relPnoop.add(CDTUtil.cfgnodeToRepr(p));
//                }
                String pRepr = CDTUtil.cfgnodeToRepr(p);
                if (p.hasReturn()) {
                    relMPexit.add(CDTUtil.methToRepr(meth), pRepr);
                    if (p.getReturn().hasFormalRet()) {
                        String retRegRepr = p.getReturn().getFormalRet();
                        relMmethRet.add(CDTUtil.methToRepr(meth), retRegRepr);
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
                    String invkRepr = CDTUtil.invkToRepr(invk);
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
                        relPeval.add(pRepr, vRepr, CDTUtil.exprToRepr(e));
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
                    relPalloc.add(pRepr, vRepr);
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

    public CInstrument getInstrument() {
        if (instr == null) {
            try {
                Path path = Files.createDirectories(workPath.resolve("instr"));
                instr = new CInstrument(path);
            } catch (IOException e) {
                Messages.error("CParser: failed to create working directory for instrumentor");
                Messages.fatal(e);
            }
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

        public CInstrument(Path path) {
            this.instrWorkDirPath = path;
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
            CFG.Invoke invk = CDTUtil.reprToInvk(invkRepr);
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
                    if (incl.isSystemInclude())
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
                int id = Integer.parseInt(words[1]);
                long[] content = new long[words.length - 2];
                for (int i = 0; i < content.length; ++i) {
                    content[i] = Long.parseLong(words[i + 2]);
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
            executeCmd.add("./instrumented");
            executeCmd.addAll(List.of(argList));
            try {
                Path peekLog = instrWorkDirPath.resolve("peek.log");
                Files.deleteIfExists(peekLog);
                int retval = execute(true, executeCmd, String.format("test-%03d.out", testTime));
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
                Path instrFile = instrWorkDirPath.resolve("instrumented.c");
                dumpInstrumented(instrFile);
                List<String> compileCmd = new ArrayList<>();
                compileCmd.add("clang");
                String[] flags = System.getProperty("chord.source.flags", "").split(" ");
                if (flags.length > 0)
                    compileCmd.addAll(List.of(flags));
                compileCmd.addAll(List.of("instrumented.c", "-o", "instrumented"));
                execute(false, compileCmd, null);
            } catch (IOException | InterruptedException e) {
                Messages.error("CInstrument: failed to execute instrumenting commands");
                Messages.fatal(e);
            }
        }

        private int execute(boolean ignoreRetVal, List<String> cmd, String outputFile) throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.directory(instrWorkDirPath.toFile());
            if (outputFile != null)
                builder.redirectOutput(instrWorkDirPath.resolve(outputFile).toFile());
            Messages.log("Executing: " + StringUtil.join(cmd, " "));
            Process cmdProcess = builder.start();
            int cmdRetVal = cmdProcess.waitFor();
            if (!ignoreRetVal && cmdRetVal != 0) {
                String errString = new String(cmdProcess.getErrorStream().readAllBytes());
                Messages.error("Abnormal exit with retval %d", cmdRetVal);
                Messages.fatal(new RuntimeException(errString));
            }
            String outputStr;
            if (outputFile == null)
                outputStr = new String(cmdProcess.getInputStream().readAllBytes());
            else
                outputStr = Files.readString(instrWorkDirPath.resolve(outputFile));
            Messages.debug("Output:\n%s", outputStr);
            return cmdRetVal;
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
                if (expression instanceof IASTBinaryExpression && expression.getParent() instanceof IASTIfStatement) {
                    IASTBinaryExpression binExpr = (IASTBinaryExpression) expression;
                    IASTNode origNode = binExpr.getOriginalNode();
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
                    Messages.debug("CInstrument: visiting binary cond-expr [%s]#%d (original: %d)", new ASTWriter().write(origNode), binExpr.hashCode(), origNode.hashCode());
                    if (modMap.containsKey(origNode)) {
                        IASTNode newNode = modMap.get(origNode);
                        IASTIfStatement ifStat = (IASTIfStatement) binExpr.getParent();
                        Messages.debug("CInstrument: instrumented into [%s]", new ASTWriter().write(newNode));
                        ifStat.setConditionExpression((IASTExpression) newNode);

                    }
                }
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
            return switch (tuple.getRelName()) {
                case "ci_IM" -> instrumentCIIM(tuple.getAttribute(0), tuple.getAttribute(1));
                case "ci_reachableM" -> instrumentReachableM(tuple.getAttribute(0));
                case "ci_PHval" -> instrumentPHval(tuple.getAttribute(0), tuple.getAttribute(1), tuple.getAttribute(2));
                default -> false;
            };
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
            CFG.CFGNode p = CDTUtil.reprToCfgNode(pRepr);
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
