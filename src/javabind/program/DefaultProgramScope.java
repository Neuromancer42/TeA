package javabind.program;

import javabind.program.binddefs.BindUtils;
import soot.SootMethod;

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
        annotatedMethods = new HashSet<>();
        for (String methRegex : BindUtils.getAnnotatedMethodRegexes()) {
            annotatedMethods.addAll(BindUtils.regex2Methods(prog.scene(), methRegex));
        }
    }
}
