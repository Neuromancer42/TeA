package javaprogram;

import soot.*;
import soot.jimple.*;
import soot.options.Options;
import soot.util.ArrayNumberer;
import soot.util.Chain;
import soot.util.NumberedSet;

import java.io.File;
import java.util.*;

public class Program {
    private static Program g;
    private SootMethod mainMethod;
    private ProgramScope scope;
    private Set<String> harnessClasses;
    private List<SootMethod> defaultEntryPoints = new ArrayList();

    private NumberedSet stubMethods;

    public static Program g() {
        if (g == null) {
            g = new Program();
        }
        return g;
    }

    private Program() {
    }

    public void build(Set<String> harnesses) {
        try {
            StringBuilder options = new StringBuilder();
            options.append("-full-resolver");
            options.append(" -f jimple");
            options.append(" -cp " + System.getProperty("chord.work.dir") + File.pathSeparator + System.getProperty("chord.class.path"));
            options.append(" -d " + System.getProperty("chord.out.dir") + File.separator + "jimple");

            if (!Options.v().parse(options.toString().split(" ")))
                throw new CompilationDeathException(
                        CompilationDeathException.COMPILATION_ABORTED,
                        "Option parse error"
                );

            Scene.v().loadBasicClasses();
            this.harnessClasses = harnesses;
            for (String h : harnesses) {
                Scene.v().loadClassAndSupport(h);
            }

            Scene.v().loadDynamicClasses();

        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public void setMainClass(String harness) {
        SootClass mainClass = Scene.v().getSootClass(harness);
        mainMethod = mainClass.getMethod(Scene.v().getSubSigNumberer().findOrAdd("void main(java.lang.String[])"));
        Scene.v().setMainClass(mainClass);

        defaultEntryPoints.add(mainMethod);

        //workaround soot bug
        if (mainClass.declaresMethodByName("<clinit>"))
            defaultEntryPoints.add(mainClass.getMethodByName("<clinit>"));
    }

    public SootMethod getMainMethod() {
        return mainMethod;
    }

    public Chain<SootClass> getClasses() {
        return Scene.v().getClasses();
    }

    public Iterator<SootMethod> getMethods() {
        return Scene.v().getMethodNumberer().iterator();
    }

    public ArrayNumberer<Type> getTypes() {
        return (ArrayNumberer<Type>) Scene.v().getTypeNumberer();
    }

    public boolean exclude(SootMethod m) {
        if (scope != null)
            return scope.exclude(m);
        else
            return false;
    }

    public void setScope(ProgramScope ps) {
        this.scope = ps;
    }

    public boolean isStub(SootMethod m) {
        if (stubMethods == null)
            identifyStubMethods();
        return stubMethods.contains(m);
    }

    public boolean ignoreStub() {
        return scope != null ? scope.ignoreStub()  : false;
    }

    private void identifyStubMethods() {
        stubMethods = new NumberedSet(Scene.v().getMethodNumberer());
        Iterator<SootMethod> mIt = getMethods();
        while (mIt.hasNext()) {
            SootMethod m = mIt.next();
            if (checkIfStub(m)) {
                System.out.println("Stub: "+m.getSignature());
                stubMethods.add(m);
            }
        }
    }

    private boolean checkIfStub(SootMethod method) {
        if(!method.isConcrete())
            return false;
        PatchingChain<Unit> units = method.retrieveActiveBody().getUnits();
        Unit unit = units.getFirst();
        while(unit instanceof IdentityStmt)
            unit = units.getSuccOf(unit);

        //if method is <init>, then next stmt could be a call to super.<init>
        if(method.getName().equals("<init>")){
            if(unit instanceof InvokeStmt){
                if(((InvokeStmt) unit).getInvokeExpr().getMethod().getName().equals("<init>"))
                    unit = units.getSuccOf(unit);
            }
        }

        if(!(unit instanceof AssignStmt))
            return false;
        Value rightOp = ((AssignStmt) unit).getRightOp();
        if(!(rightOp instanceof NewExpr))
            return false;
        //System.out.println(method.retrieveActiveBody().toString());
        if(!((NewExpr) rightOp).getType().toString().equals("java.lang.RuntimeException"))
            return false;
        Local e = (Local) ((AssignStmt) unit).getLeftOp();

        //may be there is an assignment (if soot did not optimized it away)
        Local f = null;
        unit = units.getSuccOf(unit);
        if(unit instanceof AssignStmt){
            f = (Local) ((AssignStmt) unit).getLeftOp();
            if(!((AssignStmt) unit).getRightOp().equals(e))
                return false;
            unit = units.getSuccOf(unit);
        }
        //it should be the call to the constructor
        Stmt s = (Stmt) unit;
        if(!s.containsInvokeExpr())
            return false;
        if(!s.getInvokeExpr().getMethod().getSignature().equals("<java.lang.RuntimeException: void <init>(java.lang.String)>"))
            return false;
        unit = units.getSuccOf(unit);
        if(!(unit instanceof ThrowStmt))
            return false;
        Immediate i = (Immediate) ((ThrowStmt) unit).getOp();
        return i.equals(e) || i.equals(f);
    }

    public Scene scene() { return Scene.v(); }
}
