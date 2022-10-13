package com.neuromancer42.tea.program.cdt.parser;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.program.cdt.memmodel.object.IMemObj;
import com.neuromancer42.tea.program.cdt.memmodel.object.StackObj;
import com.neuromancer42.tea.program.cdt.parser.cfg.EvalNode;
import com.neuromancer42.tea.program.cdt.parser.cfg.ICFGNode;
import com.neuromancer42.tea.program.cdt.parser.evaluation.IEval;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTName;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.parser.c.CNodeFactory;

import java.util.LinkedHashMap;
import java.util.Map;
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

    // TODO: check variable is visible in current point
    public boolean tryInstrumentMemObj(ICFGNode cfgNode, IMemObj memObj) {
        if (!(cfgNode instanceof EvalNode)) {
            Messages.error("CParser: Instrumenting non-expression position");
            return false;
        }
        EvalNode evalNode = (EvalNode) cfgNode;

        if (!(memObj instanceof StackObj) || !((StackObj) memObj).observable())
            return false;

        IVariable variable = ((StackObj) memObj).getVariable();
        Messages.debug("CParser: peek value of variable [%s] at {%s}", variable, evalNode.toDebugString());

        IEval eval = evalNode.getEvaluation();
        IASTExpression origExpr = eval.getExpression();

        instrumentPeekVar(variable, origExpr);

        return true;
    }

    public void instrumentPeekVar(IVariable variable, IASTExpression origExpr) {
        IASTExpression newExpr = wrapPeekVar(variable, origExpr);
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

    private final IndexMap<IASTNode> peekedPositions = new IndexMap<>();
    public IASTExpression wrapPeekVar(IVariable variable, IASTExpression origExpr) {
        IASTName fName = new CASTName("peek".toCharArray());
        IASTIdExpression fNameExpr = CNodeFactory.getDefault().newIdExpression(fName);

        IASTName vName = new CASTName(variable.getNameCharArray());
        IASTIdExpression vNameExpr = CNodeFactory.getDefault().newIdExpression(vName);

        peekedPositions.add(origExpr.getOriginalNode());
        int peekId = peekedPositions.indexOf(origExpr.getOriginalNode());
        IASTLiteralExpression peekIdExpr = CNodeFactory.getDefault().newLiteralExpression(IASTLiteralExpression.lk_integer_constant, String.valueOf(peekId));
        IASTInitializerClause[] argList = new IASTInitializerClause[]{vNameExpr, peekIdExpr};

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
}
