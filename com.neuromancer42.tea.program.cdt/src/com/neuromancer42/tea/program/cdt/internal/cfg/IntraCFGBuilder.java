package com.neuromancer42.tea.program.cdt.internal.cfg;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.program.cdt.internal.evaluation.*;
import com.neuromancer42.tea.program.cdt.internal.memory.*;
import org.eclipse.cdt.codan.core.model.cfg.*;
import org.eclipse.cdt.codan.internal.core.cfg.*;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.ICASTKnRFunctionDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.c.CBasicType;

import java.util.*;

// general purpose
// translate the complex source code into a control flow graph
// each edge in the graph represents a simple computation of register values
// 1. arithmetical computations
// 2. read variables / read from memory locations
// 3. write variables / store to memory locations
// 4. computation with controls: logical and/or, ternary expression
// 5. function calls
// 6. returns
// 7. control statements: if, while, do-while, for, switch
// 8. declarators

// generally, this control flow is just a determinated variety of C specification
// in that: some evaluation orders are unspecified
// e.g. operands of arithmetical computations, arguments of function calls
public class IntraCFGBuilder {
    private final IASTTranslationUnit transUnit;
    private final IndexMap<Object> registers; // sub-expressions should compute from registers and load into registers
    private final Map<IVariable, ILocation> stackMap;
    private final Map<IFunction, ILocation> funcNameMap;
    private final Map<IFunction, int[]> funcArgsMap;
    private final Map<IBinding, Integer> refRegMap;
    private final Map<IASTExpression, IFunction> staticInvkMap;
    private final Map<IASTExpression, ILocation> lvalLocMap;
    private final Map<String, ILocation> stringConstants;

    private final Map<Integer, ILocation> globalAllocs;
    private final Set<IField> fields;
    // TODO: use struct signatures rather than statement references to collect fields

    private IStartNode start;
    private List<IExitNode> exits;
    private List<IBasicBlock> unreachable;
    private IConnectorNode outerContinueTarget;
    private boolean outerContinueBackward;
    private IConnectorNode outerBreakTarget;

    public IntraCFGBuilder(IASTTranslationUnit tu) {
        this.transUnit = tu;
        registers = new IndexMap<>();
        stackMap = new HashMap<>();
        fields = new HashSet<>();
        funcNameMap = new HashMap<>();
        funcArgsMap = new HashMap<>();
        globalAllocs = new HashMap<>();
        refRegMap = new HashMap<>();
        staticInvkMap = new HashMap<>();
        lvalLocMap = new HashMap<>();
        stringConstants = new HashMap<>();
        for (IASTDeclaration decl : tu.getDeclarations()) {
            // add declared variables and functions into registers
            if (decl instanceof IASTFunctionDefinition) {
                IASTDeclarator dtor = ((IASTFunctionDefinition) decl).getDeclarator();
                IASTName fName = dtor.getName();
                IFunction func = (IFunction) fName.resolveBinding();
                registers.add(func);
                int fReg = registers.indexOf(func);
                ILocation fLoc = new FunctionLocation(func);
                funcNameMap.put(func, fLoc);
                globalAllocs.put(fReg, fLoc);
                refRegMap.put(func, fReg);
                Messages.debug("CParser: function %s@%d:%s[%s] stores itself in %s", func.getType(), fReg, func.getClass().getSimpleName(), func, fLoc.toDebugString());
                if (func.getParameters() == null) {
                    Messages.debug("CParser: function %s[%s] has no argument", func.getClass().getSimpleName(), func);
                } else {
                    // TODO: var-arg?
                    int[] argRegs = new int[func.getParameters().length];
                    for (int i = 0; i < func.getParameters().length; ++i) {
                        IParameter arg = func.getParameters()[i];
                        registers.add(arg);
                        int aReg = registers.indexOf(arg);
                        argRegs[i] = aReg;
                        Messages.debug("CParser: reference %s[%s]'s %d-th argument %s[%s] by %s@%d", func.getClass().getSimpleName(), func, i, arg.getClass().getSimpleName(), arg, arg.getType(), aReg);
                    }
                    funcArgsMap.put(func, argRegs);
                }
            }
        }
    }

    public IndexMap<Object> getRegisters() {
        return registers;
    }

    public Map<IVariable, ILocation> getStackMap() {
        return stackMap;
    }

    public Map<IFunction, ILocation> getFuncNameMap() {
        return funcNameMap;
    }

    public Map<Integer, ILocation> getGlobalAllocs() {
        return globalAllocs;
    }

    public int[] getFuncArgs(IFunction meth) {
        int[] args = funcArgsMap.get(meth);
        if (args == null) {
            Messages.warn("CParser: function %s[%s] doesn't declare arguments", meth.getClass().getSimpleName(), meth);
            return new int[0];
        } else {
            return args;
        }
    }

    public int getRefReg(IBinding id) {
        Integer reg = refRegMap.get(id);
        if (reg == null) {
            Messages.fatal("CParser: referencing undeclared identifier %s[%s]", id.getClass().getSimpleName(), id);
            return -1;
        }
        return reg;
    }

    public Set<IField> getFields() { return fields; }

    public IntraCFG build(IASTFunctionDefinition fDef) {
        IASTFunctionDeclarator fDecl = fDef.getDeclarator();
        IFunction curFunc = (IFunction) fDecl.getName().resolveBinding();
        start = new FuncEntryNode(curFunc);
        exits = new ArrayList<>();
        outerContinueTarget = null;
        outerContinueBackward = true;
        outerBreakTarget = null;
        unreachable = new ArrayList<>();
        IBasicBlock prevNode = start;
        if (fDecl instanceof IASTStandardFunctionDeclarator) {
            IASTParameterDeclaration[] paramDecls = ((IASTStandardFunctionDeclarator) fDecl).getParameters();
            for (var paramDecl : paramDecls) {
                IASTDeclarator dtor = paramDecl.getDeclarator();
                IParameter param = (IParameter) dtor.getName().getBinding();
                int aReg = registers.indexOf(param);
                prevNode = handleDeclarator(prevNode, dtor, aReg);
            }
        } else if (fDecl instanceof ICASTKnRFunctionDeclarator) {
            IASTName[] paramNames = ((ICASTKnRFunctionDeclarator) fDecl).getParameterNames();
            for (var paramName : paramNames) {
                IASTDeclarator dtor = ((ICASTKnRFunctionDeclarator) fDecl).getDeclaratorForParameterName(paramName);
                IParameter param = (IParameter) paramName.resolveBinding();
                int aReg = registers.indexOf(param);
                prevNode = handleDeclarator(prevNode, dtor, aReg);
            }
        }
        IASTStatement fBody = fDef.getBody();
        prevNode = expandGraph(prevNode, fBody);
        if (prevNode != null && !(prevNode instanceof IExitNode) && !(prevNode instanceof IJumpNode) && !unreachable.contains(prevNode)) {
            ReturnNode dummyRet = new ReturnNode();
            Messages.debug("CParser: add implicit return node [%s] for function [%s]", dummyRet.toDebugString(), curFunc);
            dummyRet.setStartNode(start);
            connect(prevNode, dummyRet);
            exits.add(dummyRet);
        }
        IntraCFG cfg = new IntraCFG(curFunc, start, exits);
        cfg.setUnconnectedNodes(unreachable);
        return cfg;
    }

    private IBasicBlock expandGraph(IBasicBlock prevNode, IASTStatement statement) {
        if (statement == null || statement instanceof IASTNullStatement) {
            return prevNode;
        } else if (statement instanceof IASTCompoundStatement) {
            IASTStatement[] subStatements = ((IASTCompoundStatement) statement).getStatements();
            for (IASTStatement subStatement : subStatements) {
                prevNode = expandGraph(prevNode, subStatement);
            }
            return prevNode;
        } else if (statement instanceof IASTExpressionStatement) {
            IASTExpression expr = unparenthesize(((IASTExpressionStatement) statement).getExpression());
            return handleExpression(prevNode, expr);
        } else if (statement instanceof IASTDeclarationStatement) {
            IASTDeclaration decl = ((IASTDeclarationStatement) statement).getDeclaration();
            if (decl instanceof IASTSimpleDeclaration) {
                IASTDeclarator[] dtors = ((IASTSimpleDeclaration) decl).getDeclarators();
                for (IASTDeclarator dtor: dtors) {
                    IASTInitializer initializer = dtor.getInitializer();
                    if (initializer == null) {
                        prevNode = handleDeclarator(prevNode, dtor, null);
                    } else if (initializer instanceof IASTEqualsInitializer) {
                        IASTInitializerClause initClause = ((IASTEqualsInitializer) initializer).getInitializerClause();
                        IASTExpression initExpr = unparenthesize((IASTExpression) initClause);
                        int initReg = createRegister(initExpr);
                        prevNode = handleRvalue(prevNode, initExpr, initReg);
                        prevNode = handleDeclarator(prevNode, dtor, initReg);
                    } else {
                        Messages.error("CParser: skip unhandled declaration initializer %s[%s]", initializer.getClass().getSimpleName(), initializer.getRawSignature());
                    }
                }
                return prevNode;
            } else if (decl instanceof IASTFunctionDefinition) {
                Messages.fatal("CParser: do not support nested function definition");
            }
            Messages.error("CParser: skip unhandled declaration %s[%s]", decl.getClass().getSimpleName(), decl.getRawSignature());
        } else if (statement instanceof IASTIfStatement) {
            IASTIfStatement cIf = (IASTIfStatement) statement;
            prevNode = handleIfStatement(prevNode, cIf);

            return prevNode;
        } else if (statement instanceof IASTWhileStatement) {
            IASTWhileStatement cWhile = (IASTWhileStatement) statement;
            prevNode = handleWhileStatement(prevNode, cWhile);

            return prevNode;
        } else if (statement instanceof IASTDoStatement) {
            IASTDoStatement cDo = (IASTDoStatement) statement;
            prevNode = handleDoWhileStatement(prevNode, cDo);

            return prevNode;
        } else if (statement instanceof IASTForStatement) {
            IASTForStatement cFor = (IASTForStatement) statement;
            prevNode = handleForStatement(prevNode, cFor);

            return prevNode;
        } else if (statement instanceof IASTBreakStatement) {
            if (outerBreakTarget != null) {
                jump(prevNode, outerBreakTarget, false);
                return null;
            } else {
                Messages.warn("CParser: skip invalid %s in no loop or switch", statement.getClass().getSimpleName());
                return prevNode;
            }
        } else if (statement instanceof IASTContinueStatement) {
            if (outerContinueTarget != null) {
                jump(prevNode, outerContinueTarget, outerContinueBackward);
                return null;
            } else {
                Messages.warn("CParser: skip invalid %s in no loop", statement.getClass().getSimpleName());
                return prevNode;
            }
        } else if (statement instanceof IASTReturnStatement) {
            IASTReturnStatement ret = (IASTReturnStatement) statement;
            IASTExpression retExpr = ret.getReturnValue();
            ReturnNode retNode ;
            if (retExpr != null) {
                retExpr = unparenthesize(retExpr);
                int retReg = createRegister(retExpr);
                prevNode = handleRvalue(prevNode, retExpr, retReg);
                retNode = new ReturnNode(retExpr, retReg);
            } else {
                retNode = new ReturnNode();
            }
            retNode.setStartNode(start);
            exits.add(retNode);
            prevNode = connect(prevNode, retNode);

            return prevNode;
        }

        Messages.error("CParser: skip unsupported C statement %s[%s]", statement.getClass().getSimpleName(), statement.getRawSignature());
        return prevNode;
    }

    public IBasicBlock handleDeclarator(IBasicBlock prevNode, IASTDeclarator declarator, Integer initReg) {
        while (declarator.getNestedDeclarator() != null)
            declarator = declarator.getNestedDeclarator();
        IASTName varName = declarator.getName();
        IVariable var = (IVariable) varName.resolveBinding();
        if (stackMap.containsKey(var)) {
            Messages.warn("CParser: redeclare variable %s[%s] at %s[%s]", var.getType(), var.getName(), declarator.getClass().getSimpleName(), declarator.getRawSignature());
            return prevNode;
        } else {
            ILocation vLoc = new VariableLocation(var);
            stackMap.put(var, vLoc);

            registers.add(declarator);
            int vReg = registers.indexOf(declarator);
            refRegMap.put(var, vReg);
            Messages.debug("CParser: alloc stack location *(%s)@%d == &%s for %s#%d (%s)", var.getType(), vReg, vLoc.toDebugString(), var.getName(), var.hashCode(), var.getOwner());

            if (initReg != null) {
                Messages.debug("CParser: store initializing value to %s <- %s@%d", vLoc.toDebugString(), var.getType(), initReg);
                IBasicBlock storeNode = new StoreNode(vLoc, initReg);
                prevNode = connect(prevNode, storeNode);
            }
            return prevNode;
        }
    }

    private IBasicBlock handleExpression(IBasicBlock prevNode, IASTExpression expression) {
        if (expression instanceof IASTBinaryExpression) {
            IASTBinaryExpression binExpr = (IASTBinaryExpression) expression;
            int op = binExpr.getOperator();
            if (op == IASTBinaryExpression.op_assign) {
                IASTExpression lhs = unparenthesize(binExpr.getOperand1());
                prevNode = handleLvalue(prevNode, lhs);
                ILocation lhsLoc = lvalLocMap.get(lhs);

                IASTExpression rhs = unparenthesize(binExpr.getOperand2());
                int rReg = createRegister(rhs);
                prevNode = handleRvalue(prevNode, rhs, rReg);

                Messages.debug("CParser: assign to location %s <- %s@%d (%s)", lhsLoc.toDebugString(), rhs.getExpressionType(), rReg, expression.getRawSignature());
                IBasicBlock storeNode = new StoreNode(lhsLoc, rReg);
                prevNode = connect(prevNode, storeNode);

                return prevNode;
            } else if (binExpr.isLValue()) { // equivalent to op is all compound assignment operators (except op_assign)
                IASTExpression lval = unparenthesize(binExpr.getOperand1());
                prevNode = handleLvalue(prevNode, lval);
                ILocation lhsLoc = lvalLocMap.get(lval);

                int prevReg = createRegister(lval);
                Messages.debug("CParser: read from location %s -> %s@%d (%s)", lhsLoc.toDebugString(), lval.getExpressionType(), prevReg, expression.getRawSignature());
                IEval loadEval = new LoadEval(lval, lhsLoc);
                IBasicBlock loadNode = new EvalNode(loadEval, prevReg);
                prevNode = connect(prevNode, loadNode);

                IASTExpression rval = unparenthesize(binExpr.getOperand2());
                int rReg = createRegister(rval);
                prevNode = handleRvalue(prevNode, rval, rReg);

                int postReg = createRegister(expression);
                String opStr = "Unknown";
                switch (op) {
                    case IASTBinaryExpression.op_plusAssign: opStr = BinaryEval.op_plus; break;
                    case IASTBinaryExpression.op_minusAssign: opStr = BinaryEval.op_minus; break;
                    case IASTBinaryExpression.op_multiplyAssign: opStr = BinaryEval.op_multiply; break;
                    case IASTBinaryExpression.op_divideAssign: opStr = BinaryEval.op_divide; break;
                    case IASTBinaryExpression.op_moduloAssign: opStr = BinaryEval.op_modulo; break;
                    case IASTBinaryExpression.op_shiftLeftAssign:
                    case IASTBinaryExpression.op_shiftRightAssign:
                    case IASTBinaryExpression.op_binaryAndAssign:
                    case IASTBinaryExpression.op_binaryOrAssign:
                    case IASTBinaryExpression.op_binaryXorAssign:
                        opStr = BinaryEval.op_bit; break;
                }
                IEval eval = new BinaryEval(expression, opStr, prevReg, rReg);
                Messages.debug("CParser: compute updated value in %s@%d := %s", eval.getType(), postReg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, postReg);
                prevNode = connect(prevNode, evalNode);

                Messages.debug("CParser: update location %s <- %s@%d (%s)", lhsLoc.toDebugString(), expression.getExpressionType(), postReg, expression.getRawSignature());
                IBasicBlock storeNode = new StoreNode(lhsLoc, postReg);
                prevNode = connect(prevNode, storeNode);

                return prevNode;
            } else if (op == IASTBinaryExpression.op_logicalAnd || op == IASTBinaryExpression.op_logicalOr){
                prevNode = handleShortcircuitExpression(prevNode, binExpr);
                return prevNode;
            } else {
                Messages.warn("CParser: expression result un-used for [%s]", expression.getRawSignature());
                int reg = createRegister(expression);
                prevNode = handleRvalue(prevNode, expression, reg);
                return prevNode;
            }
        } else if (expression instanceof IASTUnaryExpression) {
            IASTUnaryExpression unaryExpr = (IASTUnaryExpression) expression;
            int op = unaryExpr.getOperator();
            if (op == IASTUnaryExpression.op_prefixIncr || op == IASTUnaryExpression.op_prefixDecr
                    || op == IASTUnaryExpression.op_postFixIncr || op == IASTUnaryExpression.op_postFixDecr
            ) {
                IASTExpression obj = unparenthesize(unaryExpr.getOperand());
                prevNode = handleLvalue(prevNode, obj);
                ILocation loc = lvalLocMap.get(obj);

                int prevReg = createRegister(obj);
                Messages.debug("CParser: read from location %s -> %s@%d (%s)", loc.toDebugString(), obj.getExpressionType(), prevReg, expression.getRawSignature());
                IEval loadEval = new LoadEval(obj, loc);
                IBasicBlock loadNode = new EvalNode(loadEval, prevReg);
                prevNode = connect(prevNode, loadNode);

                int postReg = createRegister(expression);
                String opStr = "Unknown";
                switch (op) {
                    case IASTUnaryExpression.op_prefixIncr:
                    case IASTUnaryExpression.op_postFixIncr:
                        opStr = UnaryEval.op_incr;
                        break;
                    case IASTUnaryExpression.op_prefixDecr:
                    case IASTUnaryExpression.op_postFixDecr:
                        opStr = UnaryEval.op_decr;
                        break;
                }
                IEval eval = new UnaryEval(expression, opStr, prevReg);
                Messages.debug("CParser: compute incr/decr value in %s@%d := %s", eval.getType(), postReg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, postReg);
                prevNode = connect(prevNode, evalNode);

                Messages.debug("CParser: incr/decr location %s <- %s@%d (%s)", loc.toDebugString(), expression.getExpressionType(), postReg, expression.getRawSignature());
                IBasicBlock storeNode = new StoreNode(loc, postReg);
                prevNode = connect(prevNode,storeNode);

                return prevNode;
            } else {
                Messages.warn("CParser: expression result un-used for [%s]", expression.getRawSignature());
                int reg = createRegister(expression);
                prevNode = handleRvalue(prevNode, expression, reg);
                return prevNode;
            }
        } else if (expression instanceof IASTFunctionCallExpression) {
            //int reg = createRegister(expression);
            if (!expression.getExpressionType().isSameType(CBasicType.VOID))
                Messages.debug("CParser: discard ret-val of expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
            prevNode = handleRvalue(prevNode, expression, -1);
            return prevNode;
        }
        Messages.error("CParser: skip unsupported %s [%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        return prevNode;
    }

    // Note: this method does not check if the lvalue is modifiable
    private IBasicBlock handleLvalue(IBasicBlock prevNode, IASTExpression expression) {
        if (expression instanceof IASTIdExpression) {
            // No need to compute target
            IBinding binding = ((IASTIdExpression) expression).getName().resolveBinding();
            ILocation loc = null;
            if (binding instanceof IVariable) {
                loc = stackMap.get((IVariable) binding);
            } else if (binding instanceof IFunction) {
                loc = funcNameMap.get((IFunction) binding);
            }
            if (loc == null) {
                Messages.fatal("CParser: referenced location not found for variable %s[%s]#%d (%s)", binding.getClass().getSimpleName(), binding.getName(), binding.hashCode(), binding.getOwner());
            }
            lvalLocMap.put(expression, loc);
            return prevNode;
        } else if (expression instanceof IASTUnaryExpression) {
            IASTUnaryExpression unaryExpr = (IASTUnaryExpression) expression;
            int op = unaryExpr.getOperator();
            IASTExpression innerExpr = unparenthesize(unaryExpr.getOperand());
            if (op == IASTUnaryExpression.op_star) {
                int innerReg = createRegister(innerExpr);
                prevNode = handleRvalue(prevNode, innerExpr, innerReg);
                ILocation loc = new PointerLocation(innerExpr, innerReg);
                lvalLocMap.put(expression, loc);
                return prevNode;
            } else if (op == IASTUnaryExpression.op_bracketedPrimary)
                Messages.fatal("CParser: brackets should have been unparenthesized for [%s]", expression.getRawSignature());
            Messages.fatal("CParser: modifying non-lvalue %s [%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        } else if (expression instanceof IASTBinaryExpression) {
            IASTBinaryExpression binaryExpr = (IASTBinaryExpression) expression;
            int op = binaryExpr.getOperator();
            if (op == IASTBinaryExpression.op_pmdot) {
                IASTExpression baseExpr = unparenthesize(binaryExpr.getOperand1());
                prevNode = handleLvalue(prevNode, baseExpr);
                ILocation baseLoc = lvalLocMap.get(baseExpr);

                IASTExpression fieldExpr = unparenthesize(binaryExpr.getOperand2());
                IField field = (IField) ((IASTIdExpression) fieldExpr).getName().resolveBinding();
                ILocation loc = new FieldLocation(baseLoc, field);
                lvalLocMap.put(expression, loc);
                return prevNode;
            } else if (op == IASTBinaryExpression.op_pmarrow) {
                IASTExpression ptrExpr = unparenthesize(binaryExpr.getOperand1());
                int ptrReg = createRegister(ptrExpr);
                prevNode = handleRvalue(prevNode, ptrExpr, ptrReg);
                ILocation baseLoc = new PointerLocation(ptrExpr, ptrReg);

                IASTExpression fieldExpr = unparenthesize(binaryExpr.getOperand2());
                IField field = (IField) ((IASTIdExpression) fieldExpr).getName().resolveBinding();

                ILocation loc = new FieldLocation(baseLoc, field);
                lvalLocMap.put(expression, loc);
                return prevNode;
            }
            Messages.fatal("CParser: modifying non-lvalue %s [%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        } else if (expression instanceof IASTArraySubscriptExpression) {
            IASTArraySubscriptExpression arraySubExpr = (IASTArraySubscriptExpression) expression;

            IASTExpression arrExpr = unparenthesize(arraySubExpr.getArrayExpression());
            int arrReg = createRegister(arrExpr);
            prevNode = handleRvalue(prevNode, arrExpr, arrReg);
            ILocation baseLoc = new PointerLocation(arrExpr, arrReg);

            IASTExpression subExpr = unparenthesize((IASTExpression) arraySubExpr.getArgument());
            int subReg = createRegister(subExpr);
            prevNode = handleRvalue(prevNode, subExpr, subReg);

            ILocation loc = new OffsetLocation(baseLoc, subExpr, subReg);
            lvalLocMap.put(expression, loc);
            return prevNode;
        } else if (expression instanceof IASTLiteralExpression) {
            IASTLiteralExpression literal = (IASTLiteralExpression) expression;
            if (literal.getKind() == IASTLiteralExpression.lk_string_literal) {
                String s = String.valueOf(literal.getValue());
                if (stringConstants.containsKey(s)) {
                    ILocation loc = stringConstants.get(s);
                    assert loc.getType().isSameType(literal.getExpressionType());
                    lvalLocMap.put(expression, loc);
                } else {
                    ILocation loc = new StringConstant(s, expression.getExpressionType());
                    stringConstants.put(s, loc);
                    lvalLocMap.put(expression, loc);
                }
                return prevNode;
            }
        }
        Messages.fatal("CParser: non-lvalue %s [%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        return null;
    }

    private IBasicBlock handleRvalue(IBasicBlock prevNode, IASTExpression expression, int reg) {
        if (expression instanceof IASTLiteralExpression) {
            IEval literalEval = new LiteralEval((IASTLiteralExpression) expression);
            IBasicBlock evalNode = new EvalNode(literalEval, reg);
            prevNode = connect(prevNode, evalNode);

            return prevNode;
        } else if (expression instanceof IASTIdExpression) {
            IBinding binding = ((IASTIdExpression) expression).getName().resolveBinding();
            if (binding instanceof IVariable) {
                // TODO: special handling of array-to-pointer conversion
                ILocation loc = stackMap.get((IVariable) binding);
                Messages.debug("CParser: read from location %s -> %s@%d (%s)", loc.toDebugString(), expression.getExpressionType(), reg, expression.getRawSignature());
                IEval loadEval = new LoadEval(expression, loc);
                IBasicBlock loadNode = new EvalNode(loadEval, reg);
                prevNode = connect(prevNode, loadNode);

                return prevNode;
            } else if (binding instanceof IFunction) {
                ILocation loc = funcNameMap.get((IFunction) binding);
                Messages.debug("CParser: store function %s -> %s@%d (%s)", loc.toDebugString(), expression.getExpressionType(), reg, expression.getRawSignature());
                IEval loadEval = new LoadEval(expression, loc);
                IBasicBlock loadNode = new EvalNode(loadEval, reg);
                prevNode = connect(prevNode, loadNode);

                return prevNode;
            }
        } else if (expression instanceof IASTUnaryExpression) {
            IASTUnaryExpression unaryExpr = (IASTUnaryExpression) expression;
            int op = unaryExpr.getOperator();
            IASTExpression inner = unparenthesize(unaryExpr.getOperand());
            if (op == IASTUnaryExpression.op_bracketedPrimary)
                Messages.fatal("CParser: brackets should have been unparenthesized for [%s]", expression.getRawSignature());
            else if (op == IASTUnaryExpression.op_star) {
                if (expression.getExpressionType() instanceof IFunctionType) {
                    Messages.debug("CParser: cancel dereference of function to location %s[%s] : %s@%s", expression.getClass().getSimpleName(), expression.getRawSignature(), expression.getExpressionType(), reg);
                    prevNode = handleRvalue(prevNode, inner, reg);
                    return prevNode;
                } else {
                    int innerReg = createRegister(inner);
                    prevNode = handleRvalue(prevNode, inner, innerReg);

                    ILocation loc = new PointerLocation(inner, innerReg);

                    Messages.debug("CParser: read from location %s -> %s@%d (%s)", loc.toDebugString(), expression.getExpressionType(), reg, expression.getRawSignature());
                    IEval loadEval = new LoadEval(expression, loc);
                    IBasicBlock loadNode = new EvalNode(loadEval, reg);
                    prevNode = connect(prevNode, loadNode);

                    return prevNode;
                }
            } else if (op == IASTUnaryExpression.op_plus || op == IASTUnaryExpression.op_minus || op == IASTUnaryExpression.op_not) {
                int innerReg = createRegister(inner);
                prevNode = handleRvalue(prevNode, inner, innerReg);
                String opStr = "Unknown";
                switch (op) {
                    case IASTUnaryExpression.op_plus: opStr = UnaryEval.op_plus; break;
                    case IASTUnaryExpression.op_minus: opStr = UnaryEval.op_minus; break;
                    case IASTUnaryExpression.op_not: opStr = UnaryEval.op_not; break;
                }
                IEval eval = new UnaryEval(expression, opStr, innerReg);
                Messages.debug("CParser: compute unary expr in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, reg);
                prevNode = connect(prevNode, evalNode);

                return prevNode;
            } else if (op == IASTUnaryExpression.op_prefixIncr || op == IASTUnaryExpression.op_prefixDecr
                    || op == IASTUnaryExpression.op_postFixIncr || op == IASTUnaryExpression.op_postFixDecr) {
                Messages.warn("CParser: side effect in right value %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());

                prevNode = handleExpression(prevNode, expression);

                return prevNode;
            } else if (op == IASTUnaryExpression.op_amper) {
                if (inner instanceof IASTUnaryExpression && ((IASTUnaryExpression) inner).getOperator() == IASTUnaryExpression.op_star) {
                    IASTExpression cancelledExpr = unparenthesize(((IASTUnaryExpression) inner).getOperand());
                    prevNode = handleRvalue(prevNode, cancelledExpr, reg);
                    return prevNode;
                } else if (inner instanceof IASTArraySubscriptExpression) {
                    IASTArraySubscriptExpression arraySubExpr = (IASTArraySubscriptExpression) inner;

                    IASTExpression arrExpr = unparenthesize(arraySubExpr.getArrayExpression());
                    int arrReg = createRegister(arrExpr);
                    prevNode = handleRvalue(prevNode, arrExpr, arrReg);

                    IASTExpression subExpr = unparenthesize((IASTExpression) arraySubExpr.getArgument());
                    int subReg = createRegister(subExpr);
                    prevNode = handleRvalue(prevNode, subExpr, subReg);

                    IEval eval = new BinaryEval(expression, BinaryEval.op_plus, arrReg, subReg);
                    IBasicBlock evalNode = new EvalNode(eval, reg);
                    prevNode = connect(prevNode, evalNode);

                    return prevNode;
                }

                prevNode = handleLvalue(prevNode, inner);
                ILocation loc = lvalLocMap.get(inner);
                IEval eval = new AddressEval(expression, loc);
                Messages.debug("CParser: compute address in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, reg);
                prevNode = connect(prevNode, evalNode);

                return prevNode;
            }
        } else if (expression instanceof IASTBinaryExpression) {
            IASTBinaryExpression binExpr = (IASTBinaryExpression) expression;
            int op = binExpr.getOperator();
            if (binExpr.isLValue()) {
                Messages.warn("CParser: side effect in right value %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
                prevNode = handleExpression(prevNode, expression);

                return prevNode;
            } else if (op == IASTBinaryExpression.op_pmdot) {
                IASTExpression baseExpr = unparenthesize(binExpr.getOperand1());
                prevNode = handleLvalue(prevNode, baseExpr);
                ILocation baseLoc = lvalLocMap.get(baseExpr);

                IASTExpression fieldExpr = unparenthesize(binExpr.getOperand2());
                IField field = (IField) ((IASTIdExpression) fieldExpr).getName().resolveBinding();
                ILocation loc = new FieldLocation(baseLoc, field);

                Messages.debug("CParser: read from location %s -> @%s (%s)", loc.toDebugString(), reg, expression.getRawSignature());
                IEval loadEval = new LoadEval(expression, loc);
                IBasicBlock loadNode = new EvalNode(loadEval, reg);
                prevNode = connect(prevNode, loadNode);

                return prevNode;
            } else if (op == IASTBinaryExpression.op_pmarrow) {
                IASTExpression ptrExpr = unparenthesize(binExpr.getOperand1());
                int ptrReg = createRegister(ptrExpr);
                prevNode = handleRvalue(prevNode, ptrExpr, ptrReg);
                ILocation baseLoc = new PointerLocation(ptrExpr, ptrReg);

                IASTExpression fieldExpr = unparenthesize(binExpr.getOperand2());
                IField field = (IField) ((IASTIdExpression) fieldExpr).getName().resolveBinding();
                ILocation loc = new FieldLocation(baseLoc, field);

                Messages.debug("CParser: read from location %s -> @%s (%s)", loc.toDebugString(), reg, expression.getRawSignature());
                IEval loadEval = new LoadEval(expression, loc);
                IBasicBlock loadNode = new EvalNode(loadEval, reg);
                prevNode = connect(prevNode, loadNode);

                return prevNode;
            } else if (op == IASTBinaryExpression.op_logicalAnd || op == IASTBinaryExpression.op_logicalOr) {
                prevNode = handleShortcircuitExpression(prevNode, binExpr);
                int r1 = fetchRegister(binExpr.getOperand1());
                int r2 = fetchRegister(binExpr.getOperand2());
                String opStr = "Unknown";
                switch (op) {
                    case IASTBinaryExpression.op_logicalAnd: opStr = BinaryEval.op_and; break;
                    case IASTBinaryExpression.op_logicalOr: opStr = BinaryEval.op_or; break;
                }
                IEval eval = new BinaryEval(expression, opStr, r1, r2);
                Messages.debug("CParser: compute result of and/or in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, reg);
                prevNode = connect(prevNode, evalNode);

                return prevNode;
            } else {
                // TODO: C standard does not specify the evaluation order of arithmetical expressions
                IASTExpression op1 = unparenthesize(binExpr.getOperand1());
                int r1 = createRegister(op1);
                prevNode = handleRvalue(prevNode, op1, r1);

                IASTExpression op2 = unparenthesize(binExpr.getOperand2());
                int r2 = createRegister(op2);
                prevNode = handleRvalue(prevNode, op2, r2);

                String opStr = "Unknown";
                switch (op) {
                    case IASTBinaryExpression.op_plus: opStr = BinaryEval.op_plus; break;
                    case IASTBinaryExpression.op_minus: opStr = BinaryEval.op_minus; break;
                    case IASTBinaryExpression.op_multiply: opStr = BinaryEval.op_multiply; break;
                    case IASTBinaryExpression.op_divide: opStr = BinaryEval.op_divide; break;
                    case IASTBinaryExpression.op_modulo: opStr = BinaryEval.op_modulo; break;
                    case IASTBinaryExpression.op_equals: opStr = BinaryEval.op_eq; break;
                    case IASTBinaryExpression.op_notequals: opStr = BinaryEval.op_ne; break;
                    case IASTBinaryExpression.op_lessThan: opStr = BinaryEval.op_lt; break;
                    case IASTBinaryExpression.op_greaterThan: {
                        opStr = BinaryEval.op_lt;
                        int rr = r1;
                        r1 = r2;
                        r2 = rr;
                        break;
                    }
                    case IASTBinaryExpression.op_lessEqual: opStr = BinaryEval.op_le; break;
                    case IASTBinaryExpression.op_greaterEqual: {
                        opStr = BinaryEval.op_le;
                        int rr = r1;
                        r1 = r2;
                        r2 = rr;
                        break;
                    }
                    case IASTBinaryExpression.op_shiftLeft:
                    case IASTBinaryExpression.op_shiftRight:
                    case IASTBinaryExpression.op_binaryAnd:
                    case IASTBinaryExpression.op_binaryOr:
                    case IASTBinaryExpression.op_binaryXor: opStr = BinaryEval.op_bit; break;
                }
                IEval eval = new BinaryEval(expression, opStr, r1, r2);
                Messages.debug("CParser: compute binary expr in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, reg);
                prevNode = connect(prevNode, evalNode);

                return prevNode;
            }
        } else if (expression instanceof IASTArraySubscriptExpression) {
            IASTArraySubscriptExpression arraySubExpr = (IASTArraySubscriptExpression) expression;

            IASTExpression arrExpr = unparenthesize(arraySubExpr.getArrayExpression());
            int arrReg = createRegister(arrExpr);
            prevNode = handleRvalue(prevNode, arrExpr, arrReg);

            IASTExpression subExpr = unparenthesize((IASTExpression) arraySubExpr.getArgument());
            int subReg = createRegister(subExpr);
            prevNode = handleRvalue(prevNode, subExpr, subReg);

            ILocation baseLoc = new PointerLocation(arrExpr, arrReg);

            ILocation loc = new OffsetLocation(baseLoc, subExpr, subReg);
            Messages.debug("CParser: read from location %s -> @%s (%s)", loc.toDebugString(), reg, expression.getRawSignature());
            IEval loadEval = new LoadEval(expression, loc);
            IBasicBlock loadNode = new EvalNode(loadEval, reg);
            prevNode = connect(prevNode, loadNode);

            return prevNode;
        } else if (expression instanceof IASTFunctionCallExpression) {
            IASTFunctionCallExpression invk = (IASTFunctionCallExpression) expression;

            IASTExpression fNameExpr = unparenthesize(invk.getFunctionNameExpression());
            prevNode = handleFunctionName(prevNode, fNameExpr);

            // TODO: C standard does not specify the evaluation order of arguments
            int[] fArgRegs = new int[invk.getArguments().length];
            for (int i = 0; i < fArgRegs.length; ++i) {
                IASTExpression fArgExpr = unparenthesize((IASTExpression) invk.getArguments()[i]);
                if (fArgExpr instanceof IASTFunctionCallExpression) {
                    Messages.warn("CParser: embedded function call in [%s]", expression.getRawSignature());
                }
                fArgRegs[i] = createRegister(fArgExpr);
                prevNode = handleRvalue(prevNode, fArgExpr, fArgRegs[i]);
            }

            IEval eval;
            if (staticInvkMap.containsKey(fNameExpr)) {
                IFunction f = staticInvkMap.get(fNameExpr);
                eval = new StaticCallEval(expression, f, fArgRegs);
            } else {
                int fReg = fetchRegister(fNameExpr);
                eval = new IndirectCallEval(expression, fReg, fArgRegs);
            }
            Messages.debug("CParser: compute invocation in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
            IBasicBlock evalNode = new EvalNode(eval, reg);
            prevNode = connect(prevNode, evalNode);

            return prevNode;
        }
        registers.add(expression);
        Messages.error("CParser: skip unsupported C Rvalue expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        return prevNode;
    }

    private IBasicBlock handleFunctionName(IBasicBlock prevNode, IASTExpression fNameExpr) {
       IFunction receiver = findReceiver(fNameExpr);
        if (receiver != null) {
            Messages.debug("CParser: resolve static invocation %s[%s] to %s", fNameExpr.getClass().getSimpleName(), fNameExpr.getRawSignature(), receiver);
            staticInvkMap.put(fNameExpr, receiver);
            return prevNode;
        } else {
            int funcPtrReg = createRegister(fNameExpr);
            return handleRvalue(prevNode, fNameExpr, funcPtrReg);
        }
    }

    private IFunction findReceiver(IASTExpression fNameExpr) {
        if (fNameExpr instanceof IASTIdExpression) {
            IBinding binding = ((IASTIdExpression) fNameExpr).getName().resolveBinding();
            if (binding instanceof IFunction) {
                return (IFunction) binding;
            }
        } else if (fNameExpr instanceof IASTUnaryExpression) {
            int op = ((IASTUnaryExpression) fNameExpr).getOperator();
            IASTExpression inner = unparenthesize(((IASTUnaryExpression) fNameExpr).getOperand());
            if (op == IASTUnaryExpression.op_amper) {
                if (inner instanceof IASTIdExpression) {
                    IBinding binding = ((IASTIdExpression) inner).getName().resolveBinding();
                    if (binding instanceof IFunction) {
                        return (IFunction) binding;
                    } else {
                        Messages.fatal("CParser: not a callable function %s[%s] in [%s]", binding.getClass().getSimpleName(), binding, fNameExpr.getRawSignature());
                    }
                } else if (inner instanceof IASTUnaryExpression
                        && ((IASTUnaryExpression) inner).getOperator() == IASTUnaryExpression.op_star) {
                    IASTExpression cancelledExpr = unparenthesize(((IASTUnaryExpression) inner).getOperand());
                    return findReceiver(cancelledExpr);
                }
            } else if (op == IASTUnaryExpression.op_star) {
                if (fNameExpr.getExpressionType() instanceof IFunctionType)  {
                    return findReceiver(inner);
                }
            } else if (op == IASTUnaryExpression.op_bracketedPrimary) {
                Messages.fatal("CParser: brackets should have been unparenthesized for [%s]", fNameExpr.getRawSignature());
            }
        }
        return null;
    }

    private IBasicBlock handleShortcircuitExpression(IBasicBlock prevNode, IASTBinaryExpression expression) {
        int op = expression.getOperator();
        assert op == IASTBinaryExpression.op_logicalAnd || op == IASTBinaryExpression.op_logicalOr;

        IASTExpression lval = unparenthesize(expression.getOperand1());
        int lReg = createRegister(lval);
        prevNode = handleRvalue(prevNode, lval, lReg);

        CondNode shortCircuit = new CondNode(lval, lReg);
        connect(prevNode, shortCircuit);

        IBasicBlock trueNode = new LabelNode(IBranchNode.THEN);
        IBasicBlock falseNode = new LabelNode(IBranchNode.ELSE);
        connect(shortCircuit, trueNode);
        connect(shortCircuit, falseNode);

        IASTExpression rval = unparenthesize(expression.getOperand2());
        int rReg = createRegister(rval);
        if (op == IASTBinaryExpression.op_logicalAnd) {
            trueNode = handleRvalue(trueNode, rval, rReg);
        } else {
            falseNode = handleRvalue(falseNode, rval, rReg);
        }

        IConnectorNode phiNode = new PhiNode("shortcircuit");
        shortCircuit.setMergeNode(phiNode);
        jump(trueNode, phiNode, false);
        jump(falseNode, phiNode, false);

        if (fixPhi(phiNode))
            return phiNode;
        else
            return null;
    }

    private IBasicBlock handleIfStatement(IBasicBlock prevNode, IASTIfStatement cIf) {
        IASTExpression condExpr = unparenthesize(cIf.getConditionExpression());
        int condReg = createRegister(condExpr);
        prevNode = handleRvalue(prevNode, condExpr, condReg);

        CondNode ifNode = new CondNode(condExpr, condReg);
        connect(prevNode, ifNode);

        IBasicBlock thenNode = new LabelNode(IBranchNode.THEN);
        connect(ifNode, thenNode);
        thenNode = expandGraph(thenNode, cIf.getThenClause());

        IBasicBlock elseNode = new LabelNode(IBranchNode.ELSE);
        connect(ifNode, elseNode);
        elseNode = expandGraph(elseNode, cIf.getElseClause());

        IConnectorNode phiNode = new PhiNode("endif");
        ifNode.setMergeNode(phiNode);
        jump(thenNode, phiNode, false);
        jump(elseNode, phiNode, false);

        if (fixPhi(phiNode))
            return phiNode;
        else
            return null;
    }

    private IBasicBlock handleWhileStatement(IBasicBlock prevNode, IASTWhileStatement cWhile) {
        IConnectorNode continueNode = new PhiNode("continue");
        IConnectorNode breakNode = new PhiNode("break");

        connect(prevNode, continueNode);

        IASTExpression condExpr = cWhile.getCondition();
        IASTStatement loopBody = cWhile.getBody();

//        if (condExpr == null) {
//            IBasicBlock bodyEnd = handleLoopBody(continueNode, loopBody, continueNode, breakNode);
//            jump(bodyEnd, continueNode, true);

        condExpr = unparenthesize(condExpr);
        int condReg = createRegister(condExpr);
        IBasicBlock evalCondNode = handleRvalue(continueNode, condExpr, condReg);

        CondNode whileNode = new CondNode(condExpr, condReg);
        connect(evalCondNode, whileNode);
        whileNode.setMergeNode(breakNode);

        IBasicBlock bodyStart = new LabelNode(IBranchNode.THEN);
        connect(whileNode, bodyStart);
        IBasicBlock bodyEnd = handleLoopBody(bodyStart, loopBody, continueNode, true, breakNode);
        jump(bodyEnd, continueNode, true);

        IBasicBlock loopEnd = new LabelNode(IBranchNode.ELSE);
        connect(whileNode, loopEnd);
        jump(loopEnd, breakNode, false);

        if (fixPhi(breakNode))
            return breakNode;
        else
            return null;
    }

    private IBasicBlock handleDoWhileStatement(IBasicBlock prevNode, IASTDoStatement cDoWhile) {
        IConnectorNode continueNode = new PhiNode("continue");
        IConnectorNode breakNode = new PhiNode("break");

        IASTExpression condExpr = cDoWhile.getCondition();
        IASTStatement loopBody = cDoWhile.getBody();

        IConnectorNode bodyStart = new PhiNode("do");
        connect(prevNode, bodyStart);

        IBasicBlock bodyEnd = handleLoopBody(bodyStart, loopBody, continueNode, false, breakNode);
        jump(bodyEnd, continueNode, false);

        int condReg = createRegister(condExpr);
        IBasicBlock evalCondNode = handleRvalue(continueNode, condExpr, condReg);
        CondNode doWhileNode = new CondNode(condExpr, condReg);
        connect(evalCondNode, doWhileNode);
        doWhileNode.setMergeNode(breakNode);

        IBranchNode trueNode = new LabelNode(IBranchNode.THEN);
        connect(doWhileNode, trueNode);
        jump(trueNode, bodyStart, true);

        IBranchNode loopEnd = new LabelNode(IBranchNode.ELSE);
        connect(doWhileNode, loopEnd);
        jump(loopEnd, breakNode, false);

        fixPhi(continueNode);

        if (fixPhi(breakNode))
            return breakNode;
        else
            return null;
    }


    private IBasicBlock handleForStatement(IBasicBlock prevNode, IASTForStatement cFor) {
        IConnectorNode continueNode = new PhiNode("continue");
        IConnectorNode breakNode = new PhiNode("break");

        IASTStatement init = cFor.getInitializerStatement();
        prevNode = expandGraph(prevNode, init);
        IConnectorNode condStart = new PhiNode();
        connect(prevNode, condStart);

        IASTExpression condExpr = cFor.getConditionExpression();
        int condReg = createRegister(condExpr);
        IBasicBlock evalCondNode = handleRvalue(condStart, condExpr, condReg);
        CondNode forNode = new CondNode(condExpr, condReg);
        connect(evalCondNode, forNode);
        forNode.setMergeNode(breakNode);

        IASTStatement loopBody = cFor.getBody();
        IBranchNode bodyStart = new LabelNode(IBranchNode.THEN);
        connect(forNode, bodyStart);
        IBasicBlock bodyEnd = handleLoopBody(bodyStart, loopBody, continueNode, true, breakNode);
        jump(bodyEnd, continueNode, true);

        IASTExpression iter = cFor.getIterationExpression();
        IBasicBlock afterIter = handleExpression(continueNode, iter);
        connect(afterIter, condStart);

        IBranchNode loopEnd = new LabelNode(IBranchNode.ELSE);
        connect(forNode, loopEnd);
        jump(loopEnd, breakNode, false);

        fixPhi(continueNode);
        if (fixPhi(breakNode))
            return breakNode;
        else
            return null;
    }

    private IBasicBlock handleLoopBody(IBasicBlock bodyStart, IASTStatement body, IConnectorNode continueNode, boolean continueBackward, IConnectorNode breakNode) {
        IConnectorNode savedContinue = outerContinueTarget;
        boolean savedContinuePos = outerContinueBackward;
        IConnectorNode savedBreak = outerBreakTarget;
        outerContinueTarget = continueNode;
        outerContinueBackward = continueBackward;
        outerBreakTarget = breakNode;
        IBasicBlock bodyEnd = expandGraph(bodyStart, body);
        outerContinueTarget = savedContinue;
        outerContinueBackward = savedContinuePos;
        outerBreakTarget = savedBreak;
        return bodyEnd;
    }

    private IBasicBlock connect(IBasicBlock prevNode, IBasicBlock postNode) {
        if (prevNode instanceof IExitNode || prevNode instanceof IJumpNode || prevNode == null) {
            unreachable.add(postNode);
            return null;
        }
        ((AbstractBasicBlock) prevNode).addOutgoing(postNode);
        ((AbstractBasicBlock) postNode).addIncoming(prevNode);
        return postNode;
    }

    private void jump(IBasicBlock prevNode, IConnectorNode phiNode, boolean isBackward) {
        if (prevNode instanceof IExitNode || prevNode instanceof IJumpNode || prevNode == null) {
            return;
        }
        GotoNode gotoNode = new GotoNode();
        connect(prevNode, gotoNode);
        gotoNode.setJump(phiNode, isBackward);
        ((PhiNode) phiNode).addIncoming(gotoNode);
    }

    private boolean fixPhi(IConnectorNode phiNode) {
        if (phiNode.getIncomingSize() == 0) {
            unreachable.add(phiNode);
            return false;
        }
        return true;
    }

    // strip the wrapping brackets
    private static IASTExpression unparenthesize(IASTExpression expression) {
        IASTExpression ret = expression;
        while (ret instanceof IASTUnaryExpression &&
                ((IASTUnaryExpression) ret).getOperator() == IASTUnaryExpression.op_bracketedPrimary)
            ret = ((IASTUnaryExpression) ret).getOperand();
        return ret;
    }

    // get the register which stores the evaluated value of expression
    private int getRegister(IASTExpression expression, boolean allocate) {
        if (expression instanceof IASTUnaryExpression) {
            int op = ((IASTUnaryExpression) expression).getOperator();
            if (op == IASTUnaryExpression.op_bracketedPrimary) {
                if (allocate)
                    Messages.warn("CParser: brackets should have been unparenthesized for [%s]", expression.getRawSignature());
                return getRegister(unparenthesize(expression), allocate);
            }
            if (op == IASTUnaryExpression.op_postFixIncr || op == IASTUnaryExpression.op_postFixDecr) {
                // special case for postfix incr/decr, if fetch result, use previous value
                if (!allocate)
                    return getRegister(((IASTUnaryExpression) expression).getOperand(), allocate);
            }
        }
        if (expression instanceof IASTBinaryExpression) {
            int op = ((IASTBinaryExpression) expression).getOperator();
            if (op == IASTBinaryExpression.op_assign) {
                return getRegister(((IASTBinaryExpression) expression).getOperand2(), allocate);
            }
        }
        if (expression instanceof IASTExpressionList) {
            IASTExpression[] exprs = ((IASTExpressionList) expression).getExpressions();
            int len = exprs.length;
            if (len == 0)
                Messages.fatal("CParser: empty expression list [%s]", expression.getRawSignature());
            return getRegister(unparenthesize(exprs[len-1]), allocate);
        }
        int id = registers.indexOf(expression);
        if (id < 0) {
            if (allocate) {
                registers.add(expression);
                id = registers.indexOf(expression);
            } else {
                Messages.fatal("CParser: uncomputed expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
            }
        }
        return id;
    }

    // allocate a register to store the evaluated value of expression
    public int fetchRegister(IASTExpression expression) {
        return getRegister(expression, false);
    }

    private int createRegister(IASTExpression expression) {
        if (expression.getExpressionType().isSameType(CBasicType.VOID)) {
            Messages.warn("CParser: create null register -1 for void-type expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
            return -1;
        }
        return getRegister(expression, true);
    }
}
