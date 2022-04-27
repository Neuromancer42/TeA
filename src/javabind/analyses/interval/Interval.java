package javabind.analyses.interval;

import chord.analyses.JavaAnalysis;
import chord.analyses.ProgramRel;
import chord.project.Chord;
import chord.project.ClassicProject;
import javabind.analyses.base.*;
import javabind.program.Program;
import javabind.program.binddefs.*;
import soot.*;
import soot.jimple.*;

import java.util.*;

@Chord(name="interval-java",
        produces={"M", "S", "P",
                "ITV", "OP", "IU", "ITVP",
                "MPentry", "PPdirect", "PPcond", "PPmay",
                "DefineLoc", "InputLoc", "AssignPrimLoc",
                "BinopLoc", "BinopLoc1", "BinopLoc2",
                "Compute", "MaySat"
        },
        namesOfTypes = {"M", "S", "P",
                "ITV", "OP", "IU", "ITVP"
        },
        types = {DomM.class, DomS.class, DomP.class,
                DomITV.class, DomOP.class, DomIU.class, DomITVP.class
        },
        namesOfSigns = {"MPentry", "PPdirect", "PPcond", "PPmay",
                "DefineLoc", "InputLoc", "AssignPrimLoc",
                "BinopLoc", "BinopLoc1", "BinopLoc2",
                "Compute", "MaySat"
        },
        signs = {"M0,P0:M0_P0", "P0,P1:P0xP1", "P0,P1,ITVP0,IU0:P0xP1_ITVP0_IU0", "P0,P1:P0xP1",
                "IU0,P0,ITV0:IU0_P0_ITV0", "IU0,P0,ITV0:IU0_P0_ITV0", "P0,IU0,IU1:P0_IU0xIU1",
                "P0,IU0,OP0,IU1,IU2:P0_OP0_IU0xIU1xIU2", "P0,IU0,OP0,ITV0,IU1:P0_OP0_ITV0_IU0xIU1", "P0,IU0,OP0,IU1,ITV0:P0_OP0_ITV0_IU0xIU1",
                "OP0,ITV0,ITV1,ITV2:OP0_ITV0xITV1xITV2", "ITVP0,ITV0:ITVP0_ITV0"
        }
)

// z = x + y
// Interval(z, c) :- Binop(op, x, y, z), Interval(x, a), Interval(y, b), Compute(op, a, b, c).
// Need to generate: Binop, Compute, and base intervals.
public class Interval extends JavaAnalysis {

    private DomM domM;
    private DomS domS;
    private DomP domP;
    private DomIU domIU;
    private DomITV domITV;
    private DomOP domOP;
    private DomITVP domITVP;
//    private DomV domV;
    private ProgramRel relMPentry;
    private ProgramRel relPPdirect;
    private ProgramRel relPPcond;
    private ProgramRel relPPmay;

    private ProgramRel relDefineLoc;
    private ProgramRel relInputLoc;
    private ProgramRel relAssignPrimLoc;

    private ProgramRel relBinopLoc;
    private ProgramRel relBinopLoc1;
    private ProgramRel relBinopLoc2;

    private ProgramRel relMaySat;
    private ProgramRel relCompute;

    private Program program;

    private List<AbstractValue> intervals;
    private Set<ItvPredicate> predicates;
    private Map<Local, LocalVarNode> localToVarNode;

    private void openDomains() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        domS = (DomS) ClassicProject.g().getTrgt("S");
        domIU = (DomIU) ClassicProject.g().getTrgt("IU");

        domP = (DomP) ClassicProject.g().getTrgt("P");

        domITV = (DomITV) ClassicProject.g().getTrgt("ITV");
        domOP = (DomOP) ClassicProject.g().getTrgt("OP");
        domITVP = (DomITVP) ClassicProject.g().getTrgt("ITVP");
    }
    private void saveDomains() {
        domM.save();
        domS.save();
        domIU.save();

        domP.save();

        domITV.save();
        domOP.save();
        domITVP.save();
    }
    private void collectDomains() {
        openDomains();
        // 1. TODO: get all classes (& its integer fields)
        // 2. get all methods
        Iterator<SootMethod> mIt = program.getMethods();
        domM.add(program.getMainMethod());
        while(mIt.hasNext()) {
            SootMethod m = mIt.next();
            if (program.exclude(m))
                continue;
            domM.add(m);
            domS.add(m.getSubSignature());
        }
        // traverse all reachable methods
        // 3. collect all local variables
        // 4. collect all program points
        // 5. collect all predicates
        Set<Integer> boundSet = new HashSet<>();
        Iterator<MethodOrMethodContext> reachMIt = program.scene().getReachableMethods().listener();
        while (reachMIt.hasNext()) {
            SootMethod m = (SootMethod) reachMIt.next();
            Set<VarNode> localItvVars = new HashSet<>();
            System.out.println(m);
            // this variable
            ThisVarNode thisVar;
            if (!m.isStatic()) {
                thisVar = new ThisVarNode(m);
                System.out.println(thisVar);
            }
            // parameters
            int count = m.getParameterCount();
            for (int j = 0; j < count; j++) {
                ParamVarNode node = new ParamVarNode(m, j);
                Type pType = m.getParameterType(j);
                if (pType instanceof IntegerType) {
                    domIU.add(node);
                    localItvVars.add(node);
                } else {
                    System.out.println(pType + ": "+ node);
                }
            }
            // return
            Type retType = m.getReturnType();
            if (!(retType instanceof VoidType)) {
                RetVarNode node = new RetVarNode(m);
                if (retType instanceof IntegerType) {
                    domIU.add(node);
                    localItvVars.add(node);
                } else {
                    System.out.println(retType + ": " + node);
                }
            }

            if (!m.isConcrete()) {
                continue;
            }
            // local variables
            Body body = m.retrieveActiveBody();
            LocalsClassifier lc = new LocalsClassifier(body);
            Set<Local> primLocals = lc.primLocals();
            for (Local l : body.getLocals()) {
                if (primLocals.contains(l)) {
                    LocalVarNode node = new LocalVarNode(l, m);
                    localToVarNode.put(l, node);
                    Type localType = l.getType();
                    if (l.getType() instanceof IntegerType) {
                        domIU.add(node);
                        localItvVars.add(node);
                    } else {
                        System.out.println(localType + ": " + node);
                    }
                }
            }

            // program points
            UnitPatchingChain unitChain = body.getUnits();
            for (Unit u : unitChain) {
                if (u instanceof GotoStmt || u instanceof NopStmt) {
                    continue;
                }
                domP.add(u);
                if (u instanceof IfStmt) {
                    IfStmt ifExpr = (IfStmt) u;
                    Value cond = ifExpr.getCondition();
                    if (cond instanceof ConditionExpr) {
                        ConditionExpr condExpr = (ConditionExpr) cond;
                        Value op1 = condExpr.getOp1();
                        Value op2 = condExpr.getOp2();
                        // Constant - NumericConstant - ArithmeticConstant - IntConstant
                        if (op1 instanceof IntConstant && op2 instanceof Local) {
                            int v1 = ((IntConstant) op1).value;
                            boundSet.add(v1);
                            ItvPredicate p = new ItvPredicate(v1, condExpr.getSymbol());
                            predicates.add(p);
                            predicates.add(new ItvPredicate(p, false));
                        } else if (op2 instanceof IntConstant && op1.getType() instanceof IntType) {
                            int v2 = ((IntConstant) op2).value;
                            boundSet.add(v2);
                            ItvPredicate p = new ItvPredicate(condExpr.getSymbol(), v2);
                            predicates.add(p);
                            predicates.add(new ItvPredicate(p, false));
                        } else if (op1 instanceof Local && op2 instanceof Local) {
                            //TODO: comparison between variables
                        } else {
                            System.out.println("Unhandled CondtionExpr: " + cond);
                        }
                    } else {
                        System.err.println(u + "(cond type: " + cond.getClass() + ")");
                    }
                } else if (u instanceof AssignStmt) {
                    Value rhs = ((AssignStmt) u).getRightOp();
                    if (rhs instanceof IntConstant) {
                        int v = ((IntConstant) rhs).value;
                        boundSet.add(v);
                    } else if (rhs instanceof BinopExpr) {
                        domOP.add(((BinopExpr) rhs).getSymbol());
                        Value v1 = ((BinopExpr) rhs).getOp1();
                        Value v2 = ((BinopExpr) rhs).getOp2();
                        // TODO replace with singleton oprerator
                        if (v1 instanceof IntConstant) {
                            boundSet.add(((IntConstant) v1).value);
                        }
                        if (v2 instanceof IntConstant) {
                            boundSet.add(((IntConstant) v2).value);
                        }
                    }
                } else if (!(u instanceof DefinitionStmt)) {
                    System.err.println("Unhandled " + u.getClass() + ": " + u);
                }
            }
        }

        boundSet.remove(AbstractValue.MIN);
        boundSet.remove(AbstractValue.MAX);
        List<Integer> boundList = new ArrayList<>(boundSet);
        Collections.sort(boundList);
        for (int i = 0; i <= boundList.size(); i++) {
            int l = i == 0 ? AbstractValue.MIN : (boundList.get(i-1)+1);
            int r = i == boundList.size() ? AbstractValue.MAX : (boundList.get(i)-1);
            if (l <= r) {
                AbstractValue absVal = new AbstractValue(l, r);
                intervals.add(absVal);
                domITV.add(absVal);
            }
            if (i < boundList.size()) {
                int s = boundList.get(i);
                AbstractValue singular = new AbstractValue(s, s);
                intervals.add(singular);
                domITV.add(singular);
            }
        }
        for (ItvPredicate p : predicates) {
            domITVP.add(p);
        }
        saveDomains();
    }

    private void openRelations() {
        relMPentry = (ProgramRel) ClassicProject.g().getTrgt("MPentry");
        relMPentry.zero();
        relPPdirect = (ProgramRel) ClassicProject.g().getTrgt("PPdirect");
        relPPdirect.zero();
        relPPcond = (ProgramRel) ClassicProject.g().getTrgt("PPcond");
        relPPcond.zero();
        relPPmay = (ProgramRel) ClassicProject.g().getTrgt("PPmay");
        relPPmay.zero();

        relDefineLoc = (ProgramRel) ClassicProject.g().getTrgt("DefineLoc");
        relDefineLoc.zero();
        relInputLoc = (ProgramRel) ClassicProject.g().getTrgt("InputLoc");
        relInputLoc.zero();

        relAssignPrimLoc = (ProgramRel) ClassicProject.g().getTrgt("AssignPrimLoc");
        relAssignPrimLoc.zero();

        relBinopLoc = (ProgramRel) ClassicProject.g().getTrgt("BinopLoc");
        relBinopLoc.zero();
        relBinopLoc1 = (ProgramRel) ClassicProject.g().getTrgt("BinopLoc1");
        relBinopLoc1.zero();
        relBinopLoc2 = (ProgramRel) ClassicProject.g().getTrgt("BinopLoc2");
        relBinopLoc2.zero();

        relMaySat = (ProgramRel) ClassicProject.g().getTrgt("MaySat");
        relMaySat.zero();
        relCompute = (ProgramRel) ClassicProject.g().getTrgt("Compute");
        relCompute.zero();
    }
    private void saveRelations() {
        relMPentry.save();
        relPPdirect.save();
        relPPcond.save();
        relPPmay.save();

        relDefineLoc.save();
        relInputLoc.save();

        relAssignPrimLoc.save();

        relBinopLoc.save();
        relBinopLoc1.save();
        relBinopLoc2.save();

        relMaySat.save();
        relCompute.save();
    }
    private void collectRelations() {
        openRelations();
        // traverse all reachable methods
        Iterator<MethodOrMethodContext> reachMIt = program.scene().getReachableMethods().listener();
        while (reachMIt.hasNext()) {
            SootMethod m = (SootMethod) reachMIt.next();
            Body body = m.retrieveActiveBody();
            System.out.println(body);
            // statements
            // 1. compute (intra-)CFG edges
            // 2. collect statement facts
            //  2.1 branches
            //  2.2 assignments
            UnitPatchingChain unitChain = body.getUnits();
            Unit entryUnit = null;
            /* build CFG  (with conditions) */
            for (Unit u : unitChain) {
                if (u instanceof GotoStmt || u instanceof NopStmt) {
                    continue;
                }
                if (entryUnit == null) {
                    entryUnit = u;
                    relMPentry.add(m, entryUnit);
                    if (entryUnit != unitChain.getFirst()) {
                        System.err.println("Note: strange first statement - " + unitChain.getFirst());
                    }
                }
                //System.out.println(u);

                Unit nextUnit = unitChain.getSuccOf(u);
                nextUnit = skipUnits(unitChain, nextUnit);

                if (u instanceof IfStmt) {
                    IfStmt ifExpr = (IfStmt) u;
                    Unit gotoUnit = ifExpr.getTargetBox().getUnit();
                    gotoUnit = skipUnits(unitChain, gotoUnit);
                    Value cond = ifExpr.getCondition();
                    if (cond instanceof ConditionExpr) {
                        ConditionExpr condExpr = (ConditionExpr) cond;
                        Value op1 = condExpr.getOp1();
                        Value op2 = condExpr.getOp2();
                        // Constant - NumericConstant - ArithmeticConstant - IntConstant
                        if (op1 instanceof IntConstant && op2 instanceof Local) {
                            int v1 = ((IntConstant) op1).value;
                            ItvPredicate p = new ItvPredicate(v1, condExpr.getSymbol());
                            VarNode v2 = localToVarNode.get((Local) op2);
                            relPPcond.add(u, gotoUnit, p, v2);
                            ItvPredicate np = new ItvPredicate(p, false);
                            relPPcond.add(u, nextUnit, np, v2);
                        } else if (op2 instanceof IntConstant && op1 instanceof Local) {
                            VarNode v1 = localToVarNode.get((Local) op1);
                            int v2 = ((IntConstant) op2).value;
                            ItvPredicate p = new ItvPredicate(condExpr.getSymbol(), v2);
                            relPPcond.add(u, gotoUnit, p, v1);
                            ItvPredicate np = new ItvPredicate(p, false);
                            relPPcond.add(u, nextUnit, np, v1);
                        } else if (op1 instanceof Local && op2 instanceof Local) {
                            //TODO: comparison between variables
                            relPPmay.add(u, gotoUnit);
                            relPPmay.add(u, nextUnit);
                        } else {
                            relPPmay.add(u, gotoUnit);
                            relPPmay.add(u, nextUnit);
                        }
                    } else {
                        System.err.println(u + "(Unhandled cond type: " + cond.getClass() + ")");
                    }
                } else if (u instanceof AssignStmt) {
                    relPPdirect.add(u, nextUnit);
                    Value lhs = ((DefinitionStmt) u).getLeftOp();
                    if (lhs instanceof Local && lhs.getType() instanceof IntegerType) {
                        VarNode vnode = localToVarNode.get((Local) lhs);
                        Value rhs = ((DefinitionStmt) u).getRightOp();

                        if (rhs instanceof IntConstant) {
                            int s = ((IntConstant) rhs).value;
                            AbstractValue singular = new AbstractValue(s, s);
                            relDefineLoc.add(vnode, u, singular);
                        } else if (rhs instanceof Local) {
                            VarNode unode = localToVarNode.get((Local) rhs);
                            relAssignPrimLoc.add(u, vnode, unode);
                        } else if (rhs instanceof BinopExpr) {
                            BinopExpr binopExpr = (BinopExpr) rhs;
                            Value op1 = binopExpr.getOp1();
                            Value op2 = binopExpr.getOp1();
                            String sym = binopExpr.getSymbol();
                            if (op1 instanceof Local && op2 instanceof Local) {
                                VarNode u1node = localToVarNode.get((Local) op1);
                                VarNode u2node = localToVarNode.get((Local) op2);
                                relBinopLoc.add(u, vnode, sym, u1node, u2node);
                            } else if (op1 instanceof IntConstant && op2 instanceof Local) {
                                int s = ((IntConstant) op1).value;
                                AbstractValue b1 = new AbstractValue(s,s);
                                VarNode u2node = localToVarNode.get((Local) op2);
                                relBinopLoc1.add(u, vnode, sym, b1, u2node);
                            } else if (op1 instanceof Local && op2 instanceof IntConstant) {
                                VarNode u1node = localToVarNode.get((Local) op1);
                                int s = ((IntConstant) op2).value;
                                AbstractValue b2 = new AbstractValue(s,s);
                                relBinopLoc2.add(u, vnode, sym, u1node, b2);
                            } else {
                                System.out.println("Unhandled Binop: " + binopExpr);
                            }
                        } else if (rhs instanceof InvokeExpr) {
                            // TODO: reconstruct Invoke handling for inter-procedural
                            for (AbstractValue itv: intervals) {
                                relInputLoc.add(vnode, u, itv);
                            }
                        } else {
                            // TODO: Load Statement
                            System.out.println("Unhandled RHS" + rhs.getClass() + ": " + rhs);
                        }
                    } else {
                        // TODO: Store Statement
                        System.out.println("Unhandled LHS" + lhs.getClass() + ": " + lhs);
                    }
                } else if (u instanceof IdentityStmt) {
                    relPPdirect.add(u, nextUnit);
                    Value lhs = ((IdentityStmt) u).getLeftOp();
                    Value rhs = ((IdentityStmt) u).getRightOp();
                    // TODO: IdentityStmt should be polished for inter-procedural
                    // TODO: parameterref as a variable at method entry (MPentry)
                    // TODO: for inputs, use hints to constrain input ranges
                    if (lhs instanceof Local && lhs.getType() instanceof IntegerType && rhs instanceof ParameterRef) {
                        VarNode vnode = localToVarNode.get((Local) lhs);
                        for (AbstractValue itv: intervals) {
                            relInputLoc.add(vnode, u, itv);
                        }
                    }
                }
            }
        }
        // TODO: generate arithmetics on abstract values
        for (ItvPredicate p : predicates) {
            for (AbstractValue itv : intervals) {
                if (p.maysat(itv)) {
                    relMaySat.add(p,itv);
                }
            }
        }
        for (String op : domOP) {
            for (AbstractValue b1 : intervals) {
                for (AbstractValue b2 : intervals) {
                    long l, r;
                    if (op.contains("+")) {
                        l = b1.lower + b2.lower;
                        r = b1.upper + b2.upper;
                    } else if (op.contains("-")) {
                        l = b1.lower - b2.upper;
                        r = b1.upper - b2.lower;
                    } else {
                        // TODO: add more arithmetics
                        l = AbstractValue.MIN;
                        r = AbstractValue.MAX;
                    }
                    for (AbstractValue a : intervals) {
                        if (a.mayequal(l,r))
                            relCompute.add(op,b1,b2,a);
                    }
                }
            }
        }
        saveRelations();
    }

    static private Unit skipUnits(UnitPatchingChain unitChain, Unit nextUnit) {
        while (nextUnit instanceof GotoStmt || nextUnit instanceof NopStmt) {
            if (nextUnit instanceof GotoStmt) {
                nextUnit = ((GotoStmt) nextUnit).getTarget();
            } else {
                nextUnit = unitChain.getSuccOf(nextUnit);
            }
        }
        return nextUnit;
    }

    public void run() {
        program = Program.g();
        program.runSpark();

        predicates = new HashSet<>();
        intervals = new ArrayList<>();
        localToVarNode = new HashMap<>();
        collectDomains();
        collectRelations();
    }


}
