package javabind.program.binddefs;

import chord.project.Config;
import soot.*;
import soot.jimple.*;
import soot.tagkit.Tag;
import utils.SootUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/*
 * @author Saswat Anand
 */

public class BindUtils {
    public static String unitToString(Unit u) {
        SootMethod m = null;
        if (u instanceof Stmt) {
            for (Tag tag : ((Stmt) u).getTags()) {
                if (tag instanceof ContainerTag)
                    m = ((ContainerTag) tag).method;
            }
        }
        return (m == null) ? u.toString() : u + "@" + m;
    }

    public static boolean isQuasiStaticInvk(Unit invkUnit)
    {
        InvokeExpr ie = ((Stmt) invkUnit).getInvokeExpr();
        return isQuasiStaticMeth(ie.getMethod());
    }

    public static boolean isQuasiStaticMeth(SootMethod m)
    {
        if(m.isStatic())
            return true;
        String klassName = m.getDeclaringClass().getName();
//        if(klassName.equals("android.app.AlarmManager"))
//            return true;
//        if(klassName.equals("android.app.Notification"))
//            return true;
        return false;
    }


    public static boolean checkIfStub(SootMethod method) {
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

    private static Set<String> annotatedMethodRegexes;
    private static Set<String> srcMethodRegexes;
    private static Set<String> snkMethodRegexes;

    public static Set<String> getAnnotatedMethodRegexes() {
        if (annotatedMethodRegexes == null) {
            readAnnotations();
        }
        return annotatedMethodRegexes;
    }

    public static Set<String> getSrcMethodRegexes() {
        if (srcMethodRegexes == null) {
            readAnnotations();
        }
        return srcMethodRegexes;
    }

    public static Set<String> getSnkMethodRegexes() {
        if (snkMethodRegexes == null) {
            readAnnotations();
        }
        return snkMethodRegexes;
    }

    private static void readAnnotations() {
        annotatedMethodRegexes = new HashSet<>();
        srcMethodRegexes = new HashSet<>();
        snkMethodRegexes = new HashSet<>();
        try {
            String annotfile = System.getProperty("chord.taint.annot", "annotations.txt");
            String fullannotpath = Config.v().workDirName + File.separator + annotfile;
            BufferedReader reader = new BufferedReader(new FileReader(fullannotpath));

            String line;
            while ((line = reader.readLine()) != null) {
                char annot = line.charAt(0);
                if (annot == '$' || annot == '!') {
                    String methName = line.substring(1);
                    annotatedMethodRegexes.add(methName);
                    if (annot == '$')
                        srcMethodRegexes.add(methName);
                    else
                        snkMethodRegexes.add(methName);
                }
            }
            reader.close();
        } catch (IOException e) {
            //throw new Error(e);
            System.out.println("No annotation file: " + e.toString());
            // Do not quit, but store empty annotations
        }
    }

    public static SootMethod sig2Method(Scene scene, String methSig) {
        int atSymbolIndex = methSig.indexOf('@');
        String className = methSig.substring(atSymbolIndex+1);
        if (scene.containsClass(className)) {
            SootClass klass = scene.getSootClass(className);
            String subsig = SootUtils.getSootSubsigFor(methSig.substring(0,atSymbolIndex));
            SootMethod meth = klass.getMethod(subsig);
            return meth;
        } else {
            return null;
        }
    }

    public static Set<SootMethod> regex2Methods(Scene scene, String methRegex) {
        Set<SootMethod> methods = new HashSet<>();
        int atSymbolIndex = methRegex.indexOf('@');
        String classRegex = methRegex.substring(atSymbolIndex+1);
        Pattern classPattern = Pattern.compile(classRegex);
        String subRegex = methRegex.substring(0, atSymbolIndex);
        Pattern sigPattern = Pattern.compile(subRegex);
        for (SootClass klass : scene.getClasses()) {
            if (classPattern.matcher(klass.getName()).matches()) {
                for (SootMethod m : klass.getMethods()) {
                    if (sigPattern.matcher(m.getSubSignature()).find())
                        methods.add(m);
                }
            }
        }
        return methods;
    }
}
