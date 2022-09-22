package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.ExitNode;

public class ReturnNode extends ExitNode implements ICFGNode {
    private final int retReg;

    public ReturnNode(int reg) {
        this.retReg = reg;
    }

    public ReturnNode() {
        this.retReg = -1;
    }

    public int getRegister() {
        return retReg;
    }

    @Override
    public String toDebugString() {
        if (retReg < 0) {
            return "return:void";
        } else {
            return "return:@" + retReg;
        }
    }
}
