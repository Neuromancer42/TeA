package javabind.program.binddefs;

import soot.*;
import soot.jimple.*;
import soot.tagkit.Tag;

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
        if(klassName.equals("android.app.AlarmManager"))
            return true;
        if(klassName.equals("android.app.Notification"))
            return true;
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
}
