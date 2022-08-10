package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.StringUtil;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.ICASTVisitor;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;

import java.io.File;
import java.util.*;

public class CParser {

    public final ProgramDom<IASTFunctionDeclarator> domM;
    public final ProgramDom<IASTStatement> domP;
    public final ProgramDom<IASTDeclarator> domV;
//    private DomIU domIU;
//    private DomITV domITV;
//    private DomOP domOP;
//    private DomITVP domITVP;

    public final ProgramRel relMPentry;
    public final ProgramRel relMPexit;
    public final ProgramRel relPPdirect;
    public final ProgramRel relMV;
    public final ProgramRel relGlobalV;
    private final String fileName;

    public CParser(String fileName) {
        this.fileName = fileName;
        domM = ProgramDom.createDom("M", IASTFunctionDeclarator.class);
        domP = ProgramDom.createDom("P", IASTStatement.class);
        domV = ProgramDom.createDom("V", IASTDeclarator.class);
        relMPentry = ProgramRel.createRel("MPentry", new ProgramDom[]{domM, domP});
        relMPexit = ProgramRel.createRel("MPexit", new ProgramDom[]{domM, domP});
        relPPdirect = ProgramRel.createRel("PPdirect", new ProgramDom[]{domP, domP});
        relMV = ProgramRel.createRel("MV", new ProgramDom[]{domM, domV});
        relGlobalV = ProgramRel.createRel("GlobalV", new ProgramDom[]{domV});
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
    }
    private void saveDomains() {
        domM.save();
        domP.save();
        domV.init();
    }

    private void openRelations() {
        relMPentry.init();
        relMPexit.init();
        relPPdirect.init();
        relMV.init();
        relGlobalV.init();
    }
    private void saveRelations() {
        relMPentry.save();
        relMPentry.close();
        relMPexit.save();
        relMPexit.close();
        relPPdirect.save();
        relPPdirect.close();
        relMV.save();
        relMV.close();
        relGlobalV.save();
        relGlobalV.close();
    }

    private class DomainCollector extends ASTVisitor implements ICASTVisitor{
        public DomainCollector() {
            shouldVisitStatements = true;
            shouldVisitDeclarators = true;
        }

        @Override
        public int visit(IASTDeclarator declarator) {
            if (declarator instanceof IASTFunctionDeclarator) {
                var func = (IASTFunctionDeclarator) declarator;
                Messages.log("CParser: found function <%s>@%s\n%s", func, func.getFileLocation(), func.getRawSignature());
                domM.add(func);
            } else {
                var varName = declarator.getName();
                Messages.log("CParser: found declared var <%s>@%s\n%s", varName, declarator.getFileLocation(), declarator.getRawSignature());
                domV.add(declarator);
            }
            return super.visit(declarator);
        }

        @Override
        public int visit(IASTStatement statement) {
//            Messages.log("CParser: found statement <%s>@%s: \n%s", statement, statement.getFileLocation(), statement.getRawSignature());
            domP.add(statement);
            return super.visit(statement);
        }

    }

    private class RelationCollector extends ASTVisitor implements ICASTVisitor {
        private IASTFunctionDeclarator curFunc = null;
        private IASTStatement[] prevStats = null;
        private final Deque<Map<IASTName, IASTDeclarator>> varScope = new ArrayDeque<>();

        public RelationCollector() {
            shouldVisitTranslationUnit = true;
            shouldVisitStatements = true;
            shouldVisitDeclarations = true;
            shouldVisitParameterDeclarations = true;
        }

        @Override
        public int visit(IASTTranslationUnit transUnit) {
            assert varScope.isEmpty();
            Messages.log("CParser: creating global variable scope @%s:\n%s", transUnit, transUnit.getRawSignature());
            varScope.push(new HashMap<>());
            return super.visit(transUnit);
        }

        @Override
        public int leave(IASTTranslationUnit transUnit) {
            Map<IASTName, IASTDeclarator> globalVarScope = varScope.pop();
            Messages.log("CParser: declared %d global variables: %s", globalVarScope.size(), StringUtil.join(globalVarScope.keySet(), ", "));
            assert varScope.isEmpty();
            return super.leave(transUnit);
        }

        @Override
        public int visit(IASTDeclaration declaration) {
            if (declaration instanceof IASTFunctionDefinition) {
                if (curFunc != null) {
                    Messages.fatal("CParser: nested function definition unhandled @%s", declaration.getFileLocation());
                }
                IASTFunctionDefinition funcDef = (IASTFunctionDefinition) declaration;
                if (funcDef.getBody() != null) {
                    curFunc = funcDef.getDeclarator();
                    Messages.log("CParser: entering function %s scope\n%s", domM.indexOf(curFunc), declaration.getRawSignature());
                    varScope.push(new HashMap<>());
                } else {
                    Messages.log("CParser: skip declaration-only function %s", domM.indexOf(((IASTFunctionDefinition) declaration).getDeclarator()));
                    return ASTVisitor.PROCESS_SKIP;
                }
            } else if (declaration instanceof IASTSimpleDeclaration) {
                var simpDecl = (IASTSimpleDeclaration) declaration;
                for (var decl : simpDecl.getDeclarators()) {
                    var varName = decl.getName();
                    registerVariable(varName, decl);
                    if (curFunc != null) {
                        Messages.log("CParser: declare local var %s: %s at func %s", domV.indexOf(decl), decl.getRawSignature(), domM.indexOf(curFunc));
                        relMV.add(curFunc, decl);
                    } else {
                        Messages.log("CParser: global var %s:", domV.indexOf(decl), decl.getRawSignature());
                        relGlobalV.add(varName);
                    }
                }
            } else {
                Messages.warn("CParser: unhandled declaration %s", declaration);
            }
            return super.visit(declaration);
        }

        @Override
        public int visit(IASTParameterDeclaration parameterDeclaration) {
            if (curFunc != null) {
                var decl = parameterDeclaration.getDeclarator();
                if (decl == null) {
                    Messages.warn("CParser: anonymous parameter unhandled @%s", parameterDeclaration.getFileLocation());
                } else {
                    var varName = decl.getName();
                    registerVariable(varName, decl);
                    Messages.log("CParser: declare parameter %s: %s at func %s", domV.indexOf(decl), decl.getRawSignature(), domM.indexOf(curFunc));
                    relMV.add(curFunc, decl);
                }
            }
            return super.visit(parameterDeclaration);
        }
        @Override
        public int visit(IASTStatement statement) {
            if (statement instanceof IASTCompoundStatement) {
                // Compound statements are used to represent the post-state of its block
                // so the program point is added when leaving this statement
                if (!(statement.getParent() instanceof IASTFunctionDefinition)) {
                    // the outermost scope of a function body shares the same scope of the function
                    Messages.log("CParser: entering block scope\n%s", statement.getRawSignature());
                    varScope.push(new HashMap<>());
                } else {
                    assert curFunc.equals(((IASTFunctionDefinition) statement.getParent()).getDeclarator());
                    Messages.log("CParser: outermost block shares the same scope with function %s", domM.indexOf(curFunc));
                }
            } else {
                addProgramPoint(statement);
                if (statement instanceof IASTDeclarationStatement) {
                    Messages.warn("CParser: visiting declaration statement %s", statement.getRawSignature());
                }
            }
            return super.visit(statement);
        }

        @Override
        public int leave(IASTStatement statement) {
            if (statement instanceof IASTCompoundStatement) {
                var curScope = varScope.pop();
                Messages.log("CParser: invalidating %d declarations: %s", curScope.size(), StringUtil.join(curScope.keySet(), ", "));
                addProgramPoint(statement);
                prevStats = new IASTStatement[]{statement};
            } else {
                assert !(statement.getParent() instanceof IASTFunctionDefinition);
                prevStats = new IASTStatement[]{statement};
            }
            return super.leave(statement);
        }

        @Override
        public int leave(IASTDeclaration declaration) {
            if (declaration instanceof IASTFunctionDefinition) {
                assert curFunc != null;
                assert prevStats.length == 1;
                var prevStat = prevStats[0];
                Messages.log("CParser: leaving function %s, adding exit point %s", domM.indexOf(curFunc), domP.indexOf(prevStat));
                relMPexit.add(curFunc, prevStat);
                prevStats = null;
                curFunc = null;
            }
            return super.leave(declaration);
        }

        private void registerVariable(IASTName varName, IASTDeclarator decl) {
            var curScope = varScope.peek();
            assert curScope != null;
            if (curScope.containsKey(varName)) {
                Messages.warn("CParser: conflicting declaration for variable %s", varName);
            }
            curScope.put(varName, decl);
        }

        private void addProgramPoint(IASTStatement statement) {
            if (prevStats != null) {
                for (var prevStat : prevStats) {
                    Messages.log("CParser: add control flow edge (%s, %s)", domP.indexOf(prevStat), domP.indexOf(statement));
                    relPPdirect.add(prevStat, statement);
                }
            } else {
                var funcDefNode = statement.getParent();
                while (!(funcDefNode instanceof IASTFunctionDefinition)) {
                    assert funcDefNode instanceof IASTCompoundStatement;
                    funcDefNode = funcDefNode.getParent();
                }
                assert ((IASTFunctionDefinition) funcDefNode).getDeclarator().equals(curFunc);
                Messages.log("CParser: entering function %s, adding entry point %s.", domM.indexOf(curFunc), domP.indexOf(statement));
                relMPentry.add(curFunc, statement);
            }
        }
    }
}
