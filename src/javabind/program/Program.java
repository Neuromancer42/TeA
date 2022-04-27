package javabind.program;

import javabind.program.binddefs.BindUtils;
import soot.*;
import soot.options.Options;
import soot.shimple.ShimpleTransformer;
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
    private List<SootMethod> defaultEntryPoints = new ArrayList<>();

    private NumberedSet<SootMethod> stubMethods;
    private boolean isSSA;

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
            String classpath = System.getProperty("chord.work.dir") + File.separator + System.getProperty("chord.class.path")
                    + File.pathSeparator + System.getProperty("chord.deps.path");
            options.append(" -cp " + classpath);
            String outdir = System.getProperty("chord.proj.dir") + File.separator + "sootOutput";
            options.append(" -d " + outdir + File.separator + "jimple");

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
        stubMethods = new NumberedSet<>(Scene.v().getMethodNumberer());
        Iterator<SootMethod> mIt = getMethods();
        while (mIt.hasNext()) {
            SootMethod m = mIt.next();
            if (BindUtils.checkIfStub(m)) {
                System.out.println("Stub: "+m.getSignature());
                stubMethods.add(m);
            }
        }
    }

    public Scene scene() { return Scene.v(); }

    public void runSpark() { runSpark(""); }

    public void runSpark(String sparkOptions) {
        // TODO: make Spark a single (plug-able) pass
        Scene.v().releaseCallGraph();
        Scene.v().releasePointsToAnalysis();
        Scene.v().releaseFastHierarchy();
        G.v().MethodPAG_methodToPag.clear();
        G.v().ClassHierarchy_classHierarchyMap.clear();

        Scene.v().setEntryPoints(defaultEntryPoints);
        //run spark
        Transform sparkTransform = PackManager.v().getTransform( "cg.spark" );
        String defaultOptions = sparkTransform.getDefaultOptions();
        StringBuilder options = new StringBuilder();
        options.append("enabled:true");
        options.append(" verbose:true");
        options.append(" simulate-natives:false");//our models should take care of this
        if(sparkOptions.trim().length() > 0)
            options.append(" "+sparkOptions);
        //options.append(" dump-answer:true");
        options.append(" "+defaultOptions);
        System.out.println("spark options: "+options);
        sparkTransform.setDefaultOptions(options.toString());
        sparkTransform.apply();
    }

    public void runShimple() {
        if (!isSSA) {
            ShimpleTransformer.v().transform();
            isSSA = true;
        }
    }
}
