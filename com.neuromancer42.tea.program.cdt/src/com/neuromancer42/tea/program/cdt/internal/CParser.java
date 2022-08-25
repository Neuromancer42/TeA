package com.neuromancer42.tea.program.cdt.internal;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.internal.cfg.IntraCFGBuilder;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.ICASTVisitor;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;

import java.io.File;
import java.util.*;

public class CParser {

    public final ProgramDom<IASTFunctionDefinition> domM;
    public final ProgramDom<IASTStatement> domP;
    // TODO: change domP's domain type from IASTStatement to abstract SequencePoints
    public final ProgramDom<CSequencePoint> domS;
    public final ProgramDom<IASTDeclarator> domV;
    public final ProgramDom<IASTExpression> domE;

    public final ProgramRel relMPentry;
    public final ProgramRel relMPexit;
    public final ProgramRel relPPdirect;
    public final ProgramRel relPPtrue;
    public final ProgramRel relPPfalse;
    public final ProgramRel relMV;
    public final ProgramRel relGlobalV;
    public final ProgramRel relPret;
    public final ProgramRel relPskip;
    public final ProgramRel relPexpr;
    public final ProgramRel relPVinit;
    private final String fileName;

    public CParser(String fileName) {
        this.fileName = fileName;
        domM = ProgramDom.createDom("M", IASTFunctionDefinition.class);
        domP = ProgramDom.createDom("P", IASTStatement.class);
        domV = ProgramDom.createDom("V", IASTDeclarator.class);
        domE = ProgramDom.createDom("E", IASTExpression.class);
        domS = ProgramDom.createDom("S", CSequencePoint.class);
        // control flow relations
        relMPentry = ProgramRel.createRel("MPentry", new ProgramDom[]{domM, domP});
        relMPexit = ProgramRel.createRel("MPexit", new ProgramDom[]{domM, domP});
        relPPdirect = ProgramRel.createRel("PPdirect", new ProgramDom[]{domP, domP});
        relPPtrue = ProgramRel.createRel("PPtrue", new ProgramDom[]{domP, domP, domE});
        relPPfalse = ProgramRel.createRel("PPfalse", new ProgramDom[]{domP, domP, domE});
        // variable records
        relMV = ProgramRel.createRel("MV", new ProgramDom[]{domM, domV});
        relGlobalV = ProgramRel.createRel("GlobalV", new ProgramDom[]{domV});
        // statements
        relPret = ProgramRel.createRel("Pret", new ProgramDom[]{domP, domE});
        relPskip = ProgramRel.createRel("Pskip", new ProgramDom[]{domP});
        relPexpr = ProgramRel.createRel("Pexpr", new ProgramDom[]{domP, domE});
        relPVinit = ProgramRel.createRel("PVinit", new ProgramDom[]{domP, domV, domE});
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
        for (var decl: translationUnit.getDeclarations()) {
            if (decl instanceof IASTFunctionDefinition) {
                var fDef = (IASTFunctionDefinition) decl;
                domM.add(fDef);
                builder.build(fDef);
            }
        }
        if (builder != null) {
            return;
        }
        // TODO: move this into function template
        openDomains();
        translationUnit.accept(new DomainCollector());
        saveDomains();

        openRelations();
        translationUnit.accept(new RelationCollector());
        saveRelations();
    }

    private void openDomains() {
        domM.init();
        domP.init();
        domV.init();
        domE.init();
        domS.init();
    }
    private void saveDomains() {
        domM.save();
        domP.save();
        domV.save();
        domE.save();
        domS.save();
    }

    private void openRelations() {
        relMPentry.init();
        relMPexit.init();
        relPPdirect.init();
        relPPtrue.init();
        relPPfalse.init();
        relMV.init();
        relGlobalV.init();
        relPret.init();
        relPskip.init();
        relPexpr.init();
    }
    private void saveRelations() {
        relMPentry.save();
        relMPentry.close();
        relMPexit.save();
        relMPexit.close();
        relPPdirect.save();
        relPPdirect.close();
        relPPtrue.save();
        relPPtrue.close();
        relPPfalse.save();
        relPPfalse.close();
        relMV.save();
        relMV.close();
        relGlobalV.save();
        relGlobalV.close();
        relPret.save();
        relPret.close();
        relPskip.save();
        relPskip.close();
        relPexpr.save();
        relPexpr.close();
    }

    private class DomainCollector extends ASTVisitor implements ICASTVisitor{
        public DomainCollector() {
            shouldVisitDeclarations = true;
            shouldVisitDeclarators = true;
            shouldVisitStatements = true;
            shouldVisitExpressions = true;
        }

        @Override
        public int visit(IASTDeclaration declaration) {
            if (declaration instanceof IASTFunctionDefinition) {
                var fDef = (IASTFunctionDefinition) declaration;
                if (domM.add(fDef))
                    Messages.log("CParser: found function definition <%s>@%s\n%s", fDef.getClass().getSimpleName(), fDef.getFileLocation(), fDef.getRawSignature());
            }
            return super.visit(declaration);
        }

        @Override
        public int visit(IASTDeclarator declarator) {
            if (declarator instanceof IASTFunctionDeclarator) {
                var nested = declarator.getNestedDeclarator();
                if (nested != null) {
                    while (nested.getNestedDeclarator() != null) {
                        nested = nested.getNestedDeclarator();
                    }
                    if (domV.add(nested))
                        Messages.log("CParser: declared function pointer %s [%s] in [%s], and skip parameter decls", nested.getName(), nested.getRawSignature(), declarator.getRawSignature());
                    return ASTVisitor.PROCESS_SKIP;
                } else {
                    if (domV.add(declarator)) {
                        if (declarator.getParent() instanceof IASTFunctionDefinition) {
                            Messages.log("CParser: function declared as %s [%s] <%s>@%s", declarator.getName(), declarator.getRawSignature(), declarator.getClass().getSimpleName(), declarator.getFileLocation());
                        } else {
                            Messages.log("CParser: skip parameter declarations of declaration-only function %s [%s] <%s>@%s", declarator.getName(), declarator.getRawSignature(), declarator.getClass().getSimpleName(), declarator.getFileLocation());
                            return ASTVisitor.PROCESS_SKIP;
                        }
                    }
                }
            }
            if (domV.add(declarator)) {
                Messages.log("CParser: found declared var %s [%s] <%s>@%s", declarator.getName(), declarator.getRawSignature(), declarator.getClass().getSimpleName(), declarator.getFileLocation());
                if (declarator.getInitializer() != null) {
                    var init = declarator.getInitializer();
                    Messages.log("CParser: initialized with [%s] <%s>", init.getRawSignature(), init.getFileLocation());
                }
            }
            return super.visit(declarator);
        }

        @Override
        public int visit(IASTStatement statement) {
//            Messages.log("CParser: found statement <%s>@%s: \n%s", statement, statement.getFileLocation(), statement.getRawSignature());
            domP.add(statement);
            return super.visit(statement);
        }

        @Override
        public int visit(IASTExpression expression) {
            if (domE.add(expression))
                Messages.log("CParser: found expression %s, %s (%s) <%s>", expression.getExpressionType().toString(), expression.getValueCategory(), expression.getRawSignature(), expression.getClass().getSimpleName());
            return super.visit(expression);
        }

        @Override
        public int leave(IASTExpression expression) {
            if (expression instanceof IASTFunctionCallExpression) {
                var invkExpr = (IASTFunctionCallExpression) expression;
                var seqPoint = new CSequencePoint("PreInvk");
                domS.add(seqPoint);
            } else if (expression instanceof IASTBinaryExpression) {
                var binaryExpr = (IASTBinaryExpression) expression;
                var binop = binaryExpr.getOperator();
                if (binop == IASTBinaryExpression.op_logicalAnd) {
                    var seqPoint = new CSequencePoint("LeftAnd");
                    domS.add(seqPoint);
                } else if (binop == IASTBinaryExpression.op_logicalOr) {
                    var seqPoint = new CSequencePoint("LeftOr");
                    domS.add(seqPoint);
                }
            } else if (expression instanceof IASTExpressionList) {
                var exprList = ((IASTExpressionList) expression).getExpressions();
                for (int i = 0; i < exprList.length - 1; ++ i) {
                    var subExpr = exprList[i];
                    var seqPoint = new CSequencePoint("LeftComma");
                    domS.add(seqPoint);
                }
            } else if (expression instanceof IASTConditionalExpression) {
                var ternary = (IASTConditionalExpression) expression;
                var condExpr = ternary.getLogicalConditionExpression();
                var seqPoint = new CSequencePoint("TernaryCond");
                domS.add(seqPoint);
            }
            return super.leave(expression);
        }
    }

    private class RelationCollector extends ASTVisitor implements ICASTVisitor {
        private IASTFunctionDefinition curFunc = null;
        private IASTStatement funcEntry = null;

        private Set<IASTStatement> openDirectEdges = new HashSet<>();
        private Map<IASTStatement, IASTExpression> openTrueEdges = new HashMap<>();
        private Map<IASTStatement, IASTExpression> openFalseEdges = new HashMap<>();
        private final Map<IASTStatement, Set<IASTStatement>> cachedOpenDirectEdges = new HashMap<>();
        private final Map<IASTStatement, Map<IASTStatement, IASTExpression>> cachedOpenTrueEdges = new HashMap<>();
        private final Map<IASTStatement, Map<IASTStatement, IASTExpression>> cachedOpenFalseEdges = new HashMap<>();

        private final Deque<IASTStatement> loopStack = new ArrayDeque<>();
        private final Map<IASTStatement, Set<IASTStatement>> loopOpenBreaks = new HashMap<>();

        public RelationCollector() {
            shouldVisitTranslationUnit = true;
            shouldVisitStatements = true;
            shouldVisitDeclarations = true;
            shouldVisitDeclarators = true;
        }

        @Override
        public int visit(IASTTranslationUnit transUnit) {
            IScope scope = transUnit.getScope();
            Messages.log("CParser: creating global variable scope %s (%s)", scope.getScopeName(), scope.getKind());
            return super.visit(transUnit);
        }

        @Override
        public int leave(IASTTranslationUnit transUnit) {
            assert curFunc == null;
            return super.leave(transUnit);
        }

        @Override
        public int visit(IASTDeclaration declaration) {
            if (declaration instanceof IASTFunctionDefinition) {
                if (curFunc != null) {
                    Messages.fatal("CParser: nested function definition not supported @%s", declaration.getFileLocation());
                }
                IASTFunctionDefinition funcDef = (IASTFunctionDefinition) declaration;
                if (funcDef.getBody() != null) {
                    curFunc = funcDef;
                    assert funcEntry == null;
                    var scope = funcDef.getScope();
                    Messages.log("CParser: entering function %s scope %s (%s)", domM.indexOf(curFunc), scope.getClass().getSimpleName(), scope.getKind());
                } else {
                    Messages.fatal("CParser: function %s has no body", domM.indexOf(funcDef));
                    return ASTVisitor.PROCESS_SKIP;
                }
            } else if (declaration instanceof IASTSimpleDeclaration) {
                var simpDecl = (IASTSimpleDeclaration) declaration;
                Messages.log("CParser: simple declaration of signature [%s]", simpDecl.getDeclSpecifier().getRawSignature());
            } else {
                Messages.warn("CParser: skip unhandled declaration %s: \n%s", declaration.getClass().getSimpleName(), declaration.getRawSignature());
                return ASTVisitor.PROCESS_SKIP;
            }
            return super.visit(declaration);
        }

        @Override
        public int visit(IASTDeclarator declarator) {
            if (declarator instanceof IASTFunctionDeclarator) {
                var nested = declarator.getNestedDeclarator();
                if (nested != null) {
                    while (nested.getNestedDeclarator() != null)
                        nested = nested.getNestedDeclarator();
                    if (curFunc != null) {
                        Messages.log("CParser: declare local function pointer %s:%s [%s] at func %s, skip its parameters", domV.indexOf(nested), nested.getName(), declarator.getRawSignature(), domM.indexOf(curFunc));
                        relMV.add(curFunc, nested);
                    } else {
                        Messages.log("CParser: declare global function pointer %s:%s [%s], skip its parameters", domV.indexOf(nested), nested.getName(), declarator.getRawSignature());
                        relGlobalV.add(nested);
                    }
                    return ASTVisitor.PROCESS_SKIP;
                } else {
                    if (declarator.getParent().equals(curFunc)) {
                        Messages.log("CParser: add %s:%s [%s] to declared functions",  domV.indexOf(declarator), declarator.getName(), declarator.getRawSignature());
                        relGlobalV.add(declarator);
                        return ASTVisitor.PROCESS_CONTINUE;
                    } else {
                        Messages.log("CParser: add %s:%s [%s] to local declared functions", domV.indexOf(declarator), declarator.getName(), declarator.getRawSignature());
                        relGlobalV.add(declarator);
                        return ASTVisitor.PROCESS_SKIP;
                    }
                }
            }
            if (curFunc != null) {
                Messages.log("CParser: declare local var %s:%s [%s] at func %s", domV.indexOf(declarator), declarator.getName(), declarator.getRawSignature(), domM.indexOf(curFunc));
                relMV.add(curFunc, declarator);
            } else {
                Messages.log("CParser: global var %s:%s [%s]", domV.indexOf(declarator), declarator.getName(), declarator.getRawSignature());
                relGlobalV.add(declarator);
            }
            return super.visit(declarator);
        }

        @Override
        public int visit(IASTStatement statement) {
            if (statement instanceof IASTCompoundStatement) {
                Messages.log("CParser: entering statement block %s @%s", domP.indexOf(statement), statement.getFileLocation());
                // Compound statements are used to represent the post-state of its block
                // so the program point is added when leaving this statement
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTDeclarationStatement) {
                Messages.log("CParser: entering declaration statement %s: %s", domP.indexOf(statement), statement.getRawSignature());
                connectOpenEdges(statement);
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTExpressionStatement) {
                Messages.log("CParser: entering expression statement %s: %s", domP.indexOf(statement), statement.getRawSignature());
                connectOpenEdges(statement);
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTIfStatement) {
                Messages.log("CParser: entering if statement %s: if(%s)",domP.indexOf(statement), ((IASTIfStatement) statement).getConditionExpression().getRawSignature());
                connectOpenEdges(statement);
                // prepare open edge for its then clause
                var cIf = (IASTIfStatement) statement;
                openTrueEdges.put(cIf, cIf.getConditionExpression());
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTWhileStatement) {
                Messages.log("CParser: entering while statement %s: while(%s)", domP.indexOf(statement), ((IASTWhileStatement) statement).getCondition().getRawSignature());
                connectOpenEdges(statement);
                // prepare open edge to its loop-body
                var cWhile = (IASTWhileStatement) statement;
                openTrueEdges.put(cWhile, cWhile.getCondition());
                // loop control routine
                loopStack.push(statement);
                loopOpenBreaks.put(statement, new HashSet<>());
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTDoStatement) {
                Messages.log("CParser: entering do-while statement %s: do-while(%s)", domP.indexOf(statement), ((IASTDoStatement) statement).getCondition().getRawSignature());
                // previous open edges are left for body statement, and do-cls are added into open edges
                var cDo = (IASTDoStatement) statement;
                openTrueEdges.put(cDo, cDo.getCondition());
                // loop control routine
                loopStack.push(statement);
                loopOpenBreaks.put(statement, new HashSet<>());
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTForStatement) {
                Messages.log("CParser: entering for statement %s: for()", domP.indexOf(statement));
                var cFor = (IASTForStatement) statement;
                // leave all open edges for initializer statement if it exits, otherwise directly connect to loop
                // empty initializers are recorded as NullStatement
                assert (cFor.getInitializerStatement() != null);
                // loop control routine
                loopStack.push(statement);
                loopOpenBreaks.put(statement, new HashSet<>());
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTContinueStatement) {
                Messages.log("CParser: entering continue statement %s", domP.indexOf(statement));
                connectOpenEdges(statement);
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTBreakStatement) {
                Messages.log("CParser: entering break statement %s", domP.indexOf(statement));
                connectOpenEdges(statement);
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTReturnStatement) {
                Messages.log("CParser: entering return statement %s: %s", domP.indexOf(statement), statement.getRawSignature());
                connectOpenEdges(statement);
                return ASTVisitor.PROCESS_CONTINUE;
            } else if (statement instanceof IASTNullStatement) {
                Messages.log("CParser: entering null statement %s @%s", domP.indexOf(statement), statement.getFileLocation());
                connectOpenEdges(statement);
                return ASTVisitor.PROCESS_CONTINUE;
            } else {
                Messages.log("CParser: TODO skip %s statement %s: \n%s", statement.getClass().getSimpleName(), domP.indexOf(statement), statement.getRawSignature());
                return ASTVisitor.PROCESS_SKIP;
            }
        }

        @Override
        public int leave(IASTStatement statement) {
            if (statement instanceof IASTCompoundStatement) {
                // connect block node as the last state of current block
                connectOpenEdges(statement);
                openDirectEdges.add(statement);
            } else if (statement instanceof IASTDeclarationStatement) {
                openDirectEdges.add(statement);
            } else if (statement instanceof IASTExpressionStatement) {
                openDirectEdges.add(statement);
            } else if (statement instanceof IASTIfStatement) {
                // the open edges are the end-edges of else-clause
                // or false edge of if-clause if it has no else-clause
                var ifCls = (IASTIfStatement) statement;
                if (ifCls.getElseClause() != null)
                    mergeCachedOpenEdges(ifCls.getThenClause());
                else
                    openFalseEdges.put(ifCls, ifCls.getConditionExpression());
            } else if (statement instanceof IASTWhileStatement) {
                var whileCls = (IASTWhileStatement) statement;
                // the open edges are the end-edges of its body, which should go back to while head
                connectOpenEdges(whileCls);
                // leave the false edge to following statements
                openFalseEdges.put(whileCls, whileCls.getCondition());
                // leave the break statements to following statements
                assert statement.equals(loopStack.pop());
                var cBreaks = loopOpenBreaks.remove(statement);
                openDirectEdges.addAll(cBreaks);
            } else if (statement instanceof IASTDoStatement) {
                var doCls = (IASTDoStatement) statement;
                // the open edges are the end-edges of its body, which should go to do-while statement
                connectOpenEdges(doCls);
                // leave the false edge to following statements
                openFalseEdges.put(doCls, doCls.getCondition());
                // leave the break statements to following statements
                assert statement.equals(loopStack.pop());
                var cBreaks = loopOpenBreaks.remove(statement);
                openDirectEdges.addAll(cBreaks);
            } else if (statement instanceof IASTForStatement) {
                var forCls = (IASTForStatement) statement;
                // the open edges arethe end-edges of its body, which should go back to for statement
                var itrExpr = forCls.getIterationExpression();
                connectOpenEdges(forCls);
                // leave the false edge to following statements, or not out-of-loop edge if no condition is provided
                var cond = forCls.getConditionExpression();
                if (cond != null) {
                    openFalseEdges.put(forCls, cond);
                    assert forCls.getInitializerStatement() != null;
                    openFalseEdges.put(forCls.getInitializerStatement(), cond);
                }
                // leave the break statements to following statements
                assert statement.equals(loopStack.pop());
                var cBreaks = loopOpenBreaks.remove(statement);
                openDirectEdges.addAll(cBreaks);
            } else if (statement instanceof IASTContinueStatement) {
                var loop = loopStack.peek();
                if (loop != null) {
                    Messages.log("CParser: add direct CFG edge (%s,%s)", domP.indexOf(statement), domP.indexOf(loop));
                    relPPdirect.add(statement, loop);
                } else {
                    Messages.fatal("CParser: continue statement outside of a loop @%s", statement.getFileLocation());
                }
            } else if (statement instanceof IASTBreakStatement) {
                var loop = loopStack.peek();
                if (loop != null) {
                    loopOpenBreaks.get(loop).add(statement);
                } else {
                    Messages.fatal("CParser: break statement outside of a loop @%s", statement.getFileLocation());
                }
            } else if (statement instanceof IASTReturnStatement) {
                // no open edge is added, just connect to func Exit
                var funcExit = curFunc.getBody();
                Messages.log("CParser: add direct CFG edge (%s,%s)", domP.indexOf(statement), domP.indexOf(funcExit));
                relPPdirect.add(statement, funcExit);
            } else if (statement instanceof IASTNullStatement) {
                openDirectEdges.add(statement);
            } else {
                Messages.fatal("CParser: unhandled statement type %s", statement.getClass().getSimpleName());
            }
            var parent = statement.getParent();
            if (parent instanceof IASTIfStatement) {
                // prepare open edges for peering else-clause if it exists
                var pIf = (IASTIfStatement) parent;
                if (statement.equals(pIf.getThenClause()) && pIf.getElseClause() != null) {
                    cachingOpenEdges(statement);
                    openFalseEdges.put(pIf, pIf.getConditionExpression());
                }
            } else if (parent instanceof IASTForStatement) {
                // prepare open edges for loop-body
                var pFor = (IASTForStatement) parent;
                if (statement.equals(pFor.getInitializerStatement())) {
                    assert statement instanceof IASTExpressionStatement || statement instanceof IASTDeclarationStatement || statement instanceof IASTNullStatement;
                    assert openDirectEdges.size() == 1 && openDirectEdges.contains(statement);
                    assert openTrueEdges.isEmpty() && openFalseEdges.isEmpty();
                    var cond = pFor.getConditionExpression();
                    if (cond != null) {
                        openTrueEdges.put(pFor, cond);
                        openDirectEdges.remove(statement);
                        openTrueEdges.put(statement, cond);
                    } else {
                        openDirectEdges.add(pFor);
                    }
                }
            }
            Messages.log("CParser: leaving %s statement %s", statement.getClass().getSimpleName(), domP.indexOf(statement));
            return super.leave(statement);
        }

        private void mergeCachedOpenEdges(IASTStatement cls) {
            openDirectEdges.addAll(cachedOpenDirectEdges.remove(cls));
            openTrueEdges.putAll(cachedOpenTrueEdges.remove(cls));
            openFalseEdges.putAll(cachedOpenFalseEdges.remove(cls));
        }

        private void cachingOpenEdges(IASTStatement statement) {
            cachedOpenDirectEdges.put(statement, openDirectEdges);
            cachedOpenTrueEdges.put(statement, openTrueEdges);
            cachedOpenFalseEdges.put(statement, openFalseEdges);
            openDirectEdges = new HashSet<>();
            openTrueEdges = new HashMap<>();
            openFalseEdges = new HashMap<>();
        }

        private void connectOpenEdges(IASTStatement state) {
            // TODO: how to handle branch conditions?
            if (state instanceof IASTNullStatement
                    || state instanceof IASTCompoundStatement
                    || state instanceof IASTBreakStatement
                    || state instanceof IASTContinueStatement
            ) {
                relPskip.add(state);
            } else if (state instanceof IASTExpressionStatement) {
                IASTExpressionStatement epxrStmt = (IASTExpressionStatement) state;
                relPexpr.add(state, epxrStmt.getExpression());
            } else if (state instanceof IASTIfStatement) {
                IASTExpression condExpr = ((IASTIfStatement) state).getConditionExpression();
                relPexpr.add(state, condExpr);
            } else if (state instanceof IASTWhileStatement) {
                IASTExpression condExpr = ((IASTWhileStatement) state).getCondition();
                relPexpr.add(state, condExpr);
            } else if (state instanceof IASTDoStatement) {
                IASTExpression condExpr = ((IASTDoStatement) state).getCondition();
                relPexpr.add(state, condExpr);
            } else if (state instanceof IASTReturnStatement) {
                IASTExpression retExpr = ((IASTReturnStatement) state).getReturnValue();
                if (retExpr == null) {
                    relPskip.add(state);
                } else {
                    relPret.add(state, retExpr);
                }
            } else if (state instanceof IASTDeclarationStatement) {
                IASTDeclaration decl = ((IASTDeclarationStatement) state).getDeclaration();
                if (decl instanceof IASTSimpleDeclaration) {
                    var dtors = ((IASTSimpleDeclaration) decl).getDeclarators();
                    for (var dtor : dtors) {
                        var initializer = dtor.getInitializer();
                        if (initializer == null) {
                            // TODO: init with UnInit
                        } else if (initializer instanceof IASTEqualsInitializer) {
                            var initCls = ((IASTEqualsInitializer) initializer).getInitializerClause();
                            if (initCls instanceof IASTExpression) {
                                var initExpr = (IASTExpression) initCls;
                                // TODO: the evaluated value of initExpr is stored into declared register 'dtor'
                            }
                        }
                    }
                }
            } else if (state instanceof IASTForStatement) {
                IASTForStatement cFor = (IASTForStatement) state;
                IASTExpression iterExpr = cFor.getIterationExpression();
                if (iterExpr == null) {
                    relPskip.add(state);
                } else {
                    relPexpr.add(state, iterExpr);
                }
            } else {
                Messages.warn("CParser: unhandled statement type %s, No. %s, mark as skip: %s", state.getClass().getSimpleName(), domP.indexOf((state)), state.getRawSignature());
                relPskip.add(state);
            }
            if (funcEntry == null) {
                Messages.log("CParser: add entry node %s of function %s", domP.indexOf(state), domM.indexOf(curFunc));
                funcEntry = state;
                relMPentry.add(curFunc, state);
            }
            for (var prev : openDirectEdges) {
                Messages.log("CParser: add direct CFG edge (%s,%s)", domP.indexOf(prev), domP.indexOf(state));
                relPPdirect.add(prev, state);
            }
            for (var trueEdge : openTrueEdges.entrySet()) {
                var prev = trueEdge.getKey();
                var cond = trueEdge.getValue();
                Messages.log("CParser: add cond-true CFG edge (%d,%d)-<%s>", domP.indexOf(prev), domP.indexOf(state), cond.getRawSignature());
                relPPtrue.add(prev, state, cond);
            }
            for (var falseEdge : openFalseEdges.entrySet()) {
                var prev = falseEdge.getKey();
                var cond = falseEdge.getValue();
                Messages.log("CParser: add cond-false CFG edge (%d,%d)-!<%s>", domP.indexOf(prev), domP.indexOf(state), cond.getRawSignature());
                relPPfalse.add(prev, state, cond);
            }
            // Note: every open edge should have only one target, so they are useless after connected
            openDirectEdges.clear();
            openTrueEdges.clear();
            openFalseEdges.clear();
        }

        @Override
        public int leave(IASTDeclaration declaration) {
            if (declaration instanceof IASTFunctionDefinition) {
                assert curFunc.equals(declaration);
                assert cachedOpenDirectEdges.isEmpty() && cachedOpenTrueEdges.isEmpty() && cachedOpenFalseEdges.isEmpty();
                assert openTrueEdges.isEmpty() && openFalseEdges.isEmpty();
                var funcExit = curFunc.getBody();
                assert openDirectEdges.size() == 1 && openDirectEdges.contains(funcExit);
                assert loopStack.isEmpty() && loopOpenBreaks.isEmpty();
                openDirectEdges.remove(funcExit);
                relMPexit.add(curFunc, funcExit);
                Messages.log("CParser: leaving function %s, exit node %s", domM.indexOf(curFunc), domP.indexOf(curFunc.getBody()));
                curFunc = null;
                funcEntry = null;
            }
            return super.leave(declaration);
        }
    }
}
