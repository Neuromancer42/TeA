package com.neuromancer42.tea.codemanager.cdt;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.util.IndexMap;
import com.neuromancer42.tea.commons.util.WeakIdentityHashMap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.ICASTKnRFunctionDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.ITypeContainer;
import org.eclipse.cdt.internal.core.dom.parser.IntegralValue;
import org.eclipse.cdt.internal.core.dom.parser.ProblemBinding;
import org.eclipse.cdt.internal.core.dom.parser.c.CArrayType;
import org.eclipse.cdt.internal.core.dom.parser.c.CBasicType;
import org.eclipse.cdt.internal.core.dom.parser.c.CPointerType;
import org.neuromancer42.tea.ir.CFG;
import org.neuromancer42.tea.ir.Expr;

import java.io.PrintWriter;
import java.util.*;

public class CFGBuilder {
    private final IASTTranslationUnit transUnit;
    private final Map<IFunction, ValueGraph<CFG.CFGNode, Integer>> intraCFGMap;
    private final Map<IFunction, CFG.CFGNode> funcEntryMap;

    private final IndexMap<Object> registers;
    // a register is either:
    // 1. <IASTExpression> the result value of a non-lvalue expression
    // 2. <IBinding> a pointer points to a declared variable/function
    // 3. <Pair<IFunction, Integer>> the value of a function parameter
    // 4. <String> a pointer points to the address of a string literal
    // 5. <Pair<GepExpression, Integer>> inserted gep result for field/offset access
    // 6. <Pair<Integer, IArrayType>> inserted load instruction to get base pointer of an array reference
    // 7. <Number> constant integers
    // 8. <Pair<Type, String>> a pointer points to an undeclared identifier (a.k.a. in an include file)
    
    private int gepIdx = 0;

    private final Map<String, IFunction> funcs;
    private final Map<IFunction, Set<Integer>> funcVars;
    private final Set<Integer> staticRefs;
    private final Map<Integer, CFG.Alloca> allocaMap;
    private final Map<IASTExpression, CFG.Alloca> mallocMap;

    private final Set<IType> types;
    private final Set<IField> fields;

    private final Map<IFunction, int[]> funcArgsMap;
    private final Map<IBinding, Integer> refRegMap;
    private Map<Pair<IType, String>, Integer> inclIdMap;
    private final Map<IASTExpression, IFunction> staticInvkMap;
    private final Map<String, Integer> stringConstants;
    private final Map<Integer, String> simpleConstants;

    // TODO: use struct signatures rather than statement references to collect fields

    private CFG.CFGNode start;
    private List<CFG.CFGNode> exits;
    private List<CFG.CFGNode> unreachable;
    private CFG.CFGNode outerContinueTarget;
    private boolean outerContinueBackward;
    private CFG.CFGNode outerBreakTarget;
    private Map<ILabel, CFG.CFGNode> labelMap;
    private Map<ILabel, Set<CFG.CFGNode>> unresolvedGotos;
    private IFunction curFunc;
    private int nodeIdx;
    private CFG.CFGNode prevNode;
    private ImmutableValueGraph.Builder<CFG.CFGNode, Integer> intraCFGBuilder;

    public CFGBuilder(IASTTranslationUnit tu) {
        this.transUnit = tu;
        intraCFGMap = new HashMap<>();
        funcEntryMap = new HashMap<>();
        registers = new IndexMap<>();

        funcs = new LinkedHashMap<>();
        funcVars = new HashMap<>();
        staticRefs = new LinkedHashSet<>();
        allocaMap = new LinkedHashMap<>();
        mallocMap = new LinkedHashMap<>();
        types = new LinkedHashSet<>();
        fields = new LinkedHashSet<>();

        funcArgsMap = new HashMap<>();
        refRegMap = new HashMap<>();
        inclIdMap = new HashMap<>();
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
                buildIntraCFG(fDef);
            }
        }
    }

    public ValueGraph<CFG.CFGNode, Integer> getIntraCFG(IFunction func) {
        return intraCFGMap.get(func);
    }

    public CFG.CFGNode getEntryNode(IFunction func) {
        return funcEntryMap.get(func);
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
            if (var.getType().isSameType(CBasicType.VOID)) {
                Messages.warn("CParser: skip declared null variable %s[%s] at line#%d: (%s)", var.getClass().getSimpleName(), var, declarator.getFileLocation().getStartingLineNumber(), declarator.getRawSignature());
                return null;
            }
            processVariable(var, isStatic);
        } else if (binding instanceof IFunction) {
            IFunction func = (IFunction) binding;
            if (funcs.containsValue(func))
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
            staticRefs.add(vReg);
            allocaMap.put(vReg, newAlloca(vReg, var));
        } else {
            funcVars.get(curFunc).add(vReg);
        }
        processType(var.getType());
        Messages.debug("CParser: create ref-pointer *(%s)@%d for %s#%s in (%s)", var.getType(), vReg, var.getName(), Integer.toUnsignedString(var.hashCode()), var.getOwner());
        return vReg;
    }

    private int processIdentifier(IType type, IASTName name) {
        String id = new String(name.toCharArray());
        Pair<IType, String> globVar = new ImmutablePair<>(type, id);
        registers.add(globVar);
        int reg = registers.indexOf(globVar);
        inclIdMap.put(globVar, reg);
        staticRefs.add(reg);
        allocaMap.put(reg, newAlloca(reg, id, type));
        processType(type);
        Messages.debug("CParser: create ref-pointer *(%s)@%d for identifier %s#%s", type, reg, name, Integer.toUnsignedString(name.hashCode()));
        return reg;
    }


    private int processFunction(IFunction func) {
        registers.add(func);
        int fReg = registers.indexOf(func);
        funcs.put(CDTUtil.methToRepr(func), func);
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
                Pair<IFunction, Integer> arg = new ImmutablePair<>(func, i);
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
            IArrayType arrType = (IArrayType) type;
            if (arrType.hasSize()) {
                Number size = arrType.getSize().numberValue();
                if (size == null) {
                    //TODO: Variable-Length Array?
                    String unkStr = "unknown";
                    registers.add(unkStr);
                    int reg = registers.indexOf(unkStr);
                    simpleConstants.put(reg, unkStr);
                } else {
                    registers.add(size);
                    int reg = registers.indexOf(size);
                    simpleConstants.put(reg, String.valueOf(size));
                }
            }
            processType(arrType.getType());
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
        return new LinkedHashSet<>(funcs.values());
    }

    public Set<Integer> getGlobalRefs() {
        return staticRefs;
    }

    public CFG.Alloca getAllocaForRef(int reg) {
        CFG.Alloca alloca = allocaMap.get(reg);
        if (alloca == null) {
            Messages.error("CParser: reg %d has no alloca processed", reg);
        }
        return alloca;
    }

    public Set<IASTExpression> getMallocs() {
        return mallocMap.keySet();
    }

    public CFG.Alloca getAllocaForMalloc(IASTExpression mallocExpr) {
        return mallocMap.get(mallocExpr);
    }

    public Set<Integer> getMethodVars(IFunction func) {
        return funcVars.get(func);
    }

    public Set<IType> getTypes() {
        return types;
    }

    public Set<IField> getFields() {
        return fields;
    }

    public void buildIntraCFG(IASTFunctionDefinition fDef) {
        intraCFGBuilder = ValueGraphBuilder.directed().nodeOrder(ElementOrder.stable()).incidentEdgeOrder(ElementOrder.stable()).allowsSelfLoops(true).immutable();
        IASTFunctionDeclarator fDecl = fDef.getDeclarator();
        curFunc = (IFunction) fDecl.getName().resolveBinding();
        nodeIdx = 0;
        start = newEntryNode(funcArgsMap.get(curFunc));
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
            return;
        }
        int[] argRegs = funcArgsMap.get(curFunc);
        for (int i = 0; i < paramDtors.length; ++i) {
            IASTDeclarator dtor = paramDtors[i];
            IParameter param = (IParameter) processDeclarator(dtor, false);
            if (param == null) {
                break;
            }
            int refReg = getRefReg(param);
            if (refReg < 0) {
                Messages.fatal("CParser: cannot find reference register for %s[%s]#%s in (%s)", param.getClass().getSimpleName(), param, Integer.toUnsignedString(param.hashCode()), param.getOwner());
            }
            Messages.debug("CParser: alloc stack memory for parameter ref @%d = {%s}", refReg, param);
            CFG.CFGNode allocNode = newAllocaNode(refReg, param);
            prevNode = connect(prevNode, allocNode);

            int aReg = argRegs[i];
            Messages.debug("CParser: store parameter in location @%d <- %s@%d (%s)", refReg, param.getType(), aReg, param);

            CFG.CFGNode storeNode = newStoreNode(refReg, aReg);
            prevNode = connect(prevNode, storeNode);
        }

        IASTStatement fBody = fDef.getBody();
        expandGraph(fBody);

        if (prevNode != null && !(prevNode.hasReturn()) && !(prevNode.hasGoto()) && !unreachable.contains(prevNode)) {
            CFG.CFGNode returnNode = newReturnNode();
            Messages.debug("CParser: add implicit return node [%s] for function [%s]", TextFormat.shortDebugString(returnNode), curFunc);
            connect(prevNode, returnNode);
            exits.add(returnNode);
        }
        for (ILabel label : labelMap.keySet()) {
            CFG.CFGNode labelNode = labelMap.get(label);
            if (!fixPhi(labelNode)) {
                Messages.debug("CParser: unused label %s[%s]", label.getClass().getSimpleName(), label);
            }
        }
        //cfg.setUnconnectedNodes(unreachable);
        intraCFGMap.put(curFunc, intraCFGBuilder.build());
        funcEntryMap.put(curFunc, start);
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
                            Messages.fatal("CParser: cannot find reference register for variable %s[%s]#%s in (%s)", var.getClass().getSimpleName(), var, Integer.toUnsignedString(var.hashCode()), var.getOwner());
                        }
                        Messages.debug("CParser: alloc stack memory for variable @%d = {%s}", refReg, var);
                        CFG.CFGNode allocNode = newAllocaNode(refReg, var);
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
                            Messages.fatal("CParser: cannot find reference register for variable %s[%s]#%s in (%s)", var.getClass().getSimpleName(), var, Integer.toUnsignedString(var.hashCode()), var.getOwner());
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
                jump(prevNode, outerBreakTarget);
                prevNode = null;
            } else {
                Messages.warn("CParser: skip invalid %s in no loop or switch", statement.getClass().getSimpleName());
            }
            return;
        } else if (statement instanceof IASTContinueStatement) {
            if (outerContinueTarget != null) {
                jump(prevNode, outerContinueTarget);
                prevNode = null;
            } else {
                Messages.warn("CParser: skip invalid %s in no loop", statement.getClass().getSimpleName());
            }
            return;
        } else if (statement instanceof IASTLabelStatement) {
            ILabel label = (ILabel) ((IASTLabelStatement) statement).getName().resolveBinding();
            CFG.CFGNode labelNode = newLabelNode(label.getName() + "@" + statement.getFileLocation().getStartingLineNumber());
            labelMap.put(label, labelNode);

            connect(prevNode, labelNode);
            // labels can come across returns, do not mark them as unreachable
            if (unreachable.remove(labelNode))
                Messages.debug("CParser: label [%s] after goto/continue/break/return", label);

            Set<CFG.CFGNode> gotoNodes = unresolvedGotos.remove(label);
            if (gotoNodes != null) {
                for (CFG.CFGNode gotoNode : gotoNodes) {
                    jump(gotoNode, labelNode);
                }
            }

            prevNode = labelNode;
            expandGraph(((IASTLabelStatement) statement).getNestedStatement());
            return;
        } else if (statement instanceof IASTGotoStatement) {
            ILabel label = (ILabel) ((IASTGotoStatement) statement).getName().resolveBinding();
            CFG.CFGNode labelNode = labelMap.get(label);
            if (labelNode != null) {
                jump(prevNode, labelNode);
            } else {
                unresolvedGotos.computeIfAbsent(label, l -> new HashSet<>()).add(prevNode);
            }
            prevNode = null;
            return;
        }else if (statement instanceof IASTReturnStatement) {
            IASTReturnStatement ret = (IASTReturnStatement) statement;
            IASTExpression retExpr = ret.getReturnValue();
            CFG.CFGNode returnNode;
            if (retExpr != null) {
                retExpr = unparenthesize(retExpr);
                int retReg = handleRvalue(retExpr);
                returnNode = newReturnNode(retReg);
            } else {
                returnNode = newReturnNode();
            }
            exits.add(returnNode);
            prevNode = connect(prevNode, returnNode);

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
            CFG.CFGNode storeNode = newStoreNode(refReg, initReg);
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
                        Expr.Expression gepExpr = null;
                        IType subElemType = null;
                        if (designator instanceof ICASTFieldDesignator) {
                            String fieldName = ((ICASTFieldDesignator) designator).getName().toString();
                            ICompositeType cType = (ICompositeType) elemType;
                            IField field = cType.findField(fieldName);
                            IField[] fields = cType.getFields();
                            gepExpr = newGetFieldPtrEval(elemType, elemPtr, field);
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
                                Pair<Integer, IArrayType> basePtrObj = new ImmutablePair<>(elemPtr, (IArrayType) elemType);
                                if (!registers.contains(basePtrObj)) {
                                    registers.add(basePtrObj);
                                    int basePtr = registers.indexOf(basePtrObj);
                                    Messages.debug("CParser: load base address of ptr-to-array @%d <- *@%d", basePtr, elemPtr);
                                    CFG.CFGNode loadNode = newLoadNode(basePtr, elemPtr);
                                    prevNode = connect(prevNode, loadNode);
                                }
                                Expr.Expression expr = newLiteralExpr(CBasicType.INT, offsetStr);
                                registers.add(offset);
                                int posReg = registers.indexOf(offset);
                                simpleConstants.put(posReg, offsetStr);
                                CFG.CFGNode evalNode = newEvalNode(posReg, expr);
                                prevNode = connect(prevNode, evalNode);

                                int basePtr = registers.indexOf(basePtrObj);
                                gepExpr = newGetIndexPtrEval(elemType, basePtr, posReg);
                                subElemType = ((IArrayType) elemType).getType();
                                if (i == 0)
                                    pos = offset;
                            }
                        }
                        if (gepExpr != null) {
                            elemPtr = createRegister(gepExpr);
                            Messages.debug("CParser: get element ptr for initialize @%d = {%s}", elemPtr, TextFormat.shortDebugString(gepExpr));
                            CFG.CFGNode gepNode = newEvalNode(elemPtr, gepExpr);
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
                    Expr.Expression gepExpr;
                    IType subType;
                    if (refType instanceof IArrayType) {
                        Pair<Integer, IArrayType> basePtrObj = new ImmutablePair<>(refReg, (IArrayType) refType);
                        if (!registers.contains(basePtrObj)) {
                            registers.add(basePtrObj);
                            int basePtr = registers.indexOf(basePtrObj);
                            Messages.debug("CParser: load base address of ptr-to-array @%d <- *@%d", basePtr, refReg);
                            CFG.CFGNode loadNode = newLoadNode(basePtr, refReg);
                            prevNode = connect(prevNode, loadNode);
                        }
                        Expr.Expression expr = newLiteralExpr(CBasicType.INT, String.valueOf(pos));
                        registers.add(pos);
                        int posReg = registers.indexOf(pos);
                        simpleConstants.put(posReg, String.valueOf(pos));
                        CFG.CFGNode evalNode = newEvalNode(posReg, expr);
                        prevNode = connect(prevNode, evalNode);

                        int basePtr = registers.indexOf(basePtrObj);
                        gepExpr = newGetIndexPtrEval(refType, basePtr, posReg);
                        subType = ((ITypeContainer) refType).getType();
                    } else {
                        ICompositeType cType = (ICompositeType) refType;
                        IField field = cType.getFields()[pos];
                        gepExpr = newGetFieldPtrEval(refType, refReg, field);
                        subType = field.getType();
                    }
                    int subReg = createRegister(gepExpr);
                    CFG.CFGNode gepNode = newEvalNode(subReg, gepExpr);
                    prevNode = connect(prevNode, gepNode);
                    Messages.debug("CParser: get element ptr for initialize @%d = {%s}", subReg, TextFormat.shortDebugString(gepExpr));

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
                CFG.CFGNode storeNode = newStoreNode(lRefReg, rValReg);
                prevNode = connect(prevNode, storeNode);

                return rValReg;
            } else if (binExpr.isLValue()) { // equivalent to op is all compound assignment operators (except op_assign)
                IASTExpression lval = unparenthesize(binExpr.getOperand1());
                int lRefReg = handleLvalue(lval);

                int prevReg = createRegister(lval);
                Messages.debug("CParser: read from location %s@%d <- *@%d (%s)", lval.getExpressionType(), prevReg, lRefReg, expression.getRawSignature());
                CFG.CFGNode loadNode = newLoadNode(prevReg, lRefReg);
                prevNode = connect(prevNode, loadNode);

                IASTExpression rval = unparenthesize(binExpr.getOperand2());
                int rValReg = handleRvalue(rval);

                int postReg = createRegister(expression);
                String opStr = "Unknown";
                switch (op) {
                    case IASTBinaryExpression.op_plusAssign: opStr = Constants.OP_ADD; break;
                    case IASTBinaryExpression.op_minusAssign: opStr = Constants.OP_SUB; break;
                    case IASTBinaryExpression.op_multiplyAssign: opStr = Constants.OP_MUL; break;
                    case IASTBinaryExpression.op_divideAssign: opStr = Constants.OP_DIV; break;
                    case IASTBinaryExpression.op_moduloAssign: opStr = Constants.OP_REM; break;
                    case IASTBinaryExpression.op_shiftLeftAssign:
                    case IASTBinaryExpression.op_shiftRightAssign:
                    case IASTBinaryExpression.op_binaryAndAssign:
                    case IASTBinaryExpression.op_binaryOrAssign:
                    case IASTBinaryExpression.op_binaryXorAssign:
                        opStr = Constants.OP_BIT; break;
                }
                Expr.Expression expr = newBinaryExpr(expression.getExpressionType(), prevReg, rValReg, opStr, expression);
                Messages.debug("CParser: compute updated value in %s@%d := %s", expr.getType(), postReg, expr);
                CFG.CFGNode evalNode = newEvalNode(postReg, expr);
                prevNode = connect(prevNode, evalNode);

                Messages.debug("CParser: update location %s <- %s@%d (%s)", lRefReg, expression.getExpressionType(), postReg, expression.getRawSignature());
                CFG.CFGNode storeNode = newStoreNode(lRefReg, postReg);
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
                CFG.CFGNode loadNode = newLoadNode(prevReg, refReg);
                prevNode = connect(prevNode, loadNode);

                int postReg = createRegister(expression);
                String opStr = "Unknown";
                switch (op) {
                    case IASTUnaryExpression.op_prefixIncr:
                    case IASTUnaryExpression.op_postFixIncr:
                        opStr = Constants.OP_INCR;
                        break;
                    case IASTUnaryExpression.op_prefixDecr:
                    case IASTUnaryExpression.op_postFixDecr:
                        opStr = Constants.OP_DECR;
                        break;
                }
                Expr.Expression expr = newUnaryExpr(expression.getExpressionType(), prevReg, opStr, expression);
                Messages.debug("CParser: compute incr/decr value in %s@%d := %s", expr.getType(), postReg, TextFormat.shortDebugString(expr));
                CFG.CFGNode evalNode = newEvalNode(postReg, expr);
                prevNode = connect(prevNode, evalNode);

                Messages.debug("CParser: incr/decr location *@%d <- %s@%d (%s)", refReg, expression.getExpressionType(), postReg, expression.getRawSignature());
                CFG.CFGNode storeNode = newStoreNode(refReg, postReg);
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
            IASTName name = ((IASTIdExpression) expression).getName();
            IBinding binding = name.resolveBinding();
            if (binding instanceof ProblemBinding p && p.getID() == ISemanticProblem.BINDING_NOT_FOUND) {
                Messages.debug("CParser: get address of included identifier %s@{%s} at line#%d (%s)", "int", name.getRawSignature(), expression.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                return processIdentifier(CBasicType.INT, name);
            }
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
                    Messages.fatal("CParser: referenced register not found for %s[%s]#%s in (%s)", binding.getClass().getSimpleName(), binding.getName(), Integer.toUnsignedString(binding.hashCode()), binding.getOwner());
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

            String constant = new String(literalExpr.getValue());
            Expr.Expression expr = newLiteralExpr(expression.getExpressionType(), constant);

            Messages.debug("CParser: assign constant %s@%d := %s", expression.getExpressionType(), reg, constant);
            simpleConstants.put(reg, constant);
            CFG.CFGNode evalNode = newEvalNode(reg, expr);
            prevNode = connect(prevNode, evalNode);

            return reg;
        } else if (expression instanceof IASTIdExpression) {
            int reg = createRegister(expression);
            IASTName name = ((IASTIdExpression) expression).getName();
            String id = new String(name.toCharArray());
            if (id.equals("NULL")) {
                Expr.Expression expr = newLiteralExpr(expression.getExpressionType(), id);
                Messages.debug("CParser: set NULL constant %s@%d := %s", expression.getExpressionType(), reg, id);
                simpleConstants.put(reg, id);
                CFG.CFGNode evalNode = newEvalNode(reg, expr);
                prevNode = connect(prevNode, evalNode);
            } else {
                IBinding binding = name.resolveBinding();
                if (binding instanceof IProblemBinding p && p.getID() == ISemanticProblem.BINDING_NOT_FOUND) {
                    int refReg = processIdentifier(CBasicType.INT, name);
                    assert refReg >= 0;
                    Messages.debug("CParser: read from included location %s@%d <- *@%d (%s)", "int", reg, refReg, expression.getRawSignature());
                    CFG.CFGNode loadNode = newLoadNode(reg, refReg);
                    prevNode = connect(prevNode, loadNode);
                    return reg;
                } else if (binding instanceof IVariable) {
                    // TODO: special handling of array-to-pointer conversion
                    int refReg = getRefReg(binding);
                    if (refReg < 0) {
                        IVariable v = (IVariable) binding;
                        refReg = processVariable(v, true);
                        Messages.debug("CParser: reference external variable %s[%s] at line#%d (%s)", binding.getClass().getSimpleName(), binding, expression.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                    }
                    Messages.debug("CParser: read from location %s@%d <- *@%d (%s)", expression.getExpressionType(), reg, refReg, expression.getRawSignature());
                    CFG.CFGNode loadNode = newLoadNode(reg, refReg);
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
                    CFG.CFGNode loadNode = newLoadNode(reg, innerReg);
                    prevNode = connect(prevNode, loadNode);

                    return reg;
                }
            } else if (op == IASTUnaryExpression.op_plus || op == IASTUnaryExpression.op_minus || op == IASTUnaryExpression.op_not) {
                int innerReg = handleRvalue(inner);
                String opStr = "Unknown";
                switch (op) {
                    case IASTUnaryExpression.op_plus: opStr = Constants.OP_ID; break;
                    case IASTUnaryExpression.op_minus: opStr = Constants.OP_NEG; break;
                    case IASTUnaryExpression.op_not: opStr = Constants.OP_NOT; break;
                }
                Expr.Expression expr = newUnaryExpr(expression.getExpressionType(), innerReg, opStr, expression);
                int reg = createRegister(expression);
                Messages.debug("CParser: compute unary expr in %s@%d := %s", expr.getType(), reg, TextFormat.shortDebugString(expr));
                CFG.CFGNode evalNode = newEvalNode(reg, expr);
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
                    Expr.Expression expr = newSizeOfExpr(expression.getExpressionType(), innerReg);
                    CFG.CFGNode evalNode = newEvalNode(reg, expr);
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
                    Expr.Expression expr = newLiteralExpr(expression.getExpressionType(), constant);
                    Messages.debug("CParser: assign constant %s@%d := %s", expression.getExpressionType(), reg, constant);
                    simpleConstants.put(reg, constant);
                    CFG.CFGNode evalNode = newEvalNode(reg, expr);
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
                CFG.CFGNode loadNode = newLoadNode(reg, refReg);
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
                        opStr = Constants.OP_ADD;
                        break;
                    case IASTBinaryExpression.op_minus:
                        opStr = Constants.OP_SUB;
                        break;
                    case IASTBinaryExpression.op_multiply:
                        opStr = Constants.OP_MUL;
                        break;
                    case IASTBinaryExpression.op_divide:
                        opStr = Constants.OP_DIV;
                        break;
                    case IASTBinaryExpression.op_modulo:
                        opStr = Constants.OP_REM;
                        break;
                    case IASTBinaryExpression.op_equals:
                        opStr = Constants.OP_EQ;
                        break;
                    case IASTBinaryExpression.op_notequals:
                        opStr = Constants.OP_NE;
                        break;
                    case IASTBinaryExpression.op_lessThan:
                        opStr = Constants.OP_LT;
                        break;
                    case IASTBinaryExpression.op_greaterThan: {
                        opStr = Constants.OP_LT;
                        int rr = r1;
                        r1 = r2;
                        r2 = rr;
                        break;
                    }
                    case IASTBinaryExpression.op_lessEqual:
                        opStr = Constants.OP_LE;
                        break;
                    case IASTBinaryExpression.op_greaterEqual: {
                        opStr = Constants.OP_LE;
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
                        opStr = Constants.OP_BIT;
                        break;
                }
                Expr.Expression expr = newBinaryExpr(expression.getExpressionType(), r1, r2, opStr, expression);
                int reg = createRegister(expression);
                Messages.debug("CParser: compute binary expr in %s@%d := %s", expr.getType(), reg, TextFormat.shortDebugString(expr));
                CFG.CFGNode evalNode = newEvalNode(reg, expr);
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
            CFG.CFGNode loadNode = newLoadNode(reg, refReg);
            prevNode = connect(prevNode, loadNode);

            return reg;
        } else if (expression instanceof IASTArraySubscriptExpression) {
            IASTArraySubscriptExpression arraySubExpr = (IASTArraySubscriptExpression) expression;
            int refReg = handleArraySubscript(arraySubExpr);
            int reg = createRegister(expression);
            Messages.debug("CParser: load from offset pointer %s@%d <- *@%d[%s]", expression.getExpressionType(), reg, refReg, expression.getExpressionType());
            CFG.CFGNode loadNode = newLoadNode(reg, refReg);
            prevNode = connect(prevNode, loadNode);

            return reg;
        } else if (expression instanceof IASTFunctionCallExpression) {
            IASTFunctionCallExpression invk = (IASTFunctionCallExpression) expression;

            IASTExpression fNameExpr = unparenthesize(invk.getFunctionNameExpression());
            if (isMallocLike(fNameExpr)) {
                // Note: special handling of malloc
                int reg = createRegister(expression);
                Integer size = getMallocSize(invk);
                CFG.CFGNode allocNode = newAllocaNode(reg, invk, CBasicType.VOID, size);
                Messages.debug("CParser: create alloc node {%s} for malloc-like expression {%s}", TextFormat.shortDebugString(allocNode), expression.getRawSignature());
                prevNode = connect(prevNode, allocNode);
                return reg;
            } else {
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

                CFG.CFGNode invkNode;
                int reg = createRegister(expression);
                if (fReg < 0) {
                    IFunction f = staticInvkMap.get(fNameExpr);
                    if (f == null) {
                        Messages.fatal("CParser: cannot resolve function name %s[%s] at line#%d (%s)", fNameExpr.getClass().getSimpleName(), fNameExpr.getRawSignature(), fNameExpr.getFileLocation().getStartingLineNumber(), expression.getRawSignature());
                        assert false;
                    }
                    invkNode = newStaticCallNode(reg, f, fArgRegs, invk);
                } else {
                    invkNode = newIndirectCallNode(reg, fReg, fArgRegs, invk);
                }
                Messages.debug("CParser: compute invocation in @%d := %s", reg, TextFormat.shortDebugString(invkNode.getInvk()));

                prevNode = connect(prevNode, invkNode);

                return reg;
            }
        } else if (expression instanceof IASTCastExpression) {
            IASTExpression innerExpr = unparenthesize(((IASTCastExpression) expression).getOperand());
            if (innerExpr instanceof IASTFunctionCallExpression
                    && isMallocLike(((IASTFunctionCallExpression) innerExpr).getFunctionNameExpression())) {
                int reg = createRegister(expression);
                // Note get the actual type of malloc-ed object?
                IType ptrType = expression.getExpressionType();
                IType baseType = ((IPointerType) ptrType).getType();
                Integer size = getMallocSize((IASTFunctionCallExpression) innerExpr);
                CFG.CFGNode allocNode = newAllocaNode(reg, expression, baseType, size);
                Messages.debug("CParser: create alloc node {%s} for malloc-like expression {%s}", TextFormat.shortDebugString(allocNode), expression.getRawSignature());
                prevNode = connect(prevNode, allocNode);
                return reg;
            } else {
                int innerReg = handleRvalue(innerExpr);
                Expr.Expression expr = newCastExpr(expression.getExpressionType(), innerReg, ((IASTCastExpression) expression).getTypeId(), expression);
                int reg = createRegister(expression);
                Messages.debug("CParser: casting expression to %s@%d := %s", expr.getType(), reg, TextFormat.shortDebugString(expr));
                CFG.CFGNode evalNode = newEvalNode(reg, expr);
                prevNode = connect(prevNode, evalNode);
                return reg;
            }
        } else if (expression instanceof IASTTypeIdExpression) {
            IASTTypeIdExpression typeIdExpression = (IASTTypeIdExpression) expression;
            if (typeIdExpression.getOperator() == IASTTypeIdExpression.op_sizeof) {
                String constant = "sizeof(" + typeIdExpression.getTypeId().getRawSignature() + ")";

                Expr.Expression expr = newLiteralExpr(expression.getExpressionType(), constant);
                int reg = createRegister(expression);
                Messages.debug("CParser: assign size-of constant %s@%d := %s", expression.getExpressionType(), reg, constant);
                simpleConstants.put(reg, constant);
                CFG.CFGNode evalNode = newEvalNode(reg, expr);
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

    private static final List<String> mallocLikeFuncs = List.of("malloc", "alloca");
    private static boolean isMallocLike(IASTExpression fNameExpr) {
        fNameExpr = unparenthesize(fNameExpr);
        if (fNameExpr instanceof IASTIdExpression) {
            String fName = new String(((IASTIdExpression) fNameExpr).getName().toCharArray());
            return mallocLikeFuncs.contains(fName);
        }
        return false;
    }
    private Integer getMallocSize(IASTFunctionCallExpression innerExpr) {
        IASTExpression sizeExpr = unparenthesize((IASTExpression) innerExpr.getArguments()[0]);
        Integer size = null;
        if (sizeExpr instanceof IASTLiteralExpression) {
            size = Integer.valueOf(String.valueOf(((IASTLiteralExpression) sizeExpr).getValue()));
        } else if (sizeExpr instanceof IASTTypeIdExpression) {
            size = 1;
        } else if (sizeExpr instanceof IASTBinaryExpression) {
            IASTBinaryExpression bSizeExpr = (IASTBinaryExpression) sizeExpr;
            if (bSizeExpr.getOperator() == IASTBinaryExpression.op_multiply) {
                IASTExpression op1 = unparenthesize(bSizeExpr.getOperand1());
                IASTExpression op2 = unparenthesize(bSizeExpr.getOperand2());
                if (op1 instanceof IASTLiteralExpression && op2 instanceof IASTTypeIdExpression) {
                    size = Integer.valueOf(String.valueOf(((IASTLiteralExpression) op1).getValue()));
                } else if (op1 instanceof IASTTypeIdExpression && op2 instanceof IASTLiteralExpression) {
                    size = Integer.valueOf(String.valueOf(((IASTLiteralExpression) op2).getValue()));
                }
            }
        }
        return size;
    }

    private int handleFieldReference(IASTExpression baseExpr, IField field, boolean isPointerDereference) {
        int basePtr;
        if (isPointerDereference) {
            basePtr = handleRvalue(baseExpr);
        } else {
            basePtr = handleLvalue(baseExpr);
        }
        Expr.Expression gepExpr = newGetFieldPtrEval(baseExpr.getExpressionType(), basePtr, field);
        int fieldPtr = createRegister(gepExpr);
        CFG.CFGNode gepNode = newEvalNode(fieldPtr, gepExpr);
        prevNode = connect(prevNode, gepNode);
        Messages.debug("CParser: compute field pointer %d = &(%d->%s) [%s]", fieldPtr, basePtr, field, TextFormat.shortDebugString(gepNode));
        return fieldPtr;
    }

    private int handleArraySubscript(IASTArraySubscriptExpression arraySubExpr) {
        IASTExpression expr1 = unparenthesize(arraySubExpr.getArrayExpression());
        int reg1 = handleRvalue(expr1);

        IASTExpression expr2 = unparenthesize((IASTExpression) arraySubExpr.getArgument());
        int reg2 = handleRvalue(expr2);

        Expr.Expression gepExpr;
        if (expr1.getExpressionType() instanceof ITypeContainer) {
            gepExpr = newGetIndexPtrEval(expr1.getExpressionType(), reg1, reg2);
        } else {
            gepExpr = newGetIndexPtrEval(expr2.getExpressionType(), reg2, reg1);
        }
        int offsetPtr = createRegister(gepExpr);
        CFG.CFGNode gepNode = newEvalNode(offsetPtr, gepExpr);
        prevNode = connect(prevNode, gepNode);
        Messages.debug("CParser: compute offset pointer %d = (%d+%d) [%s]", offsetPtr, reg1, reg2, TextFormat.shortDebugString(gepNode));
        return offsetPtr;
    }

    private int handleFunctionName(IASTExpression fNameExpr) {
        IFunction receiver = findReceiver(fNameExpr);
        if (receiver != null) {
            Messages.debug("CParser: resolve static invocation %s[%s] to %s", fNameExpr.getClass().getSimpleName(), fNameExpr.getRawSignature(), receiver);
            if (!funcs.containsValue(receiver)) {
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

        CFG.CFGNode shortCircuit = newCondNode(lReg);
        connect(prevNode, shortCircuit);

        CFG.CFGNode trueNode = newLabelNode(Constants.LABEL_THEN + "#" + expression.getFileLocation().getStartingLineNumber());
        CFG.CFGNode falseNode = newLabelNode(Constants.LABEL_ELSE + "#" + expression.getFileLocation().getStartingLineNumber());
        branch(shortCircuit, trueNode, lReg, true);
        branch(shortCircuit, falseNode, lReg, false);

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

        CFG.CFGNode phiNode = newLabelNode(Constants.LABEL_PHI);
//        shortCircuit.setMergeNode(phiNode);
        jump(trueNode, phiNode);
        jump(falseNode, phiNode);

        if (fixPhi(phiNode))
            prevNode = phiNode;
        else
            prevNode = null;

        String opStr;
        if (op == IASTBinaryExpression.op_logicalAnd)
            opStr = Constants.OP_AND;
        else
            opStr = Constants.OP_OR;
        Expr.Expression expr = newBinaryExpr(expression.getExpressionType(), lReg, rReg, opStr, expression);
        int reg = createRegister(expression);
        Messages.debug("CParser: compute result of and/or in %s@%d := %s", expr.getType(), reg, TextFormat.shortDebugString(expr));
        CFG.CFGNode evalNode = newEvalNode(reg, expr);
        prevNode = connect(prevNode, evalNode);
        return reg;
    }

    private void handleIfStatement(IASTIfStatement cIf) {
        IASTExpression condExpr = unparenthesize(cIf.getConditionExpression());
        int condReg = handleRvalue(condExpr);

        CFG.CFGNode ifNode = newCondNode(condReg);
        connect(prevNode, ifNode);

        CFG.CFGNode thenNode = newLabelNode(Constants.LABEL_THEN + "#" + cIf.getFileLocation().getStartingLineNumber());
        branch(ifNode, thenNode, condReg, true);
        prevNode = thenNode;
        expandGraph(cIf.getThenClause());
        thenNode = prevNode;

        CFG.CFGNode elseNode = newLabelNode(Constants.LABEL_ELSE + "#" + cIf.getFileLocation().getStartingLineNumber());
        branch(ifNode, elseNode, condReg, false);
        prevNode = elseNode;
        expandGraph(cIf.getElseClause());
        elseNode = prevNode;

        CFG.CFGNode phiNode = newLabelNode(Constants.LABEL_PHI);
//        ifNode.setMergeNode(phiNode);
        jump(thenNode, phiNode);
        jump(elseNode, phiNode);

        if (fixPhi(phiNode))
            prevNode = phiNode;
        else
            prevNode = null;
    }

    private void handleWhileStatement(IASTWhileStatement cWhile) {
        CFG.CFGNode continueNode = newLabelNode("continue");
        CFG.CFGNode breakNode = newLabelNode("break");

        jump(prevNode, continueNode);

        IASTExpression condExpr = cWhile.getCondition();
        IASTStatement loopBody = cWhile.getBody();

//        if (condExpr == null) {
//            IBasicBlock bodyEnd = handleLoopBody(continueNode, loopBody, continueNode, breakNode);
//            jump(bodyEnd, continueNode, true);

        condExpr = unparenthesize(condExpr);
        prevNode = continueNode;
        int condReg = handleRvalue(condExpr);
        CFG.CFGNode evalCondNode = prevNode;

        CFG.CFGNode whileNode = newCondNode(condReg);
        connect(evalCondNode, whileNode);
//        whileNode.setMergeNode(breakNode);

        CFG.CFGNode bodyStart = newLabelNode(Constants.LABEL_THEN + "#" + cWhile.getFileLocation().getStartingLineNumber());
        branch(whileNode, bodyStart, condReg, true);
        prevNode = bodyStart;
        handleLoopBody(loopBody, continueNode, breakNode);
        CFG.CFGNode bodyEnd = prevNode;
        jump(bodyEnd, continueNode);

        CFG.CFGNode loopEnd = newLabelNode(Constants.LABEL_ELSE + "#" + cWhile.getFileLocation().getStartingLineNumber());
        branch(whileNode, loopEnd, condReg, false);
        jump(loopEnd, breakNode);

        if (fixPhi(breakNode))
            prevNode = breakNode;
        else
            prevNode = null;
    }

    private void handleDoWhileStatement(IASTDoStatement cDoWhile) {
        CFG.CFGNode continueNode = newLabelNode("continue");
        CFG.CFGNode breakNode = newLabelNode("break");

        IASTExpression condExpr = cDoWhile.getCondition();
        IASTStatement loopBody = cDoWhile.getBody();

        CFG.CFGNode bodyStart = newLabelNode("do");
        jump(prevNode, bodyStart);

        prevNode = bodyStart;
        handleLoopBody(loopBody, continueNode, breakNode);
        CFG.CFGNode bodyEnd = prevNode;
        jump(bodyEnd, continueNode);

        prevNode = continueNode;
        int condReg = handleRvalue(condExpr);
        CFG.CFGNode evalCondNode = prevNode;
        CFG.CFGNode doWhileNode = newCondNode(condReg);
        connect(evalCondNode, doWhileNode);
//        doWhileNode.setMergeNode(breakNode);

        CFG.CFGNode trueNode = newLabelNode(Constants.LABEL_THEN + "#" + cDoWhile.getFileLocation().getStartingLineNumber());
        branch(doWhileNode, trueNode, condReg, true);
        jump(trueNode, bodyStart);

        CFG.CFGNode loopEnd = newLabelNode(Constants.LABEL_ELSE + "#" + cDoWhile.getFileLocation().getStartingLineNumber());
        branch(doWhileNode, loopEnd, condReg, false);
        jump(loopEnd, breakNode);

        fixPhi(continueNode);

        if (fixPhi(breakNode))
            prevNode = breakNode;
        else
            prevNode = null;
    }


    private void handleForStatement(IASTForStatement cFor) {
        CFG.CFGNode continueNode = newLabelNode("continue");
        CFG.CFGNode breakNode = newLabelNode("break");

        IASTStatement init = cFor.getInitializerStatement();
        expandGraph(init);

        CFG.CFGNode condStart = newLabelNode("for-cond");
        jump(prevNode, condStart);

        IASTExpression condExpr = cFor.getConditionExpression();
        prevNode = condStart;
        int condReg = handleRvalue(condExpr);
        CFG.CFGNode evalCondNode = prevNode;
        CFG.CFGNode forNode = newCondNode(condReg);
        connect(evalCondNode, forNode);
//        forNode.setMergeNode(breakNode);

        IASTStatement loopBody = cFor.getBody();
        CFG.CFGNode bodyStart = newLabelNode(Constants.LABEL_THEN + "#" + cFor.getFileLocation().getStartingLineNumber());
        branch(forNode, bodyStart, condReg, true);
        prevNode = bodyStart;
        handleLoopBody(loopBody, continueNode, breakNode);
        CFG.CFGNode bodyEnd = prevNode;
        jump(bodyEnd, continueNode);

        IASTExpression iter = cFor.getIterationExpression();
        prevNode = continueNode;
        handleExpression(iter);
        CFG.CFGNode afterIter = prevNode;
        jump(afterIter, condStart);

        CFG.CFGNode loopEnd = newLabelNode(Constants.LABEL_ELSE + "#" + cFor.getFileLocation().getStartingLineNumber());
        branch(forNode, loopEnd, condReg, false);
        jump(loopEnd, breakNode);

        fixPhi(continueNode);
        if (fixPhi(breakNode))
            prevNode = breakNode;
        else
            prevNode = null;
    }

    private void handleLoopBody(IASTStatement body, CFG.CFGNode continueNode, CFG.CFGNode breakNode) {
        CFG.CFGNode savedContinue = outerContinueTarget;
        boolean savedContinuePos = outerContinueBackward;
        CFG.CFGNode savedBreak = outerBreakTarget;
        outerContinueTarget = continueNode;
        outerBreakTarget = breakNode;
        expandGraph(body);
        outerContinueTarget = savedContinue;
        outerContinueBackward = savedContinuePos;
        outerBreakTarget = savedBreak;
    }

    private CFG.CFGNode connect(CFG.CFGNode prevNode, CFG.CFGNode postNode) {
        if (prevNode == null || prevNode.hasReturn() || prevNode.hasGoto()) {
            unreachable.add(postNode);
            return null;
        }
        intraCFGBuilder.putEdgeValue(prevNode, postNode, -1);
        return postNode;
    }

    private CFG.CFGNode branch(CFG.CFGNode prevNode, CFG.CFGNode postNode, int condReg, boolean positive) {
        if (prevNode == null || prevNode.hasReturn() || prevNode.hasGoto()) {
            unreachable.add(postNode);
            return null;
        }
        assert condReg >= 0;
        int cond = condReg * 2 + (positive ? 1 : 0);
        // Cond encoding: true -> condReg * 2 + 1 ; false -> condReg * 2;
        intraCFGBuilder.putEdgeValue(prevNode, postNode, cond);
        return postNode;
    }

    private CFG.CFGNode jump(CFG.CFGNode prevNode, CFG.CFGNode postNode) {
        if (prevNode == null || prevNode.hasReturn() || prevNode.hasGoto()) {
            return null;
        }
        assert postNode.hasLabel();
        CFG.CFGNode gotoNode = newGotoNode(postNode.getLabel().getLabel());
        intraCFGBuilder.putEdgeValue(prevNode, gotoNode, -1);
        intraCFGBuilder.putEdgeValue(gotoNode, postNode, -1);
        return postNode;
    }

    private boolean fixPhi(CFG.CFGNode phiNode) {
        if (!intraCFGBuilder.build().nodes().contains(phiNode))
            return false;
        if (intraCFGBuilder.build().inDegree(phiNode) == 0) {
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
        int reg = getRegister(expression, true);
        if (expression.getExpressionType().isSameType(CBasicType.VOID)) {
            Messages.debug("CParser: create null register %d for void-type expression %s[%s]", reg, expression.getClass().getSimpleName(), expression.getRawSignature());
//            Messages.warn("CParser: create register for void-type expression %s[%s]", expression.getClass().getSimpleName(), expression.getRawSignature());
        }
        return reg;
    }
    
    private int createRegister(Expr.Expression gepExpr) {
        gepIdx++;
        Pair<Expr.Expression, Integer> gep = new ImmutablePair<>(gepExpr, gepIdx);
        registers.add(gep);
        return registers.indexOf(gep);
    }

    public void dumpDot(PrintWriter pw) {
        pw.println("digraph \"" + transUnit.getContainingFilename() + "\" {");
        pw.println("compound=true;");
        for (IFunction meth : funcs.values()) {
            ValueGraph<CFG.CFGNode, Integer> intraCFG = getIntraCFG(meth);
            int reg = getRefReg(meth);
            pw.print("r" + reg + " ");
            pw.print("[label=\"#" + reg + ":refFunc-" + meth + "\"");
            pw.print(";color=navy");
            pw.println("]");

            if (intraCFG != null) {
                dumpIntraDot(meth, pw);
                pw.print("r" + reg + " -> f" + Integer.toUnsignedString(meth.hashCode()));
                pw.println(" [arrowhead=odot;style=bold;lhead=cluster_" + Integer.toUnsignedString(meth.hashCode()) + "]");
            } else {
                pw.print("f" + Integer.toUnsignedString(meth.hashCode()) + " ");
                pw.println("[label=\"extern " + meth + "\"");
                pw.print(";shape=doubleoctagon");
                pw.println("]");

                pw.print("r" + reg + " -> f" + Integer.toUnsignedString(meth.hashCode()));
                pw.println(" [arrowhead=odot;style=bold]");
            }
            pw.flush();
        }
//        pw.println("subgraph cluster_global {");
        for (Integer reg: staticRefs) {
            CFG.Alloca v = allocaMap.get(reg);
            pw.print("r" + reg + " ");
            pw.print("[label=\"#" + reg + ":refVar-" + v.getVariable() + "\"");
            pw.print(";color=navy");
            pw.println("]");
            pw.print("v" + Integer.toUnsignedString(v.hashCode()));
            pw.print("[label=\"var-" + v.getVariable() + "\"");
            pw.print(";color=brown");
            pw.println("]");
            pw.print("r" + reg + " -> v" + Integer.toUnsignedString(v.hashCode()));
            pw.println(" [arrowhead=odot;style=bold]");
        }
//        pw.println("}");
        pw.println("}");
        pw.flush();
    }

    public void dumpIntraDot(IFunction func, PrintWriter writer) {
        ValueGraph<CFG.CFGNode, Integer> intraCFG = intraCFGMap.get(func);
        writer.println("subgraph cluster_" + Integer.toUnsignedString(func.hashCode()) + " {");
        writer.println("label = \"" + func + "\"");
        writer.print("f" + Integer.toUnsignedString(func.hashCode()) + " ");
        writer.print("[label=\"" + func + "\"");
        writer.print(";shape=doubleoctagon");
        writer.println("]");
        Set<String> localRegs = new LinkedHashSet<>();
        for (CFG.CFGNode node : intraCFG.nodes()) {
            writer.print("n" + Integer.toUnsignedString(node.hashCode()) + " ");
            writer.print("[label=\"" + TextFormat.escapeDoubleQuotesAndBackslashes(TextFormat.shortDebugString(node)) + "\"");
//            if (getDeadNodes().contains(node)) {
//                writer.print(";bgcolor=gray");
//            }
            if (node.hasEntry()) {
                writer.print(";shape=Mdiamond");
            } else if (node.hasReturn()) {
                writer.print(";shape=trapezium");
            } else if (node.hasCond()) {
                writer.print(";shape=diamond");
            } else if (node.hasPhi()) {
                writer.print(";shape=pentagon");
            } else if (node.hasLabel()) {
                writer.print(";shape=house");
            } else if (node.hasGoto()) {
                writer.print(";shape=invhouse");
            } else {
                writer.print(";shape=rectangle");
            }

            if (node.hasStore()) {
                writer.print(";color=red");
            } else if (node.hasLoad()) {
                writer.print(";color=green");
            } else if (node.hasEval()) {
                writer.print(";color=blue");
            } else if (node.hasAlloca()) {
                writer.print(";color=purple");
            } else if (node.hasInvk()) {
                writer.print(";color=yellow");
            }
            writer.println("]");

            if (node.hasStore()) {
                localRegs.add(node.getStore().getAddr());
                localRegs.add(node.getStore().getReg());
            } else if (node.hasLoad()) {
                localRegs.add(node.getLoad().getReg());
                localRegs.add(node.getLoad().getAddr());
            } else if (node.hasEval()) {
                localRegs.add(node.getEval().getResultReg());
                for (String reg : getOprands(node.getEval().getExpr()))
                    localRegs.add(reg);
            } else if (node.hasAlloca()) {
                localRegs.add(node.getAlloca().getReg());
            } else if (node.hasInvk()) {
                localRegs.add(node.getInvk().getActualRet());
                localRegs.addAll(node.getInvk().getActualArgList());
            }
        }
//            writer.println("subgraph cluster_regs" + Integer.toUnsignedString(func.hashCode()) + " {");
        for (String regRepr : localRegs) {
            int reg = CDTUtil.reprToReg(regRepr);
            Object debugObj = registers.get(reg);
            if (debugObj instanceof IASTExpression) {
                writer.print("r" + reg + " ");
                writer.print("[label=\"#" + reg + ":" + ((IASTExpression) debugObj).getRawSignature().replaceAll("\"", "\\\\\"") + "\"");
                writer.print(";color=tan");
                writer.println("]");
            } else if (debugObj instanceof IVariable && !staticRefs.contains(reg)) {
                writer.print("r" + reg + " ");
                writer.print("[label=\"#" + reg + ":refVar-" + debugObj + "\"");
                writer.print(";color=navy");
                writer.println("]");
            } else if (debugObj instanceof Pair) {
                writer.print("r" + reg + " ");
                writer.print("[label=\"" + reg + ":Arg-" + debugObj + "\"");
                writer.print(";color=indigo");
                writer.println("]");
            } else if (debugObj instanceof Expr.Expression) {
                writer.print("r" + reg + " ");
                writer.print("[label=\"" + reg + ":" + TextFormat.escapeDoubleQuotesAndBackslashes(TextFormat.shortDebugString((Expr.Expression) debugObj))+ "\"");
                writer.print(";color=skyblue");
                writer.println("]");
            }
        }
//            writer.println("}");
//            writer.println("subgraph cluster_vars" + Integer.toUnsignedString(func.hashCode()) + " {");
        for (int refReg : funcVars.get(func)) {
            CFG.Alloca v = allocaMap.get(refReg);
            writer.print("v" + Integer.toUnsignedString(v.hashCode()));
//            if (v instanceof IParameter)
//                writer.print("[label=\"param-" + v + "\"");
//            else
            writer.print("[label=\"var-" + v.getVariable() + "\"");
            writer.print(";color=brown");
            writer.println("]");
        }
//            writer.println("}");
        writer.println("}");
        for (var edge : intraCFG.edges()) {
            CFG.CFGNode p = edge.source();
            CFG.CFGNode q = edge.target();


            writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> n" + Integer.toUnsignedString(q.hashCode()));
//            if (getDeadNodes().contains(p) || getDeadNodes().contains(q)) {
//                writer.print(" [color=gray");
//            } else {
            writer.print(" [color=black");
//            }
            int pId = Integer.parseInt(p.getId().substring(p.getId().indexOf("#") + 1));
            int qId = Integer.parseInt(q.getId().substring(q.getId().indexOf("#") + 1));
            if (pId >= qId) {
                writer.print(";style=dashed");
            }
            int cond = intraCFG.edgeValueOrDefault(edge, -1);
            if (cond >= 0) {
                writer.print(";label=\"");
                writer.print(cond % 2 == 1 ? "+" : "-");
                writer.print(cond / 2);
                writer.print(" \"");
            }
            writer.println("]");

            if (p.hasStore()) {
                int ptr = CDTUtil.reprToReg(p.getStore().getAddr());
                int reg = CDTUtil.reprToReg(p.getStore().getReg());
                writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + ptr);
                writer.println(" [arrowhead=diamond;color=red;style=dashed]");
                writer.print("r" + reg + " -> n" + Integer.toUnsignedString(p.hashCode()));
                writer.println(" [arrowhead=none;color=red;style=dotted]");
                writer.print("r" + reg + " -> r" + ptr);
                writer.println(" [arrowhead=open;color=red;style=dotted]");
            } else if (p.hasLoad()) {
                int ptr = CDTUtil.reprToReg(p.getLoad().getAddr());
                int reg = CDTUtil.reprToReg(p.getLoad().getReg());
                writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + ptr);
                writer.println(" [arrowhead=box;color=green;style=dashed]");
                writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + reg);
                writer.println(" [arrowhead=none;color=green;style=dotted]");
                writer.print("r" + ptr + " -> r" + reg);
                writer.println(" [arrowhead=open;color=green;style=dotted]");
            } else if (p.hasEval()) {
                Expr.Expression eval = p.getEval().getExpr();
                int reg = CDTUtil.reprToReg(p.getEval().getResultReg());
                if (reg >= 0) {
                    writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + reg);
                    writer.println(" [arrowhead=none;color=blue;style=dashed]");
                    for (String paramRepr : getOprands(eval)) {
                        int param = CDTUtil.reprToReg(paramRepr);
                        writer.print("r" + param + " -> r" + reg);
                        writer.println(" [arrowhead=open;style=dotted;color=aqua]");
                    }
                }
            } else if (p.hasInvk()) {
                if (p.getInvk().hasActualRet()) {
                    int ret = CDTUtil.reprToReg(p.getInvk().getActualRet());
                    writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + ret);
                    writer.println(" [arrowhead=none;color=blue;style=dashed]");
                    if (p.getInvk().hasFuncPtr()) {
                        writer.print("r" + p.getInvk().getFuncPtr() + " -> r" + ret);
                        writer.println(" [arrowhead=open;style=dotted;color=aqua]");
                    }
                    for (String argRepr : p.getInvk().getActualArgList()) {
                        int arg = CDTUtil.reprToReg(argRepr);
                        writer.print("r" + arg + " -> r" + ret);
                        writer.println(" [arrowhead=open;style=dotted;color=aqua]");
                    }
                }
            } else if (p.hasAlloca()) {
                //IType ty = ((AllocNode) p).getAllocType();
                CFG.Alloca obj = p.getAlloca();
                int ptr = CDTUtil.reprToReg(obj.getReg());
                writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + ptr);
                writer.println(" [arrowhead=dot;color=purple;style=dashed]");
                writer.print("r" + ptr + " -> v" + Integer.toUnsignedString(obj.hashCode()));
                writer.println(" [arrowhead=odot;color=purple;style=bold]");
            } else if (p.hasReturn()) {
                if (p.getReturn().hasFormalRet()) {
                    int reg = CDTUtil.reprToReg(p.getReturn().getFormalRet());
                    writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + reg);
                    writer.println(" [arrowhead=none;color=cyan;style=dashed]");
                }
            } else if (p.hasCond()) {
                int reg = CDTUtil.reprToReg(p.getCond().getCondReg());
                if (reg >= 0) {
                    writer.print("n" + Integer.toUnsignedString(p.hashCode()) + " -> r" + reg);
                    writer.println(" [arrowhead=none;color=violet;style=dashed]");
                }
            }
        }
        writer.println();
        writer.flush();
    }

    private static String[] getOprands(Expr.Expression expr) {
        if (expr.hasUnary()) {
            return new String[]{expr.getUnary().getOprand()};
        } else if (expr.hasBinary()) {
            return new String[]{expr.getBinary().getOprand1(), expr.getBinary().getOprand2()};
        } else if (expr.hasCast()) {
            return new String[]{expr.getCast().getInner()};
        } else if (expr.hasSizeof()) {
            return new String[]{expr.getSizeof().getRef()};
        } else {
            return new String[0];
        }
    }

    private String newNodeIdx() {
        nodeIdx++;
        return curFunc + "#" + nodeIdx;
    }


    private CFG.CFGNode newEntryNode(int[] argRegs) {
        CFG.Entry.Builder entryBuilder = CFG.Entry.newBuilder();
        for (int argReg : argRegs) {
            entryBuilder.addFormalArg(CDTUtil.regToRepr(argReg));
        }
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setEntry(entryBuilder)
                .build();
    }

    private CFG.CFGNode newReturnNode() {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setReturn(CFG.Return.newBuilder())
                .build();
    }

    private CFG.CFGNode newReturnNode(int retReg) {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setReturn(CFG.Return.newBuilder()
                        .setFormalRet(CDTUtil.regToRepr(retReg)))
                .build();
    }

    private CFG.CFGNode newStaticCallNode(int retReg, IFunction f, int[] argRegs, IASTFunctionCallExpression invkExpr) {
        CFG.Invoke.Builder invkBuilder = CFG.Invoke.newBuilder();
        if (retReg >= 0) {
            invkBuilder.setActualRet(CDTUtil.regToRepr(retReg));
        }
        for (int arg : argRegs) {
            invkBuilder.addActualArg(CDTUtil.regToRepr(arg));
        }
        // TODO function abi ?
        invkBuilder.setStaticRef(CDTUtil.methToRepr(f));

        CFG.Invoke invk = invkBuilder.build();
        invkExprMap.put(invk, invkExpr);
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setInvk(invk)
                .build();
    }

    private CFG.CFGNode newIndirectCallNode(int retReg, int funcPtr, int[] argRegs, IASTFunctionCallExpression invkExpr) {
        CFG.Invoke.Builder invkBuilder = CFG.Invoke.newBuilder();
        if (retReg >= 0) {
            invkBuilder.setActualRet(CDTUtil.regToRepr(retReg));
        }
        for (int arg : argRegs) {
            invkBuilder.addActualArg(CDTUtil.regToRepr(arg));
        }
        invkBuilder.setFuncPtr(CDTUtil.regToRepr(funcPtr));

        CFG.Invoke invk = invkBuilder.build();
        invkExprMap.put(invk, invkExpr);
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setInvk(invk)
                .build();
    }

    private CFG.CFGNode newCondNode(int condReg) {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setCond(CFG.Condition.newBuilder()
                        .setCondReg(CDTUtil.regToRepr(condReg)))
                .build();
    }


    private CFG.CFGNode newPhiNode() {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setPhi(CFG.Phi.newBuilder())
                .build();
    }

    private CFG.CFGNode newGotoNode(String label) {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setGoto(CFG.Goto.newBuilder()
                        .setLabel(label))
                .build();
    }

    private CFG.CFGNode newLabelNode(String label) {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setLabel(CFG.Label.newBuilder()
                        .setLabel(label))
                .build();
    }
    
    private CFG.CFGNode newAllocaNode(int refReg, IVariable var) {
        CFG.Alloca localAlloca = newAlloca(refReg, var);
        allocaMap.put(refReg, localAlloca);
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setAlloca(localAlloca)
                .build();
    }

    private CFG.CFGNode newAllocaNode(int refReg, IASTExpression mallocExpr, IType contentType, Integer length) {
        String id = mallocExpr.getRawSignature() + "#"+ mallocExpr.getFileLocation().getStartingLineNumber();

        CArrayType arrType;
        if (length != null) {
            arrType = new CArrayType(contentType, false, false, false, IntegralValue.create(length));
            registers.add(length);
            int reg = registers.indexOf(length);
            simpleConstants.put(reg, String.valueOf(length));
        } else {
            arrType = new CArrayType(contentType);
        }
        types.add(arrType);
        CFG.Alloca heapAlloc = newAlloca(refReg, id, arrType);
        allocaMap.put(refReg, heapAlloc);
        mallocMap.put(mallocExpr, heapAlloc);
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setAlloca(heapAlloc)
                .build();
    }

    private CFG.Alloca newAlloca(int refReg, IVariable var) {
        return CFG.Alloca.newBuilder()
                .setReg(CDTUtil.regToRepr(refReg))
                .setType(CDTUtil.typeToRepr(var.getType()))
                .setVariable(CDTUtil.varToRepr(var))
                .build();
    }


    private CFG.Alloca newAlloca(int refReg, String id, IType type) {
        return CFG.Alloca.newBuilder()
                .setReg(CDTUtil.regToRepr(refReg))
                .setType(CDTUtil.typeToRepr(type))
                .setVariable(CDTUtil.varToRepr(id))
                .build();
    }

    private CFG.CFGNode newLoadNode(int dst, int src) {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setLoad(CFG.Load.newBuilder()
                        .setReg(CDTUtil.regToRepr(dst))
                        .setAddr(CDTUtil.regToRepr(src)))
                .build();
    }

    private CFG.CFGNode newStoreNode(int dst, int src) {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setStore(CFG.Store.newBuilder()
                        .setAddr(CDTUtil.regToRepr(dst))
                        .setReg(CDTUtil.regToRepr(src)))
                .build();
    }

    private CFG.CFGNode newEvalNode(int dst, Expr.Expression expr) {
        return CFG.CFGNode.newBuilder()
                .setId(newNodeIdx())
                .setEval(CFG.Evaluation.newBuilder()
                        .setExpr(expr)
                        .setResultReg(CDTUtil.regToRepr(dst)))
                .build();
    }

    private static Expr.Expression newLiteralExpr(IType exprType, String literal) {
        return Expr.Expression.newBuilder()
                .setType(CDTUtil.typeToRepr(exprType))
                .setLiteral(Expr.LiteralExpr.newBuilder()
                        .setLiteral(literal))
                .build();
    }

    private Expr.Expression newUnaryExpr(IType exprType, int op, String opStr, IASTExpression uExpr) {
        Expr.Expression expr = Expr.Expression.newBuilder()
                .setType(CDTUtil.typeToRepr(exprType))
                .setUnary(Expr.UnaryExpr.newBuilder()
                        .setOprand(CDTUtil.regToRepr(op))
                        .setOperator(opStr))
                .build();
        exprMap.put(expr, uExpr);
        return expr;
    }

    private Expr.Expression newBinaryExpr(IType exprType, int op1, int op2, String opStr, IASTExpression bExpr) {
        Expr.Expression expr = Expr.Expression.newBuilder()
                .setType(CDTUtil.typeToRepr(exprType))
                .setBinary(Expr.BinaryExpr.newBuilder()
                        .setOprand1(CDTUtil.regToRepr(op1))
                        .setOprand2(CDTUtil.regToRepr(op2))
                        .setOperator(opStr))
                .build();
        exprMap.put(expr, bExpr);
        return expr;
    }

    private Expr.Expression newGetFieldPtrEval(IType baseType, int basePtr, IField field) {
        IType elemPtrType = new CPointerType(field.getType(), 0);
        types.add(elemPtrType);
        return Expr.Expression.newBuilder()
                // the result is a pointer to the field
                .setType(CDTUtil.typeToRepr(elemPtrType))
                .setGep(Expr.GepExpr.newBuilder()
                        .setBasePtr(CDTUtil.regToRepr(basePtr))
                        .setBaseType(CDTUtil.typeToRepr(baseType))
                        .setField(CDTUtil.fieldToRepr(field)))
                .build();
    }


    private Expr.Expression newGetIndexPtrEval(IType baseType, int basePtr, int posReg) {
        IType elemType = ((ITypeContainer) baseType).getType();
        IType elemPtrType = new CPointerType(elemType, 0);
        types.add(elemPtrType);
        return Expr.Expression.newBuilder()
                // the result is a pointer to the element
                .setType(CDTUtil.typeToRepr(elemPtrType))
                .setGep(Expr.GepExpr.newBuilder()
                        .setBasePtr(CDTUtil.regToRepr(basePtr))
                        .setBaseType(CDTUtil.typeToRepr(baseType))
                        .setIndex(CDTUtil.regToRepr(posReg)))
                .build();
    }

    private static Expr.Expression newSizeOfExpr(IType exprType, int refReg) {
        return Expr.Expression.newBuilder()
                .setType(CDTUtil.typeToRepr(exprType))
                .setSizeof(Expr.SizeOfExpr.newBuilder()
                        .setRef(CDTUtil.regToRepr(refReg)))
                .build();
    }


    private Expr.Expression newCastExpr(IType exprType, int innerReg, IASTTypeId typeId, IASTExpression castExpr) {
        Expr.Expression expr = Expr.Expression.newBuilder()
                .setType(CDTUtil.typeToRepr(exprType))
                .setCast(Expr.CastExpr.newBuilder()
                        .setInner(CDTUtil.regToRepr(innerReg)))
                .build();
        exprMap.put(expr, castExpr);
        return expr;
    }

    private final Map<CFG.Invoke, IASTFunctionCallExpression> invkExprMap = new LinkedHashMap<>();
    private final Map<Expr.Expression, IASTExpression> exprMap = new LinkedHashMap<>();
    public Object getInvkExpr(CFG.Invoke invk) {
        return invkExprMap.get(invk);
    }

    public IFunction getFunc(String fRepr) {
        return funcs.get(fRepr);
    }

    public IASTExpression getExpression(Expr.Expression expr) {
        return exprMap.get(expr);
    }
}