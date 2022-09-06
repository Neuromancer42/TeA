package com.neuromancer42.tea.program.cdt.internal;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.internal.cfg.*;
import com.neuromancer42.tea.program.cdt.internal.evaluation.IEval;
import com.neuromancer42.tea.program.cdt.internal.evaluation.IndirectCallEval;
import com.neuromancer42.tea.program.cdt.internal.evaluation.LoadEval;
import com.neuromancer42.tea.program.cdt.internal.evaluation.StaticCallEval;
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
    public final ProgramDom<ILocation> domH;
    public final ProgramDom<IField> domF;
    public final ProgramDom<IASTFunctionCallExpression> domI;
    public final ProgramDom<Integer> domZ;

    public final ProgramRel relMPentry;
    public final ProgramRel relMPexit;
    public final ProgramRel relPPdirect;
    public final ProgramRel relPPtrue;
    public final ProgramRel relPPfalse;

    public final ProgramRel relPeval;
    public final ProgramRel relEinvk;

    public final ProgramRel relAlloc;
    public final ProgramRel relGlobalAlloc;
    public final ProgramRel relLoadPtr;
    public final ProgramRel relLoadFld;
    public final ProgramRel relStorePtr;
    public final ProgramRel relStoreFld;

    public final ProgramRel relIinvkArg;
    public final ProgramRel relIinvkRet;
    public final ProgramRel relIndirectCall;
    public final ProgramRel relStaticCall;

    public final ProgramRel relMmethArg;
    public final ProgramRel relMmethRet;
    public final ProgramRel relFuncPtr;
    public final ProgramRel relEntryM;


    public final ProgramDom<?>[] generatedDoms;
    public final ProgramRel[] generatedRels;

    private final String fileName;

    public CParser(String fileName) {
        this.fileName = fileName;
        domM = ProgramDom.createDom("M", IFunction.class);
        domP = ProgramDom.createDom("P", IBasicBlock.class);
        domE = ProgramDom.createDom("E", IEval.class);
        domV = ProgramDom.createDom("V", Integer.class);
        domH = ProgramDom.createDom("H", ILocation.class);
        domF = ProgramDom.createDom("F", IField.class);
        domI = ProgramDom.createDom("I", IASTFunctionCallExpression.class);
        domZ = ProgramDom.createDom("Z", Integer.class);

        generatedDoms = new ProgramDom<?>[]{domM, domP, domE, domV, domH, domF, domI, domZ};

        // control flow relations
        relMPentry = ProgramRel.createRel("MPentry", new ProgramDom[]{domM, domP});
        relMPexit = ProgramRel.createRel("MPexit", new ProgramDom[]{domM, domP});
        relPPdirect = ProgramRel.createRel("PPdirect", new ProgramDom[]{domP, domP});
        relPPtrue = ProgramRel.createRel("PPtrue", new ProgramDom[]{domP, domP, domV});
        relPPfalse = ProgramRel.createRel("PPfalse", new ProgramDom[]{domP, domP, domV});

        // statements
        relPeval = ProgramRel.createRel("Peval", new ProgramDom[]{domP, domV, domE});
        relEinvk = ProgramRel.createRel("Einvk", new ProgramDom[]{domE, domI});
        relAlloc = ProgramRel.createRel("Alloc", new ProgramDom[]{domV, domH});
        relGlobalAlloc = ProgramRel.createRel("GlobalAlloc", new ProgramDom[]{domV, domH});
        relLoadPtr = ProgramRel.createRel("LoadPtr", new ProgramDom[]{domV, domV});
        relLoadFld = ProgramRel.createRel("LoadFld", new ProgramDom[]{domV, domV, domF});
        relStorePtr = ProgramRel.createRel("StorePtr", new ProgramDom[]{domV, domV});
        relStoreFld = ProgramRel.createRel("StoreFld", new ProgramDom[]{domV, domF, domV});

        // invocations
        relIinvkArg = ProgramRel.createRel("IinvkArg", new ProgramDom[]{domI, domZ, domV});
        relIinvkRet = ProgramRel.createRel("IinvkRet", new ProgramDom[]{domI, domV});
        relIndirectCall = ProgramRel.createRel("IndirectCall", new ProgramDom[]{domI, domV});
        relStaticCall = ProgramRel.createRel("StaticCall", new ProgramDom[]{domI, domM});

        // methods
        relMmethArg = ProgramRel.createRel("MmethArg", new ProgramDom[]{domM, domZ, domV});
        relMmethRet = ProgramRel.createRel("MmethRet", new ProgramDom[]{domM, domV});
        relFuncPtr = ProgramRel.createRel("funcPtr", new ProgramDom[]{domH, domM});
        relEntryM = ProgramRel.createRel("entryM", new ProgramDom[]{domM});

        generatedRels = new ProgramRel[]{
                relMPentry, relMPexit, relPPdirect, relPPtrue, relPPfalse,
                relPeval, relEinvk, relAlloc, relGlobalAlloc, relLoadPtr, relLoadFld, relStorePtr, relStoreFld,
                relIinvkArg, relIinvkRet, relIndirectCall, relStaticCall,
                relMmethArg, relMmethRet, relFuncPtr, relEntryM
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

        IntraCFGBuilder builder = new IntraCFGBuilder(translationUnit);
        Map<IFunction, IntraCFG> methCFGMap = new HashMap<>();
        for (var decl: translationUnit.getDeclarations()) {
            if (decl instanceof IASTFunctionDefinition) {
                var fDef = (IASTFunctionDefinition) decl;
                var f = (IFunction) fDef.getDeclarator().getName().resolveBinding();
                var cfg = builder.build(fDef);
                methCFGMap.put(f, cfg);
            }
        }

        String dotName = sourceFile.getName() + ".dot";
        try {
            BufferedWriter bw = Files.newBufferedWriter(Path.of(dotName), StandardCharsets.UTF_8);
            PrintWriter pw = new PrintWriter(bw);
            pw.println("digraph \"" + sourceFile.getName() + "\" {");
            for (IFunction meth : methCFGMap.keySet()) {
                IntraCFG cfg = methCFGMap.get(meth);
                cfg.dumpDotString(pw);
            }
            pw.println("}");
            pw.flush();
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
        for (var vLoc : builder.getStackMap().values()) {
            assert (vLoc.isStatic());
            domH.add(vLoc);
        }
        for (var fLoc : builder.getFuncNameMap().values()) {
            assert (fLoc.isStatic());
            domH.add(fLoc);
        }
        int maxNumArg = 0;
        for (var entry : methCFGMap.entrySet()) {
            IFunction meth = entry.getKey();
            domM.add(meth);
            int numMargs = meth.getParameters().length;
            if (numMargs > maxNumArg)
                maxNumArg = numMargs;

            IntraCFG cfg = entry.getValue();
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
        domF.addAll(builder.getFields());
        saveDomains();

        openRelations();
        for (var entry : builder.getStackMap().entrySet()) {
            IVariable variable = entry.getKey();
            ILocation vLoc = entry.getValue();
            int vReg = builder.getRefReg(variable);
            relAlloc.add(vReg, vLoc);
        }
        for (var entry : builder.getGlobalAllocs().entrySet()) {
            int reg = entry.getKey();
            ILocation loc = entry.getValue();
            relGlobalAlloc.add(reg, loc);
        }
        for (var entry : builder.getFuncNameMap().entrySet()) {
            IFunction func = entry.getKey();
            ILocation fLoc = entry.getValue();
            relFuncPtr.add(fLoc, func);
        }
        for (var entry : methCFGMap.entrySet()) {
            IFunction meth = entry.getKey();
            if (meth.getName().contentEquals("main")) {
                relEntryM.add(meth);
            }
            IntraCFG cfg = entry.getValue();
            relMPentry.add(meth, cfg.getStartNode());
            int[] mArgRegs = builder.getFuncArgs(meth);
            for (int i = 0; i < mArgRegs.length; ++i) {
                relMmethArg.add(meth, i, mArgRegs[i]);
            }
            Collection<IBasicBlock> nodes = cfg.getNodes();
            nodes.removeAll(cfg.getDeadNodes());
            for (var p : nodes) {
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
                    relPeval.add(p, v, e);
                    if (e instanceof StaticCallEval || e instanceof IndirectCallEval) {
                        IASTFunctionCallExpression invk = (IASTFunctionCallExpression) e.getExpression();
                        relEinvk.add(e, invk);
                        int retReg = builder.fetchRegister(invk);
                        relIinvkRet.add(invk, retReg);
                        IASTInitializerClause[] args = invk.getArguments();
                        for (int i = 0; i < args.length; ++i) {
                            int iArgReg = builder.fetchRegister((IASTExpression) args[i]);
                            relIinvkArg.add(invk, i, iArgReg);
                        }
                        if (e instanceof StaticCallEval) {
                            IFunction func = ((StaticCallEval) e).getFunction();
                            relStaticCall.add(invk, func);
                        } else {
                            int funcReg = ((IndirectCallEval) e).getFunctionReg();
                            relIndirectCall.add(invk, funcReg);
                        }
                    } else if (e instanceof LoadEval) {
                        ILocation loc = ((LoadEval) e).getLocation();
                        if (loc instanceof PointerLocation) {
                            int u = ((PointerLocation) loc).getRegister();
                            relLoadPtr.add(v, u);
                        } else if (loc instanceof VariableLocation) {
                            IVariable variable = ((VariableLocation) loc).getVariable();
                            int u = builder.getRefReg(variable);
                            relLoadPtr.add(v, u);
                        } else if (loc instanceof FunctionLocation) {
                            IFunction func = ((FunctionLocation) loc).getFunc();
                            int u = builder.getRefReg(func);
                            relLoadPtr.add(v, u);
                        } else {
                            Messages.warn("CParser: unhandled location type: %s", loc.toDebugString());
                        }
                    }
                } else if (p instanceof StoreNode) {
                    IBasicBlock q = ((StoreNode) p).getOutgoing();
                    relPPdirect.add(p, q);
                    ILocation loc = ((StoreNode) p).getLocation();
                    int v = ((StoreNode) p).getRegister();
                    if (loc instanceof PointerLocation) {
                        int u = ((PointerLocation) loc).getRegister();
                        relStorePtr.add(u, v);
                    } else if (loc instanceof VariableLocation) {
                        IVariable variable = ((VariableLocation) loc).getVariable();
                        int u = builder.getRefReg(variable);
                        relStorePtr.add(u, v);
                    } else if (loc instanceof FunctionLocation) {
                        IFunction func = ((FunctionLocation) loc).getFunc();
                        int u = builder.getRefReg(func);
                        relStorePtr.add(u, v);
                    } else {
                        Messages.warn("CParser: unhandled location type: %s", loc.toDebugString());
                    }
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
