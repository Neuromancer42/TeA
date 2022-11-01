package com.neuromancer42.tea.program.cdt.parser.cfg;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.core.util.tuple.object.Pair;
import com.neuromancer42.tea.program.cdt.parser.evaluation.*;
import org.eclipse.cdt.codan.core.model.cfg.*;
import org.eclipse.cdt.codan.internal.core.cfg.*;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.ICASTKnRFunctionDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.ITypeContainer;
import org.eclipse.cdt.internal.core.dom.parser.c.CBasicType;

import java.io.PrintWriter;
import java.util.*;

// general purpose
// translate the complex source code into a control flow graph
// each edge in the graph represents a simple computation of register values
// 1. arithmetical computations
// 2. read variables from a pointer
// 3. write variables to a pointer
// 4. computation with controls: logical and/or, ternary expression
// 5. function calls
// 6. returns
// 7. control statements: if, while, do-while, for, switch
// 8. declarators

// generally, this control flow is just a determinated variety of C specification
// in that: some evaluation orders are unspecified
// e.g. operands of arithmetical computations, arguments of function calls
public class CFGBuilder {
    private final IASTTranslationUnit transUnit;
    private final Map<IFunction, IntraCFG> intraCFGMap;

    private final IndexMap<Object> registers;
    // a register is either:
    // 1. <IASTExpression> the result value of a non-lvalue expression
    // 2. <IBinding> a pointer points to a declared variable/function
    // 3. <Pair<IFunction, Integer>> the value of a function parameter
    // 4. <String> a pointer points to the address of a string literal
    // 5. <GetElementPtrEval> inserted gep result for field/offset access
    // 6. <Pair<Integer, IArrayType>> inserted load instruction to get base pointer of an array reference

    private final Set<IFunction> funcs;
    private final Map<IFunction, Set<IVariable>> funcVars;
    private final Set<IVariable> staticVars;

    private final Set<IType> types;
    private final Set<IField> fields;

    private final Map<IFunction, int[]> funcArgsMap;
    private final Map<IBinding, Integer> refRegMap;
    private final Map<IASTExpression, IFunction> staticInvkMap;
    private final Map<String, Integer> stringConstants;
    private final Map<Integer, String> simpleConstants;

    // TODO: use struct signatures rather than statement references to collect fields

    private IStartNode start;
    private List<IExitNode> exits;
    private List<IBasicBlock> unreachable;
    private IConnectorNode outerContinueTarget;
    private boolean outerContinueBackward;
    private IConnectorNode outerBreakTarget;
    private Map<ILabel, IConnectorNode> labelMap;
    private Map<ILabel, Set<IBasicBlock>> unresolvedGotos;
    private IFunction curFunc;
    private IBasicBlock prevNode;

    public CFGBuilder(IASTTranslationUnit tu) {
        this.transUnit = tu;
        intraCFGMap = new HashMap<>();
        registers = new IndexMap<>();

        funcs = new LinkedHashSet<>();
        funcVars = new HashMap<>();
        staticVars = new LinkedHashSet<>();
        types = new LinkedHashSet<>();
        fields = new LinkedHashSet<>();

        funcArgsMap = new HashMap<>();
        refRegMap = new HashMap<>();
        staticInvkMap = new LinkedHashMap<>();
        stringConstants = new LinkedHashMap<>();
        simpleConstants = new LinkedHashMap<>();

        curFunc = null;
        for (IASTDeclaration decl : tu.getDeclarations()) {
            // add declared variables and functions into registers
            if (decl instanceof IASTFunctionDefinition) {
                IASTDeclarator dtor = ((IASTFunctionDefinition) decl).getDeclarator();
                Messages.debug("CParser: declare func [%s] at %s:#%d", dtor.getName(), dtor.getFileLocation().getFileName(), dtor.getFileLocation().getStartingLineNumber());
                processDeclarator(dtor, true);
            } else if (decl instanceof IASTSimpleDeclaration) {
                IASTDeclarator[] dtors = ((IASTSimpleDeclaration) decl).getDeclarators();
                for (var dtor : dtors) {
                    processDeclarator(dtor, true);
                }
            }
        }
    }

    public void build() {
        for (var decl: transUnit.getDeclarations()) {
            if (decl instanceof IASTFunctionDefinition) {
                var fDef = (IASTFunctionDefinition) decl;
                var f = (IFunction) fDef.getDeclarator().getName().resolveBinding();
                var cfg = buildIntraCFG(fDef);
                intraCFGMap.put(f, cfg);
            }
        }
    }

    public IntraCFG getIntraCFG(IFunction func) {
        return intraCFGMap.get(func);
    }

    // given a maybe-nested declarator, returning its declared binding
    // create locations if it allocates an object
    private IBinding processDeclarator(IASTDeclarator declarator, boolean isStatic) {
        while (declarator.getNestedDeclarator() != null)
            declarator = declarator.getNestedDeclarator();
        IBinding binding = declarator.getName().resolveBinding();
        if (binding instanceof IVariable) {
            IVariable var = (IVariable) binding;
            if (refRegMap.containsKey(var))
                Messages.warn("CParser: re-declare variable %s[%s] at line#%d: (%s)", var.getClass().getSimpleName(), var, declarator.getFileLocation().getStartingLineNumber(), declarator.getRawSignature());
            processVariable(var, isStatic);
        } else if (binding instanceof IFunction) {
            IFunction func = (IFunction) binding;
            if (funcs.contains(func))
                Messages.warn("CParser: re-declare function %s[%s] at line#%d: (%s)", func.getClass().getSimpleName(), func, declarator.getFileLocation().getStartingLineNumber(), declarator.getRawSignature());
            processFunction(func);
        } else if (binding instanceof ITypedef) {
            ITypedef typedef = (ITypedef) binding;
            Messages.warn("CParser: skip typedef %s[%s]", typedef.getClass().getSimpleName(), typedef);
        } else {
            Messages.error("CParser: unhandled declaration %s[%s] (%s[%s])", binding.getClass().getSimpleName(), binding, declarator.getClass().getSimpleName(), declarator.getRawSignature());
        }
        return binding;
    }

    private int processVariable(IVariable var, boolean isStatic) {
        registers.add(var);
        int vReg = registers.indexOf(var);
        refRegMap.put(var, vReg);
        if (isStatic) {
            staticVars.add(var);
        } else {
            funcVars.get(curFunc).add(var);
        }
        processType(var.getType());
        Messages.debug("CParser: create ref-pointer *(%s)@%d for %s#%d in (%s)", var.getType(), vReg, var.getName(), var.hashCode(), var.getOwner());
        return vReg;
    }

    private int processFunction(IFunction func) {
        registers.add(func);
        int fReg = registers.indexOf(func);
        funcs.add(func);
        types.add(func.getType());
        refRegMap.put(func, fReg);
        funcVars.putIfAbsent(func, new LinkedHashSet<>());
        Messages.debug("CParser: alloc stack location (funPtr)@%d for function %s[%s]", fReg, func.getClass().getSimpleName(), func);
        if (func.getParameters() == null) {
            Messages.debug("CParser: function %s[%s] has no argument", func.getClass().getSimpleName(), func);
        } else {
            // TODO: var-arg?
            IParameter[] params = func.getParameters();
            int[] argRegs = new int[params.length];
            for (int i = 0; i < params.length; ++i) {
                Pair<IFunction, Integer> arg = new Pair<>(func, i);
                registers.add(arg);
                int aReg = registers.indexOf(arg);
                argRegs[i] = aReg;
                Messages.debug("CParser: reference %s[%s]'s %d-th argument %s[%s] by %s@%d", func.getClass().getSimpleName(), func, i, arg.getClass().getSimpleName(), arg, params[i].getType(), aReg);
            }
            funcArgsMap.put(func, argRegs);
        }
        return fReg;
    }

    private void processType(IType type) {
        boolean newFound = types.add(type);
        if (!newFound)
            return;
        if (type instanceof IArrayType) {
            processType(((IArrayType) type).getType());
        } else if (type instanceof IPointerType) {
            processType(((IPointerType) type).getType());
        } else if (type instanceof ITypedef) {
            processType(((ITypedef) type).getType());
        } else if (type instanceof ICompositeType) {
            for (IField f : ((ICompositeType) type).getFields()) {
                fields.add(f);
                processType(f.getType());
            }
        } else if (type instanceof ICQualifierType) {
            processType(((ICQualifierType) type).getType());
        }  else if (type instanceof IFunctionType) {
            types.add(type);
        } else if (!(type instanceof IBasicType)) {
            Messages.error("CParser: unhandled type %s[%s]", type.getClass().getSimpleName(), type);
        }
    }

    public IndexMap<Object> getRegisters() {
        return registers;
    }

    public Map<Integer, String> getSimpleConstants() {
        return simpleConstants;
    }

    public int[] getFuncArgs(IFunction meth) {
        int[] args = funcArgsMap.get(meth);
        if (args == null) {
            Messages.error("CParser: function %s[%s] arguments not processed", meth.getClass().getSimpleName(), meth);
            return new int[0];
        } else {
            return args;
        }
    }

    public int getRefReg(IBinding id) {
        Integer reg = refRegMap.get(id);
        if (reg == null) {
            Messages.debug("CParser: referencing undeclared identifier %s[%s]", id.getClass().getSimpleName(), id);
            return -1;
        }
        return reg;
    }

    public Set<IFunction> getFuncs() {
        return funcs;
    }

    public Set<IVariable> getGlobalVars() {
        return staticVars;
    }

    public Set<IVariable> getMethodVars(IFunction func) {
        return funcVars.get(func);
    }

    public Set<IType> getTypes() {
        return types;
    }

    public Set<IField> getFields() {
        return fields;
    }

    public IntraCFG buildIntraCFG(IASTFunctionDefinition fDef) {
        IASTFunctionDeclarator fDecl = fDef.getDeclarator();
        curFunc = (IFunction) fDecl.getName().resolveBinding();
        start = new FuncEntryNode(curFunc);
        exits = new ArrayList<>();
        outerContinueTarget = null;
        outerContinueBackward = true;
        outerBreakTarget = null;
        labelMap = new HashMap<>();
        unresolvedGotos = new HashMap<>();
        unreachable = new ArrayList<>();
        prevNode = start;
        IASTDeclarator[] paramDtors;
        if (fDecl instanceof IASTStandardFunctionDeclarator) {
            IASTParameterDeclaration[] paramDecls = ((IASTStandardFunctionDeclarator) fDecl).getParameters();
            paramDtors = new IASTDeclarator[paramDecls.length];
            for (int i = 0; i < paramDtors.length; i++)
                paramDtors[i] = paramDecls[i].getDeclarator();
        } else if (fDecl instanceof ICASTKnRFunctionDeclarator) {
            IASTName[] paramNames = ((ICASTKnRFunctionDeclarator) fDecl).getParameterNames();
            paramDtors = new IASTDeclarator[paramNames.length];
            for (int i = 0; i < paramDtors.length; i++)
                paramDtors[i] = ((ICASTKnRFunctionDeclarator) fDecl).getDeclaratorForParameterName(paramNames[i]);
        } else {
            Messages.error("CParser: skip unhandled function declarator %s[%s]", fDecl.getClass().getSimpleName(), fDecl.getName());
            return null;
        }
        int[] argRegs = funcArgsMap.get(curFunc);
        for (int i = 0; i < paramDtors.length; ++i) {
            IASTDeclarator dtor = paramDtors[i];
            IParameter param = (IParameter) processDeclarator(dtor, false);
            if (param.getType().isSameType(CBasicType.VOID)) {
                break;
            }
            int refReg = getRefReg(param);
            if (refReg < 0) {
                Messages.fatal("CParser: cannot find reference register for %s[%s]#%d in (%s)", param.getClass().getSimpleName(), param, param.hashCode(), param.getOwner());
            }
            Messages.debug("CParser: alloc stack memory for parameter ref @%d = {%s}", refReg, param);
            IBasicBlock allocNode = new AllocNode(refReg, param);
            prevNode = connect(prevNode, allocNode);

            int aReg = argRegs[i];
            Messages.debug("CParser: store parameter in location @%d <- %s@%d (%s)", refReg, param.getType(), aReg, param);
            IBasicBlock storeNode = new StoreNode(refReg, aReg);
            prevNode = connect(prevNode, storeNode);
        }

        IASTStatement fBody = fDef.getBody();
        expandGraph(fBody);

        if (prevNode != null && !(prevNode instanceof IExitNode) && !(prevNode instanceof IJumpNode) && !unreachable.contains(prevNode)) {
            ReturnNode dummyRet = new ReturnNode();
            Messages.debug("CParser: add implicit return node [%s] for function [%s]", dummyRet.toDebugString(), curFunc);
            dummyRet.setStartNode(start);
            connect(prevNode, dummyRet);
            exits.add(dummyRet);
        }
        for (ILabel label : labelMap.keySet()) {
            IConnectorNode labelNode = labelMap.get(label);
            if (!fixPhi(labelNode)) {
                Messages.debug("CParser: unused label %s[%s]", label.getClass().getSimpleName(), label);
            }
        }
        IntraCFG cfg = new IntraCFG(curFunc, start, exits);
        //cfg.setUnconnectedNodes(unreachable);
        return cfg;
    }

    private void expandGraph(IASTStatement statement) {
        if (statement == null || statement instanceof IASTNullStatement) {
            return;
        } else if (statement instanceof IASTCompoundStatement) {
            IASTStatement[] subStatements = ((IASTCompoundStatement) statement).getStatements();
            for (IASTStatement subStatement : subStatements) {
                expandGraph(subStatement);
            }
            return;
        } else if (statement instanceof IASTExpressionStatement) {
            IASTExpression expr = unparenthesize(((IASTExpressionStatement) statement).getExpression());
            handleExpression(expr);
            return;
        } else if (statement instanceof IASTDeclarationStatement) {
            IASTDeclaration decl = ((IASTDeclarationStatement) statement).getDeclaration();
            if (decl instanceof IASTSimpleDeclaration) {
                boolean isStatic = ((IASTSimpleDeclaration) decl).getDeclSpecifier().getStorageClass() == IASTDeclSpecifier.sc_static;
                IASTDeclarator[] dtors = ((IASTSimpleDeclaration) decl).getDeclarators();
                for (IASTDeclarator dtor: dtors) {
                    IBinding binding = processDeclarator(dtor, isStatic);
                    if (binding instanceof IVariable && !isStatic) {
                        IVariable var = (IVariable) binding;
                        int refReg = getRefReg(binding);
                        if (refReg < 0) {
                            Messages.fatal("CParser: cannot find reference register for variable %s[%s]#%d in (%s)", var.getClass().getSimpleName(), var, var.hashCode(), var.getOwner());
                        }
                        Messages.debug("CParser: alloc stack memory for variable @%d = {%s}", refReg, var);
                        IBasicBlock allocNode = new AllocNode(refReg, var);
                        prevNode = connect(prevNode, allocNode);
                    }

                    IASTInitializer initializer = dtor.getInitializer();
                    if (initializer != null) {
                        if (!(binding instanceof IVariable)) {
                            Messages.fatal("CParser: initializing non-variable object %s[%s] at line#%d", binding.getClass().getSimpleName(), binding, dtor.getFileLocation().getStartingLineNumber());
                            assert false;
                        }
                        IVariable var = (IVariable) binding;
                        int refReg = getRefReg(binding);
                        if (refReg < 0) {
                            Messages.fatal("CParser: cannot find reference register for variable %s[%s]#%d in (%s)", var.getClass().getSimpleName(), var, var.hashCode(), var.getOwner());
                        }
                        if (initializer instanceof IASTEqualsInitializer) {
                            IASTInitializerClause initCls = ((IASTEqualsInitializer) initializer).getInitializerClause();
                            handleInitializerClause(refReg, var.getType(), initCls);
                        } else {
                            Messages.error("CParser: skip unhandled declaration initializer %s[%s]", initializer.getClass().getSimpleName(), initializer.getRawSignature());
                        }
                    }
                }
                return;
            } else if (decl instanceof IASTFunctionDefinition) {
                Messages.fatal("CParser: do not support nested function definition");
            }
            Messages.error("CParser: skip unhandled declaration %s[%s]", decl.getClass().getSimpleName(), decl.getRawSignature());
        } else if (statement instanceof IASTIfStatement) {
            IASTIfStatement cIf = (IASTIfStatement) statement;
            handleIfStatement(cIf);

            return;
        } else if (statement instanceof IASTWhileStatement) {
            IASTWhileStatement cWhile = (IASTWhileStatement) statement;
            handleWhileStatement(cWhile);

            return;
        } else if (statement instanceof IASTDoStatement) {
            IASTDoStatement cDo = (IASTDoStatement) statement;
            handleDoWhileStatement(cDo);

            return;
        } else if (statement instanceof IASTForStatement) {
            IASTForStatement cFor = (IASTForStatement) statement;
            handleForStatement(cFor);

            return;
        } else if (statement instanceof IASTBreakStatement) {
            if (outerBreakTarget != null) {
                jump(prevNode, outerBreakTarget, false);
                prevNode = null;
            } else {
                Messages.warn("CParser: skip invalid %s in no loop or switch", statement.getClass().getSimpleName());
            }
            return;
        } else if (statement instanceof IASTContinueStatement) {
            if (outerContinueTarget != null) {
                jump(prevNode, outerContinueTarget, outerContinueBackward);
                prevNode = null;
            } else {
                Messages.warn("CParser: skip invalid %s in no loop", statement.getClass().getSimpleName());
            }
            return;
        } else if (statement instanceof IASTLabelStatement) {
            ILabel label = (ILabel) ((IASTLabelStatement) statement).getName().resolveBinding();
            IConnectorNode labelNode = new PhiNode(label.getName() + "@" + statement.getFileLocation().getStartingLineNumber());
            labelMap.put(label, labelNode);

            connect(prevNode, labelNode);
            // labels can come across returns, do not mark them as unreachable
            if (unreachable.remove(labelNode))
                Messages.debug("CParser: label [%s] after goto/continue/break/return", label);

            Set<IBasicBlock> gotoNodes = unresolvedGotos.remove(label);
            if (gotoNodes != null) {
                for (IBasicBlock gotoNode : gotoNodes) {
                    jump(gotoNode, labelNode, false);
                }
            }

            prevNode = labelNode;
            expandGraph(((IASTLabelStatement) statement).getNestedStatement());
            return;
        } else if (statement instanceof IASTGotoStatement) {
            ILabel label = (ILabel) ((IASTGotoStatement) statement).getName().resolveBinding();
            IConnectorNode labelNode = labelMap.get(label);
            if (labelNode != null) {
                jump(prevNode, labelNode, true);
            } else {
                unresolvedGotos.computeIfAbsent(label, l -> new HashSet<>()).add(prevNode);
            }
            prevNode = null;
            return;
        }else if (statement instanceof IASTReturnStatement) {
            IASTReturnStatement ret = (IASTReturnStatement) statement;
            IASTExpression retExpr = ret.getReturnValue();
            ReturnNode retNode ;
            if (retExpr != null) {
                retExpr = unparenthesize(retExpr);
                int retReg = handleRvalue(retExpr);
                retNode = new ReturnNode(retReg);
            } else {
                retNode = new ReturnNode();
            }
            retNode.setStartNode(start);
            exits.add(retNode);
            prevNode = connect(prevNode, retNode);

            return;
        }

        Messages.error("CParser: skip unsupported C statement %s[%s]", statement.getClass().getSimpleName(), statement.getRawSignature());
    }

    private void handleInitializerClause(int refReg, IType refType, IASTInitializerClause initClause) {
        while (refType instanceof ITypedef || refType instanceof IQualifierType) {
            if (refType instanceof ITypedef)
                refType = ((ITypedef) refType).getType();
            else
                refType = ((IQualifierType) refType).getType();
        }
        if (initClause instanceof IASTExpression) {
            // scalar expression
            IASTExpression initExpr = unparenthesize((IASTExpression) initClause);
            int initReg = handleRvalue(initExpr);

            Messages.debug("CParser: initialize location %s <- %s@%d (%s)", refReg, initExpr.getExpressionType(), initReg, initClause.getRawSignature());
            IBasicBlock storeNode = new StoreNode(refReg, initReg);
            prevNode = connect(prevNode, storeNode);
            return;
        } else if (initClause instanceof IASTInitializerList) {
            IASTInitializerClause[] clsList = ((IASTInitializerList) initClause).getClauses();
            if (refType instanceof IBasicType) {
                if (clsList.length >= 1) {
                    if (clsList.length > 1)
                        Messages.warn("CParser: drop excess elements in scalar initializer");
                    handleInitializerClause(refReg, refType, clsList[0]);
                    return;
                }
            }
            int pos = -1;
            for (IASTInitializerClause subCls : clsList) {
                if (subCls instanceof ICASTDesignatedInitializer) {
                    ICASTDesignatedInitializer designatedInit = (ICASTDesignatedInitializer) subCls;
                    ICASTDesignator[] designators = designatedInit.getDesignators();

                    int elemPtr = refReg;
                    IType elemType = refType;

                    for (int i = 0; i < designators.length; ++i) {
                        ICASTDesignator designator = designators[i];
                        IEval gepEval = null;
                        IType subElemType = null;
                        if (designator instanceof ICASTFieldDesignator) {
                            String fieldName = ((ICASTFieldDesignator) designator).getName().toString();
                            ICompositeType cType = (ICompositeType) elemType;
                            IField field = cType.findField(fieldName);
                            IField[] fields = cType.getFields();
                            gepEval = new GetFieldPtrEval(elemType, elemPtr, field);
                            subElemType = field.getType();
                            if (i == 0) {
                                for (int fId = 0; fId < fields.length; ++fId) {
                                    if (fields[fId].equals(field)) {
                                        pos = fId;
                                        break;
                                    }
                                }
                            }
                        } else if (designator instanceof ICASTArrayDesignator) {
                            IASTExpression offsetExpr = ((ICASTArrayDesignator) designator).getSubscriptExpression();
                            if (offsetExpr instanceof IASTLiteralExpression) {
                                String offsetStr = String.valueOf(((IASTLiteralExpression) offsetExpr).getValue());
                                int offset = Integer.parseInt(offsetStr);
                                Pair<Integer, IArrayType> basePtrObj = new Pair<>(elemPtr, (IArrayType) elemType);
                                if (!registers.contains(basePtrObj)) {
                                    registers.add(basePtrObj);
                                    int basePtr = registers.indexOf(basePtrObj);
                                    Messages.debug("CParser: load base address of ptr-to-array @%d <- *@%d", basePtr, elemPtr);
                                    IBasicBlock loadNode = new LoadNode(basePtr, elemPtr);
                                    prevNode = connect(prevNode, loadNode);
                                }
                                int basePtr = registers.indexOf(basePtrObj);
                                gepEval = new GetOffsetPtrEval(elemType, basePtr, offset);
                                subElemType = ((IArrayType) elemType).getType();
                                if (i == 0)
                                    pos = offset;
                            }
                        }
                        if (gepEval != null) {
                            registers.add(gepEval);
                            elemPtr = registers.indexOf(gepEval);
                            Messages.debug("CParser: get element ptr for initialize @%d = {%s}", elemPtr, gepEval.toDebugString());
                            IBasicBlock gepNode = new EvalNode(gepEval, elemPtr);
                            prevNode = connect(prevNode, gepNode);
                        }
                        while (subElemType instanceof ITypedef) {
                            subElemType = ((ITypedef) subElemType).getType();
                        }
                        elemType = subElemType;
                    }
                    IASTInitializerClause cls = designatedInit.getOperand();
                    handleInitializerClause(elemPtr, elemType, cls);
                } else {
                    ++pos;
                    GetElementPtrEval gepEval;
                    IType subType;
                    if (refType instanceof IArrayType) {
                        Pair<Integer, IArrayType> basePtrObj = new Pair<>(refReg, (IArrayType) refType);
                        if (!registers.contains(basePtrObj)) {
                            registers.add(basePtrObj);
                            int basePtr = registers.indexOf(basePtrObj);
                            Messages.debug("CParser: load base address of ptr-to-array @%d <- *@%d", basePtr, refReg);
                            IBasicBlock loadNode = new LoadNode(basePtr, basePtr);
                            prevNode = connect(prevNode, loadNode);
                        }
                        int basePtr = registers.indexOf(basePtrObj);
                        gepEval = new GetOffsetPtrEval(refType, basePtr, pos);
                        subType = ((ITypeContainer) refType).getType();
                    } else {
                        ICompositeType cType = (ICompositeType) refType;
                        IField field = cType.getFields()[pos];
                        gepEval = new GetFieldPtrEval(refType, refReg, field);
                        subType = field.getType();
                    }
                    registers.add(gepEval);
                    int subReg = registers.indexOf(gepEval);
                    Messages.debug("CParser: get element ptr for initialize @%d = {%s}", subReg, gepEval.toDebugString());

                    handleInitializerClause(subReg, subType, subCls);
                }
            }
            return;
        }
        Messages.error("CParser: skip unhandled initializer clause %s[%s]", initClause.getClass().getSimpleName(), initClause.getRawSignature());
    }


    private int handleExpression(IASTExpression expression) {
        if (expression instanceof IASTBinaryExpression) {
            IASTBinaryExpression binExpr = (IASTBinaryExpression) expression;
            int op = binExpr.getOperator();
            if (op == IASTBinaryExpression.op_assign) {
                IASTExpression lhs = unparenthesize(binExpr.getOperand1());
                int lRefReg = handleLvalue(lhs);

                IASTExpression rhs = unparenthesize(binExpr.getOperand2());
                int rValReg = handleRvalue(rhs);

                Messages.debug("CParser: assign to location %s <- %s@%d (%s)", lRefReg, rhs.getExpressionType(), rValReg, expression.getRawSignature());
                IBasicBlock storeNode = new StoreNode(lRefReg, rValReg);
                prevNode = connect(prevNode, storeNode);

                return rValReg;
            } else if (binExpr.isLValue()) { // equivalent to op is all compound assignment operators (except op_assign)
                IASTExpression lval = unparenthesize(binExpr.getOperand1());
                int lRefReg = handleLvalue(lval);

                int prevReg = createRegister(lval);
                Messages.debug("CParser: read from location %s@%d <- *@%d (%s)", lval.getExpressionType(), prevReg, lRefReg, expression.getRawSignature());
                IBasicBlock loadNode = new LoadNode(prevReg, lRefReg);
                prevNode = connect(prevNode, loadNode);

                IASTExpression rval = unparenthesize(binExpr.getOperand2());
                int rValReg = handleRvalue(rval);

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
                IEval eval = new BinaryEval(expression, opStr, prevReg, rValReg);
                Messages.debug("CParser: compute updated value in %s@%d := %s", eval.getType(), postReg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, postReg);
                prevNode = connect(prevNode, evalNode);

                Messages.debug("CParser: update location %s <- %s@%d (%s)", lRefReg, expression.getExpressionType(), postReg, expression.getRawSignature());
                IBasicBlock storeNode = new StoreNode(lRefReg, postReg);
                prevNode = connect(prevNode, storeNode);

                return postReg;
            } else if (op == IASTBinaryExpression.op_logicalAnd || op == IASTBinaryExpression.op_logicalOr){
                return handleShortcircuitExpression(binExpr);
            } else {
                Messages.warn("CParser: expression result un-used for [%s]", expression.getRawSignature());
                return handleRvalue(expression);
            }
        } else if (expression instanceof IASTUnaryExpression) {
            IASTUnaryExpression unaryExpr = (IASTUnaryExpression) expression;
            int op = unaryExpr.getOperator();
            if (op == IASTUnaryExpression.op_prefixIncr || op == IASTUnaryExpression.op_prefixDecr
                    || op == IASTUnaryExpression.op_postFixIncr || op == IASTUnaryExpression.op_postFixDecr
            ) {
                IASTExpression obj = unparenthesize(unaryExpr.getOperand());
                int refReg = handleLvalue(obj);

                int prevReg = createRegister(obj);
                Messages.debug("CParser: read from location %s@%d <- *@%d (%s)", obj.getExpressionType(), prevReg, refReg, expression.getRawSignature());
                IBasicBlock loadNode = new LoadNode(prevReg, refReg);
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

                Messages.debug("CParser: incr/decr location *@%d <- %s@%d (%s)", refReg, expression.getExpressionType(), postReg, expression.getRawSignature());
                IBasicBlock storeNode = new StoreNode(refReg, postReg);
                prevNode = connect(prevNode,storeNode);

                return postReg;
            } else {
                Messages.warn("CParser: expression result un-used for [%s]", expression.getRawSignature());
                return handleRvalue(expression);
            }
        } else if (expression instanceof IASTFunctionCallExpression) {
            //int reg = createRegister(expression);
            if (!expression.getExpressionType().isSameType(CBasicType.VOID))
                Messages.warn("CParser: discard ret-val of expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
            return handleRvalue(expression);
        } else {
            Messages.warn("CParser: expression result unused for %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
            return handleRvalue(expression);
        }
    }

    // Note: this method does not check if the lvalue is modifiable
    private int handleLvalue(IASTExpression expression) {
        if (expression instanceof IASTIdExpression) {
            // No need to compute target
            IBinding binding = ((IASTIdExpression) expression).getName().resolveBinding();
            int refReg = getRefReg(binding);
            if (refReg < 0) {
                if (binding instanceof IFunction) {
                    IFunction f = (IFunction) binding;
                    Messages.debug("CParser: get address of external function %s[%s] at line#%d (%s)", binding.getClass().getSimpleName(), binding, expression.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                    refReg = processFunction(f);
                } else if (binding instanceof IVariable) {
                    IVariable v = (IVariable) binding;
                    Messages.debug("CParser: get address of external variable %s[%s] at line#%d (%s)", binding.getClass().getSimpleName(), binding, expression.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                    refReg = processVariable(v, true);
                } else {
                    Messages.fatal("CParser: referenced register not found for %s[%s]#%d in (%s)", binding.getClass().getSimpleName(), binding.getName(), binding.hashCode(), binding.getOwner());
                }
            }
            return refReg;
        } else if (expression instanceof IASTUnaryExpression) {
            IASTUnaryExpression unaryExpr = (IASTUnaryExpression) expression;
            int op = unaryExpr.getOperator();
            IASTExpression innerExpr = unparenthesize(unaryExpr.getOperand());
            if (op == IASTUnaryExpression.op_star) {
                return handleRvalue(innerExpr);
            } else if (op == IASTUnaryExpression.op_bracketedPrimary)
                Messages.fatal("CParser: brackets should have been unparenthesized for [%s]", expression.getRawSignature());
            Messages.fatal("CParser: modifying non-lvalue %s [%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        } else if (expression instanceof IASTBinaryExpression) {
            IASTBinaryExpression binaryExpr = (IASTBinaryExpression) expression;
            int op = binaryExpr.getOperator();
            if (op == IASTBinaryExpression.op_pmdot || op == IASTBinaryExpression.op_pmarrow) {
                Messages.error("CParser: rarely used binary-expr-represented field reference expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
                IASTExpression baseExpr = unparenthesize(binaryExpr.getOperand1());
                IASTExpression fieldExpr = unparenthesize(binaryExpr.getOperand2());
                IField field = (IField) ((IASTIdExpression) fieldExpr).getName().resolveBinding();
                boolean isPointerDeref = op == IASTBinaryExpression.op_pmarrow;
                return handleFieldReference(baseExpr, field, isPointerDeref);
            }
            Messages.fatal("CParser: modifying non-lvalue %s [%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        } else if (expression instanceof IASTFieldReference) {
            IASTFieldReference fieldRef = (IASTFieldReference) expression;
            IASTExpression baseExpr = unparenthesize(fieldRef.getFieldOwner());
            IField field = (IField) fieldRef.getFieldName().resolveBinding();
            return handleFieldReference(baseExpr, field, fieldRef.isPointerDereference());
        } else if (expression instanceof IASTArraySubscriptExpression) {
            IASTArraySubscriptExpression arraySubExpr = (IASTArraySubscriptExpression) expression;
            return handleArraySubscript(arraySubExpr);
        } else if (expression instanceof IASTLiteralExpression) {
            IASTLiteralExpression literal = (IASTLiteralExpression) expression;
            if (literal.getKind() == IASTLiteralExpression.lk_string_literal) {
                int refReg = processStringConstant(literal);
                throw new UnsupportedOperationException();
            }
        }
        Messages.fatal("CParser: non-lvalue %s [%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        return -1;
    }

    private int processStringConstant(IASTLiteralExpression literal) {
        int refReg;
        String s = String.valueOf(literal.getValue());
        if (stringConstants.containsKey(s)) {
            refReg = stringConstants.get(s);
        } else {
            registers.add(s);
            refReg = registers.indexOf(s);
            stringConstants.put(s, refReg);
        }
        return refReg;
    }

    private int handleRvalue(IASTExpression expression) {
        if (expression instanceof IASTLiteralExpression) {
            int reg = createRegister(expression);

            IASTLiteralExpression literalExpr = (IASTLiteralExpression) expression;
            ConstantEval literalEval = new ConstantEval(literalExpr);
            String constant = literalEval.getValue();

            Messages.debug("CParser: assign constant %s@%d := %s", expression.getExpressionType(), reg, constant);
            simpleConstants.put(reg, constant);
            IBasicBlock evalNode = new EvalNode(literalEval, reg);
            prevNode = connect(prevNode, evalNode);

            return reg;
        } else if (expression instanceof IASTIdExpression) {
            int reg = createRegister(expression);
            IBinding binding = ((IASTIdExpression) expression).getName().resolveBinding();
            if (binding instanceof IVariable) {
                // TODO: special handling of array-to-pointer conversion
                int refReg = getRefReg(binding);
                if (refReg < 0) {
                    IVariable v = (IVariable) binding;
                    refReg = processVariable(v, true);
                    Messages.debug("CParser: reference external variable %s[%s] at line#%d (%s)", binding.getClass().getSimpleName(), binding, expression.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                }
                Messages.debug("CParser: read from location %s@%d <- *@%d (%s)", expression.getExpressionType(), reg, refReg, expression.getRawSignature());
                IBasicBlock loadNode = new LoadNode(reg, refReg);
                prevNode = connect(prevNode, loadNode);

                return reg;
            } else if (binding instanceof IFunction) {
                int refReg = getRefReg(binding);
                if (refReg < 0) {
                    IFunction f = (IFunction) binding;

                    Messages.debug("CParser: reference external function %s[%s] at line#%d (%s)", binding.getClass().getSimpleName(), binding, expression.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                    refReg = processFunction(f);
                }
                return refReg;
            }
        } else if (expression instanceof IASTUnaryExpression) {
            IASTUnaryExpression unaryExpr = (IASTUnaryExpression) expression;
            int op = unaryExpr.getOperator();
            IASTExpression inner = unparenthesize(unaryExpr.getOperand());
            if (op == IASTUnaryExpression.op_bracketedPrimary)
                Messages.fatal("CParser: brackets should have been unparenthesized for [%s]", expression.getRawSignature());
            else if (op == IASTUnaryExpression.op_star) {
                if (expression.getExpressionType() instanceof IFunctionType) {
                    int reg = handleRvalue(inner);
                    Messages.debug("CParser: cancel dereference of function to location %s[%s] : %s@%s", expression.getClass().getSimpleName(), expression.getRawSignature(), expression.getExpressionType(), reg);
                    return reg;
                } else {
                    int innerReg = handleRvalue(inner);
                    int reg = createRegister(expression);
                    Messages.debug("CParser: read from location %s@%d <- *@%d (%s)", expression.getExpressionType(), reg, innerReg, expression.getRawSignature());
                    IBasicBlock loadNode = new LoadNode(reg, innerReg);
                    prevNode = connect(prevNode, loadNode);

                    return reg;
                }
            } else if (op == IASTUnaryExpression.op_plus || op == IASTUnaryExpression.op_minus || op == IASTUnaryExpression.op_not) {
                int innerReg = handleRvalue(inner);
                String opStr = "Unknown";
                switch (op) {
                    case IASTUnaryExpression.op_plus: opStr = UnaryEval.op_plus; break;
                    case IASTUnaryExpression.op_minus: opStr = UnaryEval.op_minus; break;
                    case IASTUnaryExpression.op_not: opStr = UnaryEval.op_not; break;
                }
                IEval eval = new UnaryEval(expression, opStr, innerReg);
                int reg = createRegister(expression);
                Messages.debug("CParser: compute unary expr in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, reg);
                prevNode = connect(prevNode, evalNode);

                return reg;
            } else if (op == IASTUnaryExpression.op_prefixIncr || op == IASTUnaryExpression.op_prefixDecr
                    || op == IASTUnaryExpression.op_postFixIncr || op == IASTUnaryExpression.op_postFixDecr) {
                Messages.warn("CParser: side effect in right value %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());

                return handleExpression(expression);
            } else if (op == IASTUnaryExpression.op_amper) {
                if (inner instanceof IASTUnaryExpression && ((IASTUnaryExpression) inner).getOperator() == IASTUnaryExpression.op_star) {
                    IASTExpression cancelledExpr = unparenthesize(((IASTUnaryExpression) inner).getOperand());
                    return handleRvalue(cancelledExpr);
                }

                return handleLvalue(inner);
            } else if (op == IASTUnaryExpression.op_sizeof) {
                int reg = createRegister(expression);
                if (inner.getExpressionType() instanceof IArrayType) {
                    int innerReg = handleRvalue(inner);
                    IEval eval = new ArraySizeEval(expression, innerReg);
                    IBasicBlock evalNode = new EvalNode(eval, reg);
                    prevNode = connect(prevNode, evalNode);
                } else {
                    String constant;
                    if (inner instanceof IASTLiteralExpression && ((IASTLiteralExpression) inner).getKind() == IASTLiteralExpression.lk_string_literal) {
                        processStringConstant((IASTLiteralExpression) inner);
                        constant = "sizeof(" + String.valueOf(((IASTLiteralExpression) inner).getValue()) + ")";
                    } else {
                        Messages.debug("CParser: do not evaluate non-VLA type %s[%s] in %s[%s]", inner.getExpressionType().getClass().getSimpleName(), inner.getExpressionType(), expression.getClass().getSimpleName(), expression.getRawSignature());
                        constant = "sizeof(" + inner.getExpressionType() + ")";
                    }
                    IEval eval = new ConstantEval(expression, constant);
                    Messages.debug("CParser: assign constant %s@%d := %s", expression.getExpressionType(), reg, constant);
                    simpleConstants.put(reg, constant);
                    IBasicBlock evalNode = new EvalNode(eval, reg);
                    prevNode = connect(prevNode, evalNode);
                }
                return reg;
            }
        } else if (expression instanceof IASTBinaryExpression) {
            IASTBinaryExpression binExpr = (IASTBinaryExpression) expression;
            int op = binExpr.getOperator();
            if (binExpr.isLValue()) {
                Messages.warn("CParser: side effect in right value %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
                return handleExpression(expression);
            } else if (op == IASTBinaryExpression.op_pmdot || op == IASTBinaryExpression.op_pmarrow) {
                Messages.error("CParser: rarely used binary-expr-represented field reference expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());

                IASTExpression baseExpr = unparenthesize(binExpr.getOperand1());
                IASTExpression fieldExpr = unparenthesize(binExpr.getOperand2());
                IField field = (IField) ((IASTIdExpression) fieldExpr).getName().resolveBinding();
                boolean isPointerDeref = op == IASTBinaryExpression.op_pmarrow;
                int refReg = handleFieldReference(baseExpr, field, isPointerDeref);
                int reg = createRegister(expression);
                IBasicBlock loadNode = new LoadNode(reg, refReg);
                prevNode = connect(prevNode, loadNode);
                return reg;
            } else if (op == IASTBinaryExpression.op_logicalAnd || op == IASTBinaryExpression.op_logicalOr) {
                return handleShortcircuitExpression(binExpr);
            } else {
                // TODO: C standard does not specify the evaluation order of arithmetical expressions
                IASTExpression op1 = unparenthesize(binExpr.getOperand1());
                int r1 = handleRvalue(op1);

                IASTExpression op2 = unparenthesize(binExpr.getOperand2());
                int r2 = handleRvalue(op2);

                String opStr = "Unknown";
                switch (op) {
                    case IASTBinaryExpression.op_plus:
                        opStr = BinaryEval.op_plus;
                        break;
                    case IASTBinaryExpression.op_minus:
                        opStr = BinaryEval.op_minus;
                        break;
                    case IASTBinaryExpression.op_multiply:
                        opStr = BinaryEval.op_multiply;
                        break;
                    case IASTBinaryExpression.op_divide:
                        opStr = BinaryEval.op_divide;
                        break;
                    case IASTBinaryExpression.op_modulo:
                        opStr = BinaryEval.op_modulo;
                        break;
                    case IASTBinaryExpression.op_equals:
                        opStr = BinaryEval.op_eq;
                        break;
                    case IASTBinaryExpression.op_notequals:
                        opStr = BinaryEval.op_ne;
                        break;
                    case IASTBinaryExpression.op_lessThan:
                        opStr = BinaryEval.op_lt;
                        break;
                    case IASTBinaryExpression.op_greaterThan: {
                        opStr = BinaryEval.op_lt;
                        int rr = r1;
                        r1 = r2;
                        r2 = rr;
                        break;
                    }
                    case IASTBinaryExpression.op_lessEqual:
                        opStr = BinaryEval.op_le;
                        break;
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
                    case IASTBinaryExpression.op_binaryXor:
                        opStr = BinaryEval.op_bit;
                        break;
                }
                IEval eval = new BinaryEval(expression, opStr, r1, r2);
                int reg = createRegister(expression);
                Messages.debug("CParser: compute binary expr in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
                IBasicBlock evalNode = new EvalNode(eval, reg);
                prevNode = connect(prevNode, evalNode);

                return reg;
            }
        } else if (expression instanceof IASTFieldReference) {
            IASTFieldReference fieldRef = (IASTFieldReference) expression;
            IASTExpression baseExpr = unparenthesize(fieldRef.getFieldOwner());
            IField field = (IField) fieldRef.getFieldName().resolveBinding();
            int refReg = handleFieldReference(baseExpr, field, fieldRef.isPointerDereference());
            int reg = createRegister(expression);
            Messages.debug("CParser: load from field pointer %s@%d <- *@%d[%s]", expression.getExpressionType(), reg, refReg, expression.getRawSignature());
            IBasicBlock loadNode = new LoadNode(reg, refReg);
            prevNode = connect(prevNode, loadNode);

            return reg;
        } else if (expression instanceof IASTArraySubscriptExpression) {
            IASTArraySubscriptExpression arraySubExpr = (IASTArraySubscriptExpression) expression;
            int refReg = handleArraySubscript(arraySubExpr);
            int reg = createRegister(expression);
            Messages.debug("CParser: load from offset pointer %s@%d <- *@%d[%s]", expression.getExpressionType(), reg, refReg, expression.getExpressionType());
            IBasicBlock loadNode = new LoadNode(reg, refReg);
            prevNode = connect(prevNode, loadNode);

            return reg;
        } else if (expression instanceof IASTFunctionCallExpression) {
            IASTFunctionCallExpression invk = (IASTFunctionCallExpression) expression;

            IASTExpression fNameExpr = unparenthesize(invk.getFunctionNameExpression());
            int fReg = handleFunctionName(fNameExpr);

            // TODO: C standard does not specify the evaluation order of arguments
            int[] fArgRegs = new int[invk.getArguments().length];
            for (int i = 0; i < fArgRegs.length; ++i) {
                IASTExpression fArgExpr = unparenthesize((IASTExpression) invk.getArguments()[i]);
                if (fArgExpr instanceof IASTFunctionCallExpression) {
                    Messages.warn("CParser: embedded function call in [%s]", expression.getRawSignature());
                }
                fArgRegs[i] = handleRvalue(fArgExpr);
            }

            IEval eval;
            if (fReg < 0) {
                IFunction f = staticInvkMap.get(fNameExpr);
                if (f == null) {
                    Messages.fatal("CParser: cannot resolve function name %s[%s] at line#%d (%s)", fNameExpr.getClass().getSimpleName(), fNameExpr.getRawSignature(), fNameExpr.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                }
                eval = new StaticCallEval(expression, f, fArgRegs);
            } else {
                eval = new IndirectCallEval(expression, fReg, fArgRegs);
            }
            int reg = createRegister(expression);
            Messages.debug("CParser: compute invocation in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
            IBasicBlock evalNode = new EvalNode(eval, reg);
            prevNode = connect(prevNode, evalNode);

            return reg;
        } else if (expression instanceof IASTCastExpression) {
            IASTExpression innerExpr = unparenthesize(((IASTCastExpression) expression).getOperand());
            int innerReg = handleRvalue(innerExpr);
            IEval eval = new CastEval(expression, innerReg);
            int reg = createRegister(expression);
            Messages.debug("CParser: casting expression to %s@%d := %s", eval.getType(), reg, eval.toDebugString());
            IBasicBlock evalNode = new EvalNode(eval, reg);
            prevNode = connect(prevNode, evalNode);
            return reg;
        } else if (expression instanceof IASTTypeIdExpression) {
            IASTTypeIdExpression typeIdExpression = (IASTTypeIdExpression) expression;
            if (typeIdExpression.getOperator() == IASTTypeIdExpression.op_sizeof) {
                String constant = "sizeof(" + typeIdExpression.getTypeId().getRawSignature() + ")";

                ConstantEval sizeEval = new ConstantEval(typeIdExpression, constant);
                int reg = createRegister(expression);
                Messages.debug("CParser: assign size-of constant %s@%d := %s", expression.getExpressionType(), reg, constant);
                simpleConstants.put(reg, constant);
                IBasicBlock evalNode = new EvalNode(sizeEval, reg);
                prevNode = connect(prevNode, evalNode);
                return reg;
            }
        }
        if (expression == null) {
            registers.add("literal: 1");
            int reg = registers.indexOf("literal: 1");
            Messages.debug("CParser: assign null expression as constant value 1");
            return reg;
        }
        registers.add(expression);
        int reg = registers.indexOf(expression);
        Messages.error("CParser: skip unsupported C Rvalue expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        return reg;
    }

    private int handleFieldReference(IASTExpression baseExpr, IField field, boolean isPointerDereference) {
        int basePtr;
        if (isPointerDereference) {
            basePtr = handleRvalue(baseExpr);
        } else {
            basePtr = handleLvalue(baseExpr);
        }
        GetElementPtrEval gepEval = new GetFieldPtrEval(baseExpr.getExpressionType(), basePtr, field);
        registers.add(gepEval);
        int fieldPtr = registers.indexOf(gepEval);
        Messages.debug("CParser: compute field pointer %d = &(%d->%s)", fieldPtr, basePtr, field);
        return fieldPtr;
    }

    private int handleArraySubscript(IASTArraySubscriptExpression arraySubExpr) {
        IASTExpression expr1 = unparenthesize(arraySubExpr.getArrayExpression());
        int reg1 = handleRvalue(expr1);

        IASTExpression expr2 = unparenthesize((IASTExpression) arraySubExpr.getArgument());
        int reg2 = handleRvalue(expr2);

        GetElementPtrEval gepEval;
        if (expr1.getExpressionType() instanceof ITypeContainer) {
            gepEval = new GetOffsetPtrEval(expr1.getExpressionType(), reg1, expr2, reg2);
        } else {
            gepEval = new GetOffsetPtrEval(expr2.getExpressionType(), reg2, expr1, reg1);
        }
        registers.add(gepEval);
        int offsetPtr = registers.indexOf(gepEval);
        Messages.debug("CParser: compute offset pointer %d = (%d+%d)", offsetPtr, reg1, reg2);
        return offsetPtr;
    }

    private int handleFunctionName(IASTExpression fNameExpr) {
       IFunction receiver = findReceiver(fNameExpr);
        if (receiver != null) {
            Messages.debug("CParser: resolve static invocation %s[%s] to %s", fNameExpr.getClass().getSimpleName(), fNameExpr.getRawSignature(), receiver);
            if (!funcs.contains(receiver)) {
                Messages.debug("CParser: invoke external function %s[%s] at line#%d (%s)", receiver.getClass().getSimpleName(), receiver, fNameExpr.getFileLocation().getStartingLineNumber(), fNameExpr.getRawSignature());
                processFunction(receiver);
            }
            staticInvkMap.put(fNameExpr, receiver);
            return -1;
        } else {
            return handleRvalue(fNameExpr);
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

    private int handleShortcircuitExpression(IASTBinaryExpression expression) {
        int op = expression.getOperator();
        assert op == IASTBinaryExpression.op_logicalAnd || op == IASTBinaryExpression.op_logicalOr;

        IASTExpression lval = unparenthesize(expression.getOperand1());
        int lReg = handleRvalue(lval);

        CondNode shortCircuit = new CondNode(lReg);
        connect(prevNode, shortCircuit);

        IBasicBlock trueNode = new LabelNode(IBranchNode.THEN);
        IBasicBlock falseNode = new LabelNode(IBranchNode.ELSE);
        connect(shortCircuit, trueNode);
        connect(shortCircuit, falseNode);

        IASTExpression rval = unparenthesize(expression.getOperand2());
        int rReg;
        if (op == IASTBinaryExpression.op_logicalAnd) {
            prevNode = trueNode;
            rReg = handleRvalue(rval);
            trueNode = prevNode;
        } else {
            prevNode = falseNode;
            rReg = handleRvalue(rval);
            falseNode = prevNode;
        }

        IConnectorNode phiNode = new PhiNode("shortcircuit");
        shortCircuit.setMergeNode(phiNode);
        jump(trueNode, phiNode, false);
        jump(falseNode, phiNode, false);

        if (fixPhi(phiNode))
            prevNode = phiNode;
        else
            prevNode = null;

        String opStr = "Unknown";
        if (op == IASTBinaryExpression.op_logicalAnd)
            opStr = BinaryEval.op_and;
        else
            opStr = BinaryEval.op_or;
        IEval eval = new BinaryEval(expression, opStr, lReg, rReg);
        int reg = createRegister(expression);
        Messages.debug("CParser: compute result of and/or in %s@%d := %s", eval.getType(), reg, eval.toDebugString());
        IBasicBlock evalNode = new EvalNode(eval, reg);
        prevNode = connect(prevNode, evalNode);
        return reg;
    }

    private void handleIfStatement(IASTIfStatement cIf) {
        IASTExpression condExpr = unparenthesize(cIf.getConditionExpression());
        int condReg = handleRvalue(condExpr);

        CondNode ifNode = new CondNode(condReg);
        connect(prevNode, ifNode);

        IBasicBlock thenNode = new LabelNode(IBranchNode.THEN);
        connect(ifNode, thenNode);
        prevNode = thenNode;
        expandGraph(cIf.getThenClause());
        thenNode = prevNode;

        IBasicBlock elseNode = new LabelNode(IBranchNode.ELSE);
        connect(ifNode, elseNode);
        prevNode = elseNode;
        expandGraph(cIf.getElseClause());
        elseNode = prevNode;

        IConnectorNode phiNode = new PhiNode("endif");
        ifNode.setMergeNode(phiNode);
        jump(thenNode, phiNode, false);
        jump(elseNode, phiNode, false);

        if (fixPhi(phiNode))
            prevNode = phiNode;
        else
            prevNode = null;
    }

    private void handleWhileStatement(IASTWhileStatement cWhile) {
        IConnectorNode continueNode = new PhiNode("continue");
        IConnectorNode breakNode = new PhiNode("break");

        connect(prevNode, continueNode);

        IASTExpression condExpr = cWhile.getCondition();
        IASTStatement loopBody = cWhile.getBody();

//        if (condExpr == null) {
//            IBasicBlock bodyEnd = handleLoopBody(continueNode, loopBody, continueNode, breakNode);
//            jump(bodyEnd, continueNode, true);

        condExpr = unparenthesize(condExpr);
        prevNode = continueNode;
        int condReg = handleRvalue(condExpr);
        IBasicBlock evalCondNode = prevNode;

        CondNode whileNode = new CondNode(condReg);
        connect(evalCondNode, whileNode);
        whileNode.setMergeNode(breakNode);

        IBasicBlock bodyStart = new LabelNode(IBranchNode.THEN);
        connect(whileNode, bodyStart);
        prevNode = bodyStart;
        handleLoopBody(loopBody, continueNode, true, breakNode);
        IBasicBlock bodyEnd = prevNode;
        jump(bodyEnd, continueNode, true);

        IBasicBlock loopEnd = new LabelNode(IBranchNode.ELSE);
        connect(whileNode, loopEnd);
        jump(loopEnd, breakNode, false);

        if (fixPhi(breakNode))
            prevNode = breakNode;
        else
            prevNode = null;
    }

    private void handleDoWhileStatement(IASTDoStatement cDoWhile) {
        IConnectorNode continueNode = new PhiNode("continue");
        IConnectorNode breakNode = new PhiNode("break");

        IASTExpression condExpr = cDoWhile.getCondition();
        IASTStatement loopBody = cDoWhile.getBody();

        IConnectorNode bodyStart = new PhiNode("do");
        connect(prevNode, bodyStart);

        prevNode = bodyStart;
        handleLoopBody(loopBody, continueNode, false, breakNode);
        IBasicBlock bodyEnd = prevNode;
        jump(bodyEnd, continueNode, false);

        prevNode = continueNode;
        int condReg = handleRvalue(condExpr);
        IBasicBlock evalCondNode = prevNode;
        CondNode doWhileNode = new CondNode(condReg);
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
            prevNode = breakNode;
        else
            prevNode = null;
    }


    private void handleForStatement(IASTForStatement cFor) {
        IConnectorNode continueNode = new PhiNode("continue");
        IConnectorNode breakNode = new PhiNode("break");

        IASTStatement init = cFor.getInitializerStatement();
        expandGraph(init);

        IConnectorNode condStart = new PhiNode();
        connect(prevNode, condStart);

        IASTExpression condExpr = cFor.getConditionExpression();
        prevNode = condStart;
        int condReg = handleRvalue(condExpr);
        IBasicBlock evalCondNode = prevNode;
        CondNode forNode = new CondNode(condReg);
        connect(evalCondNode, forNode);
        forNode.setMergeNode(breakNode);

        IASTStatement loopBody = cFor.getBody();
        IBranchNode bodyStart = new LabelNode(IBranchNode.THEN);
        connect(forNode, bodyStart);
        prevNode = bodyStart;
        handleLoopBody(loopBody, continueNode, false, breakNode);
        IBasicBlock bodyEnd = prevNode;
        jump(bodyEnd, continueNode, false);

        IASTExpression iter = cFor.getIterationExpression();
        prevNode = continueNode;
        handleExpression(iter);
        IBasicBlock afterIter = prevNode;
        jump(afterIter, condStart, true);

        IBranchNode loopEnd = new LabelNode(IBranchNode.ELSE);
        connect(forNode, loopEnd);
        jump(loopEnd, breakNode, false);

        fixPhi(continueNode);
        if (fixPhi(breakNode))
            prevNode = breakNode;
        else
            prevNode = null;
    }

    private void handleLoopBody(IASTStatement body, IConnectorNode continueNode, boolean continueBackward, IConnectorNode breakNode) {
        IConnectorNode savedContinue = outerContinueTarget;
        boolean savedContinuePos = outerContinueBackward;
        IConnectorNode savedBreak = outerBreakTarget;
        outerContinueTarget = continueNode;
        outerContinueBackward = continueBackward;
        outerBreakTarget = breakNode;
        expandGraph(body);
        outerContinueTarget = savedContinue;
        outerContinueBackward = savedContinuePos;
        outerBreakTarget = savedBreak;
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
                    Messages.error("CParser: brackets should have been unparenthesized for [%s]", expression.getRawSignature());
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
    @Deprecated
    public int fetchRegister(IASTExpression expression) {
        return getRegister(expression, false);
    }

    private int createRegister(IASTExpression expression) {
        if (expression.getExpressionType().isSameType(CBasicType.VOID)) {
//            Messages.debug("CParser: create null register -1 for void-type expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
//            return -1;
            Messages.debug("CParser: create register for void-type expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        }
        return getRegister(expression, true);
    }

    public void dumpDot(PrintWriter pw) {
        pw.println("digraph \"" + transUnit.getContainingFilename() + "\" {");
        pw.println("compound=true;");
        for (IFunction meth : funcs) {
            CFGBuilder.IntraCFG cfg = getIntraCFG(meth);
            int reg = getRefReg(meth);
            pw.print("r" + reg + " ");
            pw.print("[label=\"#" + reg + ":refFunc-" + meth + "\"");
            pw.print(";color=navy");
            pw.println("]");

            if (cfg != null) {
                cfg.dumpDotString(pw);
                pw.print("r" + reg + " -> f" + meth.hashCode());
                pw.println(" [arrowhead=odot;style=bold;lhead=cluster_" + meth.hashCode() + "]");

            } else {
                pw.print("f" + meth.hashCode() + " ");
                pw.println("[label=\"extern " + meth + "\"");
                pw.print(";shape=doubleoctagon");
                pw.println("]");

                pw.print("r" + reg + " -> f" + meth.hashCode());
                pw.println(" [arrowhead=odot;style=bold]");
            }
            pw.flush();
        }
//        pw.println("subgraph cluster_global {");
        for (IVariable v: staticVars) {
            int reg = getRefReg(v);
            pw.print("r" + reg + " ");
            pw.print("[label=\"#" + reg + ":refVar-" + v + "\"");
            pw.print(";color=navy");
            pw.println("]");
            pw.print("v" + v.hashCode());
            pw.print("[label=\"var-" + v + "\"");
            pw.print(";color=brown");
            pw.println("]");
            pw.print("r" + reg + " -> v" + v.hashCode());
            pw.println(" [arrowhead=odot;style=bold]");
        }
//        pw.println("}");
        pw.println("}");
        pw.flush();
    }

    public class IntraCFG extends ControlFlowGraph {
        private final IFunction func;
        public IntraCFG(IFunction func, IStartNode start, Collection<IExitNode> exitNodes) {
            super(start, exitNodes);
            this.func = func;
        }

        public void dumpDotString(PrintWriter writer) {
            writer.println("subgraph cluster_" + func.hashCode() + " {");
            writer.println("label = \"" + func + "\"");
            writer.print("f" + func.hashCode() + " ");
            writer.println("[label=\"" + func + "\"");
            writer.print(";shape=doubleoctagon");
            writer.println("]");
            Set<Integer> localRegs = new LinkedHashSet<>();
            for (IBasicBlock n: getNodes()) {
                ICFGNode node = (ICFGNode) n;
                writer.print("n" + node.hashCode() + " ");
                writer.print("[label=\"" + node.toDebugString() + "\"");
                if (getDeadNodes().contains(node)) {
                    writer.print(";bgcolor=gray");
                }
                if (node instanceof IStartNode) {
                    writer.print(";shape=Mdiamond");
                } else if (node instanceof IExitNode) {
                    writer.print(";shape=trapezium");
                } else if (node instanceof IDecisionNode) {
                    writer.print(";shape=diamond");
                } else if (node instanceof IConnectorNode) {
                    writer.print(";shape=pentagon");
                } else if (node instanceof IBranchNode) {
                    writer.print(";shape=house");
                } else if (node instanceof IJumpNode) {
                    writer.print(";shape=invhouse");
                } else if (node instanceof IPlainNode) {
                    writer.print(";shape=rectangle");
                }

                if (node instanceof StoreNode) {
                    writer.print(";color=red");
                } else if (node instanceof LoadNode) {
                    writer.print(";color=green");
                } else if (node instanceof EvalNode) {
                    writer.print(";color=blue");
                } else if (node instanceof AllocNode) {
                    writer.print(";color=purple");
                }
                writer.println("]");

                if (node instanceof StoreNode) {
                    localRegs.add(((StoreNode) node).getPointer());
                    localRegs.add(((StoreNode) node).getValue());
                } else if (node instanceof LoadNode) {
                    localRegs.add(((LoadNode) node).getValue());
                    localRegs.add(((LoadNode) node).getPointer());
                } else if (node instanceof EvalNode) {
                    localRegs.add(((EvalNode) node).getRegister());
                    for (int reg: ((EvalNode) node).getEvaluation().getOperands())
                        localRegs.add(reg);
                } else if (node instanceof AllocNode) {
                    localRegs.add(((AllocNode) node).getRegister());
                }
            }
//            writer.println("subgraph cluster_regs" + func.hashCode() + " {");
            for (int reg : localRegs) {
                if (reg < 0)
                    continue;
                Object debugObj = registers.get(reg);
                if (debugObj instanceof IASTExpression) {
                    writer.print("r" + reg + " ");
                    writer.print("[label=\"#" + reg + ":" + ((IASTExpression) debugObj).getRawSignature() + "\"");
                    writer.print(";color=tan");
                    writer.println("]");
                } else if (debugObj instanceof IVariable && !staticVars.contains(debugObj)) {
                    writer.print("r" + reg + " ");
                    writer.print("[label=\"#" + reg + ":refVar-" + debugObj + "\"");
                    writer.print(";color=navy");
                    writer.println("]");
                } else if (debugObj instanceof Pair) {
                    writer.print("r" + reg + " ");
                    writer.print("[label=\"" + reg + ":Arg-" + debugObj + "\"");
                    writer.print(";color=indigo");
                    writer.println("]");
                } else if (debugObj instanceof GetElementPtrEval) {
                    writer.print("r" + reg + " ");
                    writer.print("[label=\"" + reg + ":" + ((GetElementPtrEval) debugObj).toDebugString() + "\"");
                    writer.print(";color=skyblue");
                    writer.println("]");
                }
            }
//            writer.println("}");
//            writer.println("subgraph cluster_vars" + func.hashCode() + " {");
            for (IVariable v: funcVars.get(func)) {
                writer.print("v" + v.hashCode());
                if (v instanceof IParameter)
                    writer.print("[label=\"param-" + v + "\"");
                else
                    writer.print("[label=\"var-" + v + "\"");
                writer.print(";color=brown");
                writer.println("]");
            }
//            writer.println("}");
            writer.println("}");
            for (IBasicBlock p : getNodes()) {
                for (IBasicBlock q : p.getOutgoingNodes()) {
                    if (q == null) {
                        if (!getDeadNodes().contains(p))
                            Messages.error("CParser: null outgoing node from n%d[%s]", p.hashCode(), ((ICFGNode) p).toDebugString());
                        continue;
                    }
                    writer.print("n" + p.hashCode() + " -> n" + q.hashCode());
                    if (getDeadNodes().contains(p) || getDeadNodes().contains(q)) {
                        writer.print(" [color=gray");
                    } else {
                        writer.print(" [color=black");
                    }
                    if (p instanceof GotoNode && ((GotoNode) p).isBackwardArc()) {
                        writer.print(";style=dashed");
                    }
                    writer.println("]");
                }
                if (p instanceof StoreNode) {
                    int ptr = ((StoreNode) p).getPointer();
                    int reg = ((StoreNode) p).getValue();
                    writer.print("n" + p.hashCode() + " -> r" + ptr);
                    writer.println(" [arrowhead=diamond;color=red;style=dashed]");
                    writer.print("r" + reg + " -> n" + p.hashCode());
                    writer.println(" [arrowhead=none;color=red;style=dotted]");
                    writer.print("r" + reg + " -> r" + ptr);
                    writer.println(" [arrowhead=open;color=red;style=dotted]");
                } else if (p instanceof LoadNode) {
                    int ptr = ((LoadNode) p).getPointer();
                    int reg = ((LoadNode) p).getValue();
                    writer.print("n" + p.hashCode() + " -> r" + ptr);
                    writer.println(" [arrowhead=box;color=green;style=dashed]");
                    writer.print("n" + p.hashCode() + " -> r" + reg);
                    writer.println(" [arrowhead=none;color=green;style=dotted]");
                    writer.print("r" + ptr + " -> r" + reg);
                    writer.println(" [arrowhead=open;color=green;style=dotted]");
                } else if (p instanceof EvalNode) {
                    IEval eval = ((EvalNode) p).getEvaluation();
                    int reg = ((EvalNode) p).getRegister();
                    if (reg >= 0) {
                        writer.print("n" + p.hashCode() + " -> r" + reg);
                        writer.println(" [arrowhead=none;color=blue;style=dashed]");
                        for (int param : eval.getOperands()) {
                            writer.print("r" + param + " -> r" + reg);
                            writer.println(" [arrowhead=open;style=dotted;color=aqua]");
                        }
                    }
                } else if (p instanceof AllocNode) {
                    int ptr = ((AllocNode) p).getRegister();
                    //IType ty = ((AllocNode) p).getAllocType();
                    IBinding obj = (IBinding) registers.get(ptr);
                    writer.print("n" + p.hashCode() + " -> r" + ptr);
                    writer.println(" [arrowhead=dot;color=purple;style=dashed]");
                    writer.print("r" + ptr + " -> v" + obj.hashCode());
                    writer.println(" [arrowhead=odot;color=purple;style=bold]");
                } else if (p instanceof ReturnNode) {
                    int reg = ((ReturnNode) p).getRegister();
                    if (reg >= 0) {
                        writer.print("n" + p.hashCode() + " -> r" + reg);
                        writer.println(" [arrowhead=none;color=cyan;style=dashed]");
                    }
                } else if (p instanceof CondNode) {
                    int reg = ((CondNode) p).getRegister();
                    if (reg >= 0) {
                        writer.print("n" + p.hashCode() + " -> r" + reg);
                        writer.println(" [arrowhead=none;color=violet;style=dashed]");
                    }
                }
            }
            writer.println();
            writer.flush();
        }
    }
}
