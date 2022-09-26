package com.neuromancer42.tea.program.cdt.internal;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.internal.cfg.*;
import com.neuromancer42.tea.program.cdt.internal.evaluation.*;
import com.neuromancer42.tea.program.cdt.internal.memory.*;
import org.eclipse.cdt.codan.core.model.cfg.IBasicBlock;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CParser {

    public final ProgramDom<IFunction> domM;
    public final ProgramDom<IBasicBlock> domP;
    public final ProgramDom<IEval> domE;
    public final ProgramDom<Integer> domV;
    public final ProgramDom<IField> domF;
    public final ProgramDom<IASTFunctionCallExpression> domI;
    public final ProgramDom<Integer> domZ;
    public final ProgramDom<String> domC; // temporarily, use String to represent constants
    public final ProgramDom<IType> domT;
    public final ProgramDom<IVariable> domA;

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

    public final ProgramRel relFuncRef;
    public final ProgramRel relMmethArg;
    public final ProgramRel relMmethRet;
    public final ProgramRel relEntryM;

    public final ProgramRel relVvalue;
    //public final ProgramRel relHvalue;

    public final ProgramDom<?>[] generatedDoms;
    public final ProgramRel[] generatedRels;

    private final String fileName;

    public CParser(String fileName) {
        this.fileName = fileName;
        domM = ProgramDom.createDom("M", IFunction.class);
        domP = ProgramDom.createDom("P", IBasicBlock.class);
        domE = ProgramDom.createDom("E", IEval.class);
        domV = ProgramDom.createDom("V", Integer.class);
        domF = ProgramDom.createDom("F", IField.class);
        domI = ProgramDom.createDom("I", IASTFunctionCallExpression.class);
        domZ = ProgramDom.createDom("Z", Integer.class);
        domC = ProgramDom.createDom("C", String.class);
        domT = ProgramDom.createDom("T", IType.class);
        domA = ProgramDom.createDom("A", IVariable.class);

        generatedDoms = new ProgramDom<?>[]{domM, domP, domE, domV, domF, domI, domZ, domC, domT, domA};

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
        relLoadArr = new ProgramRel("LoadArr", domV, domV);
        relStoreArr = new ProgramRel("StoreArr", domV, domV);

        // invocations
        relIinvkArg = new ProgramRel("IinvkArg", domI, domZ, domV);
        relIinvkRet = new ProgramRel("IinvkRet", domI, domV);
        relIndirectCall = new ProgramRel("IndirectCall", domI, domV);
        relStaticCall = new ProgramRel("StaticCall", domI, domM);

        // methods
        relFuncRef = new ProgramRel("funcRef", domM, domV);
        relMmethArg = new ProgramRel("MmethArg", domM, domZ, domV);
        relMmethRet = new ProgramRel("MmethRet", domM, domV);
        relEntryM = new ProgramRel("entryM", domM);

        // values
        relVvalue = new ProgramRel("Vvalue", domV, domC);

        generatedRels = new ProgramRel[]{
                relMPentry, relMPexit, relPPdirect, relPPtrue, relPPfalse,
                relPeval, relPload, relPstore, relPalloc, relPinvk, relPnoop,
                relAlloca, relGlobalAlloca, relLoadPtr, relStorePtr, relLoadFld, relStoreFld, relLoadArr, relStoreArr,
                relIinvkArg, relIinvkRet, relIndirectCall, relStaticCall,
                relFuncRef, relMmethArg, relMmethRet, relEntryM,
                relVvalue
        };
    }


    public void run() {
        File sourceFile = new File(fileName);
        if (!sourceFile.isFile()) {
            Messages.fatal("CParser: the referenced path %s is not a source file", sourceFile.toString());
        }
        FileContent fileContent = FileContent.createForExternalFileLocation(fileName);
        Map<String, String> definedSymbols = new HashMap<>();
        String[] includePaths = new String[0];
        IScannerInfo scannerInfo = new ScannerInfo(definedSymbols, includePaths);
        IParserLogService log = new DefaultLogService();
        IncludeFileContentProvider emptyIncludes = IncludeFileContentProvider.getEmptyFilesProvider();
        int opts = 8; //TODO: read documents?

        IASTTranslationUnit translationUnit = null;
        try {
            translationUnit = GCCLanguage.getDefault().getASTTranslationUnit(fileContent, scannerInfo, emptyIncludes, null, opts, log);
        } catch (CoreException e) {
            Messages.error("CParser: failed to crete parser for file %s, exit.", fileName);
            Messages.fatal(e);
            assert false;
        }

        CFGBuilder builder = new CFGBuilder(translationUnit);
        builder.build();

        String dotName = sourceFile.getName() + ".dot";
        try {
            BufferedWriter bw = Files.newBufferedWriter(Path.of(dotName), StandardCharsets.UTF_8);
            PrintWriter pw = new PrintWriter(bw);
            builder.dumpDot(pw);
        } catch (IOException e) {
            Messages.error("CParser: failed to dump DOT file %s", dotName);
            Messages.fatal(e);
        }
        // TODO: move this into function template
        openDomains();
        int numRegs = builder.getRegisters().size();
        for (int i = 0; i < numRegs; ++i) {
            domV.add(i);
        }
        for (var c : builder.getSimpleConstants().values()) {
            domC.add(c);
        }
        for (IVariable var : builder.getGlobalVars()) {
            domA.add(var);
        }
        int maxNumArg = 0;
        for (IFunction meth : builder.getDeclaredFuncs()) {
            domM.add(meth);
            int numMargs = meth.getParameters().length;
            if (numMargs > maxNumArg)
                maxNumArg = numMargs;
            for (IVariable var : builder.getMethodVars(meth)) {
                domA.add(var);
            }
            CFGBuilder.IntraCFG cfg = builder.getIntraCFG(meth);
            if (cfg == null) {
                Messages.warn("CParser: external function? %s[%s]", meth.getClass().getSimpleName(), meth);
                continue;
            }
            Collection<IBasicBlock> nodes = cfg.getNodes();
            nodes.removeAll(cfg.getDeadNodes());
            domP.addAll(nodes);
            for (IBasicBlock n : nodes) {
                if (n instanceof EvalNode) {
                    IEval e = ((EvalNode) n).getEvaluation();
                    domE.add(e);
                    if (e instanceof StaticCallEval || e instanceof IndirectCallEval) {
                        IASTFunctionCallExpression invk = (IASTFunctionCallExpression) e.getExpression();
                        domI.add(invk);
                        int numIargs = invk.getArguments().length;
                        if (numIargs > maxNumArg)
                            maxNumArg = numIargs;
                    }
                }
            }
        }
        for (int i = 0; i < maxNumArg; ++i) {
            domZ.add(i);
        }
        domT.addAll(builder.getTypes());
        domF.addAll(builder.getFields());
        saveDomains();

        openRelations();
        for (IFunction func : builder.getDeclaredFuncs()) {
            int refReg = builder.getRefReg(func);
            relFuncRef.add(func, refReg);
        }
        for (IVariable var : builder.getGlobalVars()) {
            int refReg = builder.getRefReg(var);
            relGlobalAlloca.add(refReg, var);
        }
        for (var entry : builder.getSimpleConstants().entrySet()) {
            int reg = entry.getKey();
            String c = entry.getValue();
            if (reg >= 0) {
                relVvalue.add(reg, c);
            }
        }
        for (IFunction meth : builder.getDeclaredFuncs()) {
            if (meth.getName().contentEquals("main")) {
                Messages.debug("CParser: find entry method %s[%s]", meth.getClass().getSimpleName(), meth);
                relEntryM.add(meth);
            }
            CFGBuilder.IntraCFG cfg = builder.getIntraCFG(meth);
            if (cfg == null)
                continue;
            relMPentry.add(meth, cfg.getStartNode());
            int[] mArgRegs = builder.getFuncArgs(meth);
            for (int i = 0; i < mArgRegs.length; ++i) {
                relMmethArg.add(meth, i, mArgRegs[i]);
            }
            Collection<IBasicBlock> nodes = cfg.getNodes();
            nodes.removeAll(cfg.getDeadNodes());
            for (var p : nodes) {
                if (!(p instanceof EvalNode || p instanceof StoreNode || p instanceof LoadNode || p instanceof AllocNode)) {
                    relPnoop.add(p);
                }
                if (p instanceof ReturnNode) {
                    relMPexit.add(meth, p);
                    int retReg = ((ReturnNode) p).getRegister();
                    if (retReg >= 0) {
                        relMmethRet.add(meth, retReg);
                    }
                } else if (p instanceof CondNode) {
                    int condReg = ((CondNode) p).getRegister();
                    assert (p.getOutgoingNodes().length == 2);
                    IBasicBlock qTrue = p.getOutgoingNodes()[0];
                    relPPtrue.add(p, qTrue, condReg);
                    IBasicBlock qFalse = p.getOutgoingNodes()[1];
                    relPPfalse.add(p, qFalse, condReg);
                } else if (p instanceof EvalNode) {
                    IBasicBlock q = ((EvalNode) p).getOutgoing();
                    relPPdirect.add(p, q);
                    IEval e = ((EvalNode) p).getEvaluation();
                    int v = ((EvalNode) p).getRegister();
                    if (e instanceof StaticCallEval || e instanceof IndirectCallEval) {
                        IASTFunctionCallExpression invk = (IASTFunctionCallExpression) e.getExpression();
                        relPinvk.add(p, invk);
                        if (v >= 0) {
                            relIinvkRet.add(invk, v);
                        } else {
                            Messages.debug("CParser: invocation has no ret-val [%s]", e.toDebugString());
                        }
                        int[] iArgRegs;
                        if (e instanceof StaticCallEval) {
                            IFunction func = ((StaticCallEval) e).getFunction();
                            relStaticCall.add(invk, func);
                            iArgRegs = ((StaticCallEval) e).getArguments();
                        } else {
                            int funcReg = ((IndirectCallEval) e).getFunctionReg();
                            relIndirectCall.add(invk, funcReg);
                            iArgRegs = ((IndirectCallEval) e).getArguments();
                        }

                        IASTInitializerClause[] argExprs = invk.getArguments();
                        assert iArgRegs.length == argExprs.length;
                        for (int i = 0; i < iArgRegs.length; ++i) {
                            relIinvkArg.add(invk, i, iArgRegs[i]);
                        }
                    } else if (e instanceof GetOffsetPtrEval) {
                        int u = ((GetOffsetPtrEval) e).getBasePtr();
                        int o = ((GetOffsetPtrEval) e).getOffset();
                        Messages.debug("CParser: get offset address #%d = [%s]", v, e.toDebugString());
                        relPload.add(p, v);
                        relLoadArr.add(v, u);
                    } else if (e instanceof GetFieldPtrEval) {
                        int u = ((GetFieldPtrEval) e).getBasePtr();
                        IField f = ((GetFieldPtrEval) e).getField();
                        Messages.debug("CParser: get field address #%d = [%s]", v, e.toDebugString());
                        relPload.add(p, v);
                        relLoadFld.add(v, u, f);
                    } else {
                        relPeval.add(p, v, e);
                    }
                } else if (p instanceof LoadNode) {
                    IBasicBlock q = ((LoadNode) p).getOutgoing();
                    relPPdirect.add(p, q);
                    int v = ((LoadNode) p).getValue();
                    relPload.add(p, v);
                    int u = ((LoadNode) p).getPointer();
                    relLoadPtr.add(v, u);
                } else if (p instanceof StoreNode) {
                    IBasicBlock q = ((StoreNode) p).getOutgoing();
                    relPPdirect.add(p, q);
                    int v = ((StoreNode) p).getValue();
                    relPstore.add(p, v);
                    int u = ((StoreNode) p).getPointer();
                    relStorePtr.add(u, v);
                } else if (p instanceof AllocNode) {
                    IBasicBlock q = ((AllocNode) p).getOutgoing();
                    relPPdirect.add(p,q);
                    int v = ((AllocNode) p).getRegister();
                    relPalloc.add(p, v);
                    IVariable variable = ((AllocNode) p).getVariable();
                    relAlloca.add(v, variable);
                } else {
                    for (var q : p.getOutgoingNodes())
                        relPPdirect.add(p, q);
                }
            }
        }
        saveRelations();
    }

    private void openDomains() {
        for (ProgramDom<?> dom : generatedDoms) {
            dom.init();
        }
    }
    private void saveDomains() {
        for (ProgramDom<?> dom : generatedDoms) {
            dom.save();
        }
    }

    private void openRelations() {
        for (ProgramRel rel : generatedRels) {
            rel.init();
        }
    }

    private void saveRelations() {
        for (ProgramRel rel : generatedRels) {
            rel.save();
            rel.close();
        }
    }

}
