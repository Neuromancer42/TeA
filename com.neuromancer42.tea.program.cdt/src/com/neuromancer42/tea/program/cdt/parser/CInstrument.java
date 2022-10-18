package com.neuromancer42.tea.program.cdt.parser;

import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.program.cdt.memmodel.object.IMemObj;
import com.neuromancer42.tea.program.cdt.memmodel.object.StackObj;
import com.neuromancer42.tea.program.cdt.parser.cfg.EvalNode;
import com.neuromancer42.tea.program.cdt.parser.cfg.ICFGNode;
import com.neuromancer42.tea.program.cdt.parser.evaluation.IEval;
import com.neuromancer42.tea.program.cdt.parser.evaluation.IndirectCallEval;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.ICASTKnRFunctionDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTName;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.parser.c.CNodeFactory;

import java.util.*;
//import org.eclipse.cdt.internal.core.dom.rewrite.ASTModification;
//import org.eclipse.cdt.internal.core.dom.rewrite.ASTModificationStore;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
//import org.eclipse.cdt.internal.core.dom.rewrite.changegenerator.ChangeGenerator;
//import org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap;

public class CInstrument {
    private final CASTTranslationUnit tu;

//    private final ASTModificationStore modStore;
    private final Map<IASTNode, IASTNode> modMap = new LinkedHashMap<>();

    public CInstrument(CASTTranslationUnit tu) {
        this.tu = tu;
//        this.modStore = new ASTModificationStore();
    }

    private final IndexMap<IASTNode> instrPositions = new IndexMap<>();

    private int genInstrumentId(IASTNode astNode) {
        if (instrPositions.contains(astNode)) {
            Messages.error("CInstrument: position [%s](line#%d) already instrumented", astNode.getRawSignature(), astNode.getFileLocation().getStartingLineNumber());
            return -1;
        }
        instrPositions.add(astNode.getOriginalNode());
        return instrPositions.indexOf(astNode.getOriginalNode());
    }

    public int instrumentBeforeLoad(IEval eval) {
        if (eval instanceof IndirectCallEval) {
            IndirectCallEval indCall = (IndirectCallEval) eval;
            IASTFunctionCallExpression callExpr = (IASTFunctionCallExpression) indCall.getExpression();
            IASTExpression fNameExpr = callExpr.getFunctionNameExpression();
            int instrId = genInstrumentId(fNameExpr);
            if (instrId == -1) return -1;
            IASTExpression newFNameExpr = wrapPeekExpr(instrId, fNameExpr);
            modMap.put(fNameExpr, newFNameExpr);
            return instrId;
        }
        return -1;
    }

    public int instrumentEnterMethod(IFunction meth) {
        IASTName[] decls = tu.getDeclarationsInAST(meth);
        for (IASTName decl: decls) {
            if (decl.getParent() instanceof IASTFunctionDeclarator || decl.getParent() instanceof ICASTKnRFunctionDeclarator) {
                if (decl.getParent().getParent() instanceof IASTFunctionDefinition) {
                    IASTFunctionDefinition fDef = (IASTFunctionDefinition) decl.getParent().getParent();
                    IASTCompoundStatement fBody = (IASTCompoundStatement) fDef.getBody();
                    int instrId = genInstrumentId(fBody);
                    IASTCompoundStatement newBody = peekVarEnterBlock(instrId, meth.getNameCharArray(), fBody);
                    modMap.put(fBody, newBody);
                    return instrId;
                }
            }
        }
        return -1;
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
    private static IASTExpression wrapPeekExpr(int instrId, IASTExpression expr) {
        IASTName fName = new CASTName("peek".toCharArray());
        IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

        IASTLiteralExpression instrIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(instrId));
        IASTInitializerClause[] argList = new IASTInitializerClause[]{instrIdExpr, expr.copy(IASTNode.CopyStyle.withLocations)};

        IASTFunctionCallExpression callExpr = CNodeFactory.getDefault().newFunctionCallExpression(fNameExpr, argList);
        return callExpr;
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
        modMap.put(origExpr, newExpr);

        return instrPositions.indexOf(origExpr.getOriginalNode());
    }

    public void instrumentPeekVar(IVariable variable, IASTExpression origExpr) {
        IASTExpression newExpr = peekVarBeforeExpression(variable, origExpr);
        Messages.debug("CParser: instrumented expr {%s}", (new ASTWriter()).write(newExpr));
//        ASTModification mod = new ASTModification(ASTModification.ModificationKind.REPLACE, origExpr, newExpr, );
//        modStore.storeModification(null, mod);
        modMap.put(origExpr, newExpr);
    }

    public CASTTranslationUnit instrumented() {
//        ChangeGenerator changeGenerator = new ChangeGenerator(modStore, new NodeCommentMap());
//        changeGenerator.generateChange(tu);
        tu.accept(new InstrVisitor());
//        tu.addDeclaration(genPeekVarFunction());
        return tu;
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

    public class InstrVisitor extends ASTVisitor {
        public InstrVisitor() {
            this.shouldVisitExpressions = true;
            this.shouldVisitInitializers = true;
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
            return super.visit(expression);
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
        int iInstrId = instrumentBeforeLoad(eval);
        int mInstrId = instrumentEnterMethod(meth);
        return false;
    }

    private ProgramDom<IEval> domI;
    private ProgramDom<IFunction> domM;
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

class Trace {
    public static final int BEFORE_INVOKE = 1;
    public static final int ENTER_METHOD = 2;
    private final int typeId;
    private final int[] contents;
    public Trace(int[] traceLine) {
        typeId = traceLine[0];
        contents = new int[traceLine.length - 1];
        System.arraycopy(traceLine, 1, contents, 0, traceLine.length - 1);
    }

    public int getType() {
        return typeId;
    }

    public int getContent(int i) {
        if (i >= contents.length) {
            Messages.error("Trace %d: index %d out of content length %d, return -1", hashCode(), i, contents.length);
            return -1;
        } else {
            return contents[i];
        }
    }
}