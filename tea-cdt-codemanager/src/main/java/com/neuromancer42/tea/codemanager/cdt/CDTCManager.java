package com.neuromancer42.tea.codemanager.cdt;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.ValueGraph;
import com.google.protobuf.TextFormat;
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

public class CDTCManager {
    public static final String[] producedDoms = {
            "M:functions",
            "P:program-points",
            "E:evaluations",
            "V:registers",
            "F:fields",
            "I:invocations",
            "Z:cardinals",
            "C:constants",
            "T:types",
            "A:allocations"
    };
    public static final String[] producedRels = {
            "MPentry(meth:M,point:P)",
            "MPexit(meth:M,point:P)",
            "PPdirect(prev:P,post:P):unconditional edges",
            "PPtrue(prev:P,post:P,cond:V):if-true edges",
            "PPfalse(prev:P,post:P,cond:V):if-false edges",
            "Peval(point:P,dest:V,eval:E):compute eval and store into dest",
            "Pload(point:P,r:V):update reg r",
            "Pstore(point:P,r:V):store value to where r points to",
            "Palloc(point:P,r:V):alloc a address and assign to r",
            "Pinvk(point:P,invk:I):invoke at this point",
            "Pnoop(point:P):do nothing or unhandled point",
            "Alloca(reg:V,alloca:A):allocate with address assigned to reg",
            "GlobalAlloca(reg:V, alloca:A):allocate for global vars",
            "LoadPtr(dst:V,src:V):dst = *src",
            "StorePtr(dst:V,src:V):*dst = src",
            "LoadFld(dst:V,src:V,field:F):dst=src->field",
            "StoreFld(dst:V,field:F,src:V):dst->field=src",
            "LoadArr(dst:V,src:V,idx:V):dst=src[idx]",
            "StoreArr(dst:V,src:V):dst[]=src",
            "IinvkArg(invk:I,i:Z,v:V):v is the i-th argument of invocation",
            "IinvkRet(invk:I,v:V):v is the ret-val of invocation",
            "IndirectCall(invk:I,v:V):call with function pointer v",
            "StaticCall(invk:I,meth:M)",
            "ExtMeth(meth:M):mark external functions",
            "FuncRef(meth:M,name:V):function name as a function pointer",
            "MmethArg(meth:M,i:Z,v:V):v is the i-th argument of function",
            "MmethRet(meth:M,v:V):v is the ret-val of function",
            "EntryM(meth:M):mark the main function",
            "Vvalue(v:V,constant:C):mark constants"

    };
    public static final String[] observableRels = {
            // TODO
    };

    public final ProgramDom domM;
    public final ProgramDom domP;
    public final ProgramDom domE;
    public final ProgramDom domV;
    public final ProgramDom domF;
    public final ProgramDom domI;
    public final ProgramDom domZ;
    public final ProgramDom domC; // temporarily, use String to represent constants
    public final ProgramDom domT;
    public final ProgramDom domA;

    public final ProgramRel relMPentry;
    public final ProgramRel relMPexit;
    public final ProgramRel relPPdirect;
    public final ProgramRel relPPtrue;
    public final ProgramRel relPPfalse;

    public final ProgramRel relPeval;
    public final ProgramRel relPload;
    public final ProgramRel relPstore;
    public final ProgramRel relPalloc;
    public final ProgramRel relPinvk;
    public final ProgramRel relPnoop;

    public final ProgramRel relAlloca;
    public final ProgramRel relGlobalAlloca;
    public final ProgramRel relLoadPtr;
    public final ProgramRel relStorePtr;
    public final ProgramRel relLoadFld;
    public final ProgramRel relStoreFld;
    public final ProgramRel relLoadArr;
    public final ProgramRel relStoreArr;

    public final ProgramRel relIinvkArg;
    public final ProgramRel relIinvkRet;
    public final ProgramRel relIndirectCall;
    public final ProgramRel relStaticCall;

    public final ProgramRel relExtMeth;
    public final ProgramRel relFuncRef;
    public final ProgramRel relMmethArg;
    public final ProgramRel relMmethRet;
    public final ProgramRel relEntryM;

    public final ProgramRel relVvalue;
    //public final ProgramRel relHvalue;
    private IASTTranslationUnit translationUnit = null;

    private final File sourceFile;
    private Path workPath;

    public List<ProgramDom> getProducedDoms() {
        return List.of(
                domM, domP, domE, domV, domF, domI, domZ, domC, domT, domA
        );
    }

    public List<ProgramRel> getProducedRels() {
        return List.of(
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

        domM = ProgramDom.createDom("M");
        domP = ProgramDom.createDom("P");
        domE = ProgramDom.createDom("E");
        domV = ProgramDom.createDom("V");
        domF = ProgramDom.createDom("F");
        domI = ProgramDom.createDom("I");
        domZ = ProgramDom.createDom("Z");
        domC = ProgramDom.createDom("C");
        domT = ProgramDom.createDom("T");
        domA = ProgramDom.createDom("A");

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
        relAlloca = new ProgramRel("Alloca", domV, domA);
        relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA);
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
        CFGBuilder builder = new CFGBuilder(translationUnit);
        builder.build();

        // TODO: move this into function template
        openDomains();
        int numRegs = builder.getRegisters().size();
        for (int i = 0; i < numRegs; ++i) {
            domV.add(CDTUtil.regToRepr(i));
        }
        for (var c : builder.getSimpleConstants().values()) {
            domC.add(c);
        }
        for (int refReg : builder.getGlobalRefs()) {
            domA.add(CDTUtil.allocaToRepr(builder.getAllocaForRef(refReg)));
        }
        int maxNumArg = 0;
        for (IFunction meth : builder.getFuncs()) {
            // TODO: to more locatable representation?
            domM.add(CDTUtil.methToRepr(meth));
            int numMargs = meth.getParameters().length;
            if (numMargs > maxNumArg)
                maxNumArg = numMargs;
            for (int refReg : builder.getMethodVars(meth)) {
                domA.add(CDTUtil.allocaToRepr(builder.getAllocaForRef(refReg)));
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
        saveDomains();

        openRelations();
        for (IFunction func : builder.getFuncs()) {
            int refReg = builder.getRefReg(func);
            relFuncRef.add(CDTUtil.methToRepr(func), CDTUtil.regToRepr(refReg));
        }
        for (int refReg : builder.getGlobalRefs()) {
            CFG.Alloca alloca = builder.getAllocaForRef(refReg);
            relGlobalAlloca.add(CDTUtil.regToRepr(refReg), CDTUtil.allocaToRepr(alloca));
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
                    String allocRepr = CDTUtil.allocaToRepr(p.getAlloca());
                    relAlloca.add(vRepr, allocRepr);
                } else {
                    if (!(p.hasGoto() || p.hasLabel() || p.hasCond() || p.hasEntry()))
                        Messages.warn("CParser: mark unknown program point as no-op (%s)", pRepr);
                    // TODO fix pnoop in other analyses
                    relPnoop.add(pRepr);
                }
            }
        }
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

    public IASTTranslationUnit getTranslationUnit() {
        return translationUnit;
    }
}
