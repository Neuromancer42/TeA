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
import com.neuromancer42.tea.commons.configs.Messages;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;
import org.neuromancer42.tea.ir.CFG;
import org.neuromancer42.tea.ir.Expr;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@TeAAnalysis(name = "cmanager")
public class CDTCManager extends AbstractAnalysis {
    public static final String[] observableRels = {
            // TODO
    };

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
    @ProduceRel(doms = {"T", "T"}, description = "content type of arrays")
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
        relArrContentType = new ProgramRel("ArrFieldType", domT, domT);

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
                relArrContentType.add(CDTUtil.typeToRepr(baseType), CDTUtil.typeToRepr(contentType));
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
                            Messages.debug("CParser: get field address #%d = [%s]", p.getEval().getResultReg(), TextFormat.shortDebugString(gepExpr));
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
}
