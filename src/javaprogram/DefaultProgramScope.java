package javaprogram;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import utils.SootUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class DefaultProgramScope  extends ProgramScope {
    private Set<SootMethod> annotatedMethods = new HashSet();
    private Pattern excludePattern;

    public DefaultProgramScope(Program prog) {
        super(prog);
        identifyMethodsWithAnnotations();
        this.excludePattern = Pattern.compile(System.getProperty("chord.scope.exclude"));
    }

    public boolean exclude(SootMethod method) {
        boolean excluded = false;
        if (prog.isStub(method))
            excluded = !annotatedMethods.contains(method);
        else if (excludePattern != null)
            excluded = excludePattern.matcher(method.getDeclaringClass().getName()).matches();
        if (excluded)
            System.out.println("Excluding " + method + " from analysis.");
        return excluded;

    }
    public boolean ignoreStub() { return true; }

    private void identifyMethodsWithAnnotations() {
        Scene scene = Scene.v();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("chord.out.dir"), "annotations.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String methSig = line.split(" ")[0];
                int atSymbolIndex = methSig.indexOf('@');
                String className = methSig.substring(atSymbolIndex+1);
                if (scene.containsClass(className)) {
                    SootClass klass = scene.getSootClass(className);
                    String subsig = SootUtils.getSootSubsigFor(methSig.substring(0, atSymbolIndex));
                    SootMethod meth = klass.getMethod(subsig);
                    annotatedMethods.add(meth);
                }
            }
            reader.close();
        } catch (IOException e) {
            throw new Error(e);
        }
    }
}
