package com.neuromancer42.tea.codemanager.cdt;

import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.configs.Messages;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.ICQualifierType;
import org.neuromancer42.tea.ir.CFG;
import org.neuromancer42.tea.ir.Expr;

public class CDTUtil {
    private CDTUtil() {}

    public static String methToRepr(IFunction meth) {
        return meth.getName();
    }

    public static String regToRepr(int i) {
        return "%" + i;
    }

    public static int reprToReg(String repr) {
        return Integer.parseInt(repr.substring(1));
    }

    public static String fieldToRepr(IField f) {
        return f.toString();
    }

    public static String typeToRepr(IType type) {
        return getRawType(type);
    }

    private static String getRawType(IType type) {
        if (type instanceof IBasicType) {
            return type.toString();
        } else if (type instanceof IArrayType) {
            IArrayType arrType = (IArrayType) type;
            return getRawType(arrType.getType()) + "[" + arrType.getSize() + "]";
        } else if (type instanceof IPointerType) {
            return getRawType(((IPointerType) type).getType()) + "*";
        } else if (type instanceof ITypedef) {
            return getRawType(((ITypedef) type).getType());
        } else if (type instanceof ICompositeType) {
            StringBuilder sb = new StringBuilder();
            ICompositeType compType = (ICompositeType) type;
            sb.append("composite {");
            for (IField f : compType.getFields())
                sb.append(getRawType(f.getType())).append(":").append(CDTUtil.fieldToRepr(f)).append(";");
            sb.append("}");
            return sb.toString();
        } else if (type instanceof ICQualifierType) {
            return getRawType(((ICQualifierType) type).getType());
        }  else if (type instanceof IFunctionType) {
            IFunctionType funcType = (IFunctionType) type;
            return "funcptr";
        } else {
            Messages.error("CParser: unhandled type %s[%s]", type.getClass().getSimpleName(), type);
            return "unknown";
        }
    }

    public static String varToRepr(IVariable var) {
        return var.getName();
    }

    public static String invkToRepr(CFG.Invoke invk) {
        return TextFormat.shortDebugString(invk);
    }

    public static String exprToRepr(Expr.Expression e) {
        return TextFormat.shortDebugString(e);
    }

    public static String cfgnodeToRepr(CFG.CFGNode node) {
        return TextFormat.shortDebugString(node);
    }

    public static String allocaToRepr(CFG.Alloca alloca) {
        return TextFormat.shortDebugString(alloca);
    }
}
