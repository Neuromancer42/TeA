package com.neuromancer42.tea.program.cdt.internal.cfg;


import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.internal.evaluation.IEval;
import com.neuromancer42.tea.program.cdt.internal.evaluation.LoadEval;
import com.neuromancer42.tea.program.cdt.internal.memory.ILocation;
import org.eclipse.cdt.codan.core.model.cfg.*;
import org.eclipse.cdt.codan.internal.core.cfg.ControlFlowGraph;
import org.eclipse.cdt.core.dom.ast.IFunction;

import java.io.PrintWriter;
import java.util.Collection;

public class IntraCFG extends ControlFlowGraph {
    private final IFunction func;
    public IntraCFG(IFunction func, IStartNode start, Collection<IExitNode> exitNodes) {
        super(start, exitNodes);
        this.func = func;
    }

    public void dumpDotString(PrintWriter writer) {
        writer.println("subgraph cluster_" + func.hashCode() + " {");
        writer.println("label = \"" + func + "\"");
        for (IBasicBlock n: getNodes()) {
            ICFGNode node = (ICFGNode) n;
            writer.print("n" + node.hashCode() + " ");
            writer.print("[label=\"" + node.toDebugString() + "\"");
            if (getDeadNodes().contains(node)) {
                writer.print(";bgcolor=gray");
            }
            if (node instanceof IStartNode) {
                writer.print(";shape=Mdiamond");
            } else if (node instanceof IExitNode) {
                writer.print(";shape=trapezium");
            } else if (node instanceof IDecisionNode) {
                writer.print(";shape=diamond");
            } else if (node instanceof IConnectorNode) {
                writer.print(";shape=pentagon");
            } else if (node instanceof IBranchNode) {
                writer.print(";shape=house");
            } else if (node instanceof IJumpNode) {
                writer.print(";shape=invhouse");
            } else if (node instanceof IPlainNode) {
                writer.print(";shape=rectangle");
            }

            if (node instanceof StoreNode) {
                writer.print(";color=red");
            } else if (node instanceof EvalNode && ((EvalNode) node).getEvaluation() instanceof LoadEval) {
                writer.print(";color=green");
            }
            writer.println("]");

            ILocation loc = null;
            Integer reg = null;
            if (node instanceof StoreNode) {
                loc = ((StoreNode) node).getLocation();
                reg = ((StoreNode) node).getRegister();
            } else if (node instanceof EvalNode && ((EvalNode) node).getEvaluation() instanceof LoadEval) {
                loc = ((LoadEval) ((EvalNode) node).getEvaluation()).getLocation();
                reg = ((EvalNode) node).getRegister();
            } else if (node instanceof EvalNode) {
                reg = ((EvalNode) node).getRegister();
            }
            if (loc != null) {
                writer.print("m" + loc.hashCode() + " ");
                writer.print("[label=\"" + loc.toDebugString() + "\"");
                writer.println(";shape=hexagon]");
            }
            if (reg != null) {
                writer.print("r" + reg + " ");
                writer.print("[label=\"#" + reg + "\"");
                writer.print(";shape=egg]");
            }
        }
        writer.println("}");
        for (IBasicBlock p : getNodes()) {
            for (IBasicBlock q : p.getOutgoingNodes()) {
                if (q == null) {
                    if (!getDeadNodes().contains(p))
                        Messages.error("CParser: null outgoing node from n%d[%s]", p.hashCode(), ((ICFGNode) p).toDebugString());
                    continue;
                }
                writer.print("n" + p.hashCode() + " -> n" + q.hashCode());
                if (getDeadNodes().contains(p) || getDeadNodes().contains(q)) {
                    writer.print(" [color=gray");
                } else {
                    writer.print(" [color=black");
                }
                if (p instanceof GotoNode && ((GotoNode) p).isBackwardArc()) {
                    writer.print(";style=dashed");
                }
                writer.println("]");
            }
            if (p instanceof StoreNode) {
                ILocation loc = ((StoreNode) p).getLocation();
                int reg = ((StoreNode) p).getRegister();
                writer.print("n" + p.hashCode() + " -> m" + loc.hashCode());
                writer.println(" [arrowhead=diamond;color=red]");
                writer.print("r" + reg + " -> n" + p.hashCode());
                writer.println(" [arrowhead=open;color=red;style=dotted]");
                for (int param : loc.getParameters()) {
                    writer.print("r" + param + " -> m" + loc.hashCode());
                    writer.println(" [arrowhead=open;style=dotted;color=yellow]");
                }
            } else if (p instanceof EvalNode && ((EvalNode) p).getEvaluation() instanceof LoadEval) {
                ILocation loc = ((LoadEval) ((EvalNode) p).getEvaluation()).getLocation();
                int reg = ((EvalNode) p).getRegister();
                writer.print("n" + p.hashCode() + " -> m" + loc.hashCode());
                writer.println(" [arrowhead=box;color=green]");
                writer.print("n" + p.hashCode() + " -> r" + reg);
                writer.println(" [arrowhead=open;color=green;style=dotted]");
                for (int param : loc.getParameters()) {
                    writer.print("r" + param + " -> m" + loc.hashCode());
                    writer.println(" [arrowhead=open;style=dotted;color=yellow]");
                }
            } else if (p instanceof EvalNode) {
                IEval eval = ((EvalNode) p).getEvaluation();
                int reg = ((EvalNode) p).getRegister();
                writer.print("n" + p.hashCode() + " -> r" + reg);
                writer.println(" [arrowhead=none;style=bold;color=blue]");
                for (int param : eval.getOperands()) {
                    writer.print("r" + param + " -> r" + reg);
                    writer.println(" [arrowhead=open;style=dotted;color=aqua]");
                }
            }
        }
        writer.println();
        writer.flush();
    }
}
