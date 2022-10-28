package com.neuromancer42.tea.program.cdt.parser;

import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.program.cdt.memmodel.object.IMemObj;
import com.neuromancer42.tea.program.cdt.memmodel.object.StackObj;
import com.neuromancer42.tea.program.cdt.parser.cfg.EvalNode;
import com.neuromancer42.tea.program.cdt.parser.cfg.ICFGNode;
import com.neuromancer42.tea.program.cdt.parser.evaluation.IEval;
import com.neuromancer42.tea.program.cdt.parser.evaluation.IndirectCallEval;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTName;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.parser.c.CNodeFactory;

import java.util.*;
//import org.eclipse.cdt.internal.core.dom.rewrite.ASTModification;
//import org.eclipse.cdt.internal.core.dom.rewrite.ASTModificationStore;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
//import org.eclipse.cdt.internal.core.dom.rewrite.changegenerator.ChangeGenerator;
//import org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap;

class Trace {
    public static final int BEFORE_INVOKE = 1;
    public static final int ENTER_METHOD = 2;
    private final int typeId;
    private final int[] contents;
    public Trace(int[] traceLine) {
        typeId = traceLine[0];
        contents = new int[traceLine.length - 1];
        System.arraycopy(traceLine, 1, contents, 0, traceLine.length - 1);
    }

    public int getType() {
        return typeId;
    }

    public int getContent(int i) {
        if (i >= contents.length) {
            Messages.error("Trace %d: index %d out of content length %d, return -1", hashCode(), i, contents.length);
            return -1;
        } else {
            return contents[i];
        }
    }
}