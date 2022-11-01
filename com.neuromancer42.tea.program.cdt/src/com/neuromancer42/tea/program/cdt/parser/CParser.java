package com.neuromancer42.tea.program.cdt.parser;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.program.cdt.memmodel.object.IMemObj;
import com.neuromancer42.tea.program.cdt.memmodel.object.StackObj;
import com.neuromancer42.tea.program.cdt.parser.cfg.*;
import com.neuromancer42.tea.program.cdt.parser.evaluation.*;
import org.eclipse.cdt.codan.core.model.cfg.IBasicBlock;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTName;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.parser.c.CNodeFactory;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
import org.eclipse.core.runtime.CoreException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CParser {

    public final ProgramDom<IFunction> domM;
    public final ProgramDom<IBasicBlock> domP;
    public final ProgramDom<IEval> domE;
    public final ProgramDom<Integer> domV;
    public final ProgramDom<IField> domF;
    public final ProgramDom<IEval> domI;
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

    public final ProgramRel relExtMeth;
    public final ProgramRel relFuncRef;
    public final ProgramRel relMmethArg;
    public final ProgramRel relMmethRet;
    public final ProgramRel relEntryM;

    public final ProgramRel relVvalue;
    //public final ProgramRel relHvalue;

    public final ProgramDom<?>[] generatedDoms;
    public final ProgramRel[] generatedRels;
    private IASTTranslationUnit translationUnit = null;

    private String filename;

    public CParser() {
        domM = ProgramDom.createDom("M", IFunction.class);
        domP = ProgramDom.createDom("P", IBasicBlock.class);
        domE = ProgramDom.createDom("E", IEval.class);
        domV = ProgramDom.createDom("V", Integer.class);
        domF = ProgramDom.createDom("F", IField.class);
        domI = ProgramDom.createDom("I", IEval.class);
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
        relExtMeth = new ProgramRel("ExtMeth", domM);
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
                relExtMeth, relFuncRef, relMmethArg, relMmethRet, relEntryM,
                relVvalue
        };

    }


    public void run(String fileName, Map<String, String> definedSymbols, String[] includePaths) {
        this.filename = fileName;
        File sourceFile = new File(fileName);
        if (!sourceFile.isFile()) {
            Messages.fatal("CParser: the referenced path %s is not a source file", sourceFile.toString());
        }
        FileContent fileContent = FileContent.createForExternalFileLocation(fileName);
        IScannerInfo scannerInfo = new ScannerInfo(definedSymbols, includePaths);
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

        CFGBuilder builder = new CFGBuilder(translationUnit);
        builder.build();

        String dotName = sourceFile.getName() + ".dot";
        try {
            BufferedWriter bw = Files.newBufferedWriter(Paths.get(Config.v().workDirName).resolve(dotName), StandardCharsets.UTF_8);
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
        for (IFunction meth : builder.getFuncs()) {
            domM.add(meth);
            int numMargs = meth.getParameters().length;
            if (numMargs > maxNumArg)
                maxNumArg = numMargs;
            for (IVariable var : builder.getMethodVars(meth)) {
                domA.add(var);
            }
            CFGBuilder.IntraCFG cfg = builder.getIntraCFG(meth);
            if (cfg == null) {
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
                        domI.add(e);
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
        for (IFunction func : builder.getFuncs()) {
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
        for (IFunction meth : builder.getFuncs()) {
            if (meth.getName().contentEquals("main")) {
                Messages.debug("CParser: find entry method %s[%s]", meth.getClass().getSimpleName(), meth);
                relEntryM.add(meth);
            }
            CFGBuilder.IntraCFG cfg = builder.getIntraCFG(meth);
            if (cfg == null) {
                Messages.debug("CParser: external function %s[%s]", meth.getClass().getSimpleName(), meth);
                relExtMeth.add(meth);
                continue;
            }
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
                        relPinvk.add(p, e);
                        if (v >= 0) {
                            relIinvkRet.add(e, v);
                        } else {
                            Messages.debug("CParser: invocation has no ret-val [%s]", e.toDebugString());
                        }
                        int[] iArgRegs;
                        if (e instanceof StaticCallEval) {
                            IFunction func = ((StaticCallEval) e).getFunction();
                            relStaticCall.add(e, func);
                            iArgRegs = ((StaticCallEval) e).getArguments();
                        } else {
                            int funcReg = ((IndirectCallEval) e).getFunctionReg();
                            relIndirectCall.add(e, funcReg);
                            iArgRegs = ((IndirectCallEval) e).getArguments();
                        }

                        IASTInitializerClause[] argExprs = invk.getArguments();
                        assert iArgRegs.length == argExprs.length;
                        for (int i = 0; i < iArgRegs.length; ++i) {
                            relIinvkArg.add(e, i, iArgRegs[i]);
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

    public CInstrument getInstrument() {
        return new CInstrument();
    }

    public class CInstrument {
        private final CASTTranslationUnit instrTU = (CASTTranslationUnit) translationUnit.copy(IASTNode.CopyStyle.withLocations);

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

        public int instrumentBeforeInvoke(IEval eval) {
            if (eval instanceof IndirectCallEval) {
                IndirectCallEval indCall = (IndirectCallEval) eval;
                IASTFunctionCallExpression callExpr = (IASTFunctionCallExpression) indCall.getExpression();
                IASTExpression fNameExpr = callExpr.getFunctionNameExpression();
                int instrId = genInstrumentId(fNameExpr);
                if (instrId == -1) return -1;
                IASTExpression newFNameExpr = wrapPeekExpr(instrId, fNameExpr);
                modMap.put(fNameExpr.getOriginalNode(), newFNameExpr);
                Messages.debug("CInstrument: instrumenting function name expression [%s]#%d (original: %d)", new ASTWriter().write(fNameExpr.getOriginalNode()), fNameExpr.hashCode(), fNameExpr.getOriginalNode().hashCode());
                return instrId;
            }
            return -1;
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

        public int instrumentEnterMethod(int mId) {
            return instrumentEnterMethod(domM.get(mId));
        }

        private IASTCompoundStatement peekVarEnterBlock(int instrId, char[] nameCharArray, IASTCompoundStatement fBody) {
            IASTName fName = new CASTName("peek".toCharArray());
            IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

            IASTLiteralExpression instrIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(instrId));
            IASTName vName = new CASTName(nameCharArray);
            IASTIdExpression vNameExpr = CNodeFactory.getDefault().newIdExpression(vName);

            IASTInitializerClause[] argList = new IASTInitializerClause[]{instrIdExpr, vNameExpr};
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
        private IASTExpression wrapPeekExpr(int instrId, IASTExpression expr) {
            IASTName fName = new CASTName("peek".toCharArray());
            IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

            IASTLiteralExpression instrIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(instrId));
            IASTInitializerClause[] argList = new IASTInitializerClause[]{instrIdExpr, expr.copy(IASTNode.CopyStyle.withLocations)};

            IASTFunctionCallExpression callExpr = CNodeFactory.getDefault().newFunctionCallExpression(fNameExpr, argList);
            IASTExpressionList exprList = CNodeFactory.getDefault().newExpressionList();
            exprList.addExpression(callExpr);
            exprList.addExpression(expr.copy(IASTNode.CopyStyle.withLocations));
            IASTExpression wrapExpr = CNodeFactory.getDefault().newUnaryExpression(IASTUnaryExpression.op_bracketedPrimary, exprList);
            return wrapExpr;
        }

        // TODO: check variable is visible in current point
        public int instrumentBeforeLoad(ICFGNode cfgNode, IMemObj memObj) {
            if (!(cfgNode instanceof EvalNode)) {
                Messages.error("CParser: Instrumenting non-expression position");
                return -1;
            }
            EvalNode evalNode = (EvalNode) cfgNode;

            if (!(memObj instanceof StackObj) || !((StackObj) memObj).observable())
                return -1;

            IVariable variable = ((StackObj) memObj).getVariable();
            Messages.debug("CParser: peek value of variable [%s] at {%s}", variable, evalNode.toDebugString());

            IEval eval = evalNode.getEvaluation();
            IASTExpression origExpr = eval.getExpression();

            IASTExpression newExpr = peekVarBeforeExpression(variable, origExpr);
            Messages.debug("CParser: instrumented expr {%s}", (new ASTWriter()).write(newExpr));
    //        ASTModification mod = new ASTModification(ASTModification.ModificationKind.REPLACE, origExpr, newExpr, );
    //        modStore.storeModification(null, mod);
            modMap.put(origExpr.getOriginalNode(), newExpr);

            return instrPositions.indexOf(origExpr.getOriginalNode());
        }

        public void instrumentPeekVar(IVariable variable, IASTExpression origExpr) {
            IASTExpression newExpr = peekVarBeforeExpression(variable, origExpr);
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
                lines.add("extern int printf(const char*restrict  __format, ...); \n" +
                        "void* peek (int id, void *ptr) { \n" +
                        "        printf(\"peek\t%d\t%ld\\n\", id, (long) ptr); \n" +
                        "        return ptr; \n" +
                        "}\n");
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

        public IASTExpression peekVarBeforeExpression(IVariable variable, IASTExpression origExpr) {
            IASTName fName = new CASTName("peek".toCharArray());
            IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

            IASTName vName = new CASTName(variable.getNameCharArray());
            IASTIdExpression vNameExpr = CNodeFactory.getDefault().newIdExpression(vName);

            instrPositions.add(origExpr.getOriginalNode());
            int peekId = instrPositions.indexOf(origExpr.getOriginalNode());
            IASTLiteralExpression peekIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(peekId));
            IASTInitializerClause[] argList = new IASTInitializerClause[]{peekIdExpr, vNameExpr};

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
                if (expression instanceof IASTBinaryExpression) {
                    IASTBinaryExpression binExpr = (IASTBinaryExpression) expression;
                    IASTNode origNode = binExpr.getOperand2().getOriginalNode();
                    if (binExpr.getOperator() == IASTBinaryExpression.op_assign && modMap.containsKey(origNode)) {
                        IASTNode newRhs = modMap.get(origNode);
                        binExpr.setOperand2((IASTExpression) newRhs);
                        newRhs.setParent(binExpr);
                    }
                    if (binExpr.getOperator() == IASTBinaryExpression.op_divide && modMap.containsKey(origNode)) {
                        IASTNode newDivider = modMap.get(origNode);
                        binExpr.setOperand2((IASTExpression) newDivider);
                        newDivider.setParent(binExpr);
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

        public Set<String> getInstructableRelations() {
            return Set.of("ci_IM");
        }

        public boolean instrument(ProgramRel rel, Tuple tuple) {
            assert rel.getName().equals(tuple.getRelName());
            switch (rel.getName()) {
                case "ci_IM":
                    return instrumentCIIM(tuple.getIndices());
            }
            return false;
        }

        private boolean instrumentCIIM(int[] indices) {
            assert indices.length == 2;
            IEval eval = domI.get(indices[0]);
            IFunction meth = domM.get(indices[1]);
            int iInstrId = instrumentBeforeInvoke(eval);
            int mInstrId = instrumentEnterMethod(meth);
            return false;
        }

        private List<Tuple> provedTuples;
        private List<Tuple> deniedTuples;
        private void processTraceCIIM(List<Trace> traces) {
            Map<Integer, Integer> methAddrMap = new LinkedHashMap<>();
            for (Trace trace: traces) {
                if (trace.getType() == Trace.BEFORE_INVOKE) {
                    int methId = trace.getContent(0);
                    int methAddr = trace.getContent(1);
                    methAddrMap.put(methAddr, methId);
                }
            }
            for (Trace trace: traces) {
                if (trace.getType() == Trace.ENTER_METHOD) {
                    int invkId = trace.getContent(0);
                    int methAddr = trace.getContent(1);
                    int methId = methAddrMap.get(methAddr);
                    provedTuples.add(new Tuple("ci_IM", invkId, methId));
                }
            }
        }


    }
}
