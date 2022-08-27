package com.neuromancer42.tea.program.cdt.internal.cfg;


import com.neuromancer42.tea.core.project.Messages;
import org.eclipse.cdt.codan.core.model.cfg.*;
import org.eclipse.cdt.codan.internal.core.cfg.ControlFlowGraph;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IFunction;

import java.io.PrintWriter;
import java.util.Collection;

public class IntraCFG extends ControlFlowGraph {
    private final IASTFunctionDefinition func;
    public IntraCFG(IASTFunctionDefinition func, IStartNode start, Collection<IExitNode> exitNodes) {
        super(start, exitNodes);
        this.func = func;
    }

    public void dumpDotString(PrintWriter writer) {
        IFunction f = (IFunction) func.getDeclarator().getName().resolveBinding();
        writer.println("subgraph cluster_" + func.hashCode() + " {");
        writer.println("label = \"" + f + "\"");
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
            } else if (node instanceof LoadNode) {
                writer.print(";color=green");
            }
            writer.println("]");
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
        }
        writer.println();
        writer.flush();
    }
}
