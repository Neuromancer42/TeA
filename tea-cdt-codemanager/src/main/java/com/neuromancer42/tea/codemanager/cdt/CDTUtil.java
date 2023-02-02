package com.neuromancer42.tea.codemanager.cdt;

import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.configs.Messages;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.ICQualifierType;
import org.neuromancer42.tea.ir.CFG;
import org.neuromancer42.tea.ir.Expr;

import java.util.LinkedHashMap;
import java.util.Map;

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

    // TODO handle recursive maps?
    private static Map<IType, String> typeNameMap = new LinkedHashMap<>();

    private static String getRawType(IType type) {
        if (typeNameMap.containsKey(type)) {
            return typeNameMap.get(type);
        }
        if (type instanceof IBasicType) {
            String typeStr = type.toString();
            typeNameMap.put(type, typeStr);
            return typeStr;
        } else if (type instanceof IArrayType) {
            IArrayType arrType = (IArrayType) type;
            String typeStr = getRawType(arrType.getType()) + "[" + arrType.getSize() + "]";
            typeNameMap.put(type, typeStr);
            return typeStr;
        } else if (type instanceof IPointerType) {
            String typeStr = getRawType(((IPointerType) type).getType()) + "*";
            typeNameMap.put(type, typeStr);
            return typeStr;
        } else if (type instanceof ITypedef) {
            ITypedef typedef = (ITypedef) type;
            String typeStr = getRawType(typedef.getType());
            typeNameMap.put(type, typeStr);
            return typeStr;
        } else if (type instanceof ICompositeType) {
            ICompositeType compType = (ICompositeType) type;
            if (compType.getName() == null || compType.getName().isBlank()) {
                StringBuilder sb = new StringBuilder();
                sb.append("composite{");
                for (IField f : compType.getFields())
                    sb.append(getRawType(f.getType())).append(":").append(CDTUtil.fieldToRepr(f)).append(";");
                sb.append("}");
                String typeStr = sb.toString();
                typeNameMap.put(type, typeStr);
                return typeStr;
            } else {
                String typeStr = "composite_" + compType.getName();
                typeNameMap.put(type, typeStr);
                return typeStr;
            }
        } else if (type instanceof ICQualifierType) {
            String typeStr = getRawType(((ICQualifierType) type).getType());
            typeNameMap.put(type, typeStr);
            return typeStr;
        }  else if (type instanceof IFunctionType) {
            IFunctionType funcType = (IFunctionType) type;
            String typeStr = "func";
            typeNameMap.put(type, typeStr);
            return typeStr;
        } else {
            Messages.error("CParser: unhandled type %s[%s]", type.getClass().getSimpleName(), type);
            return "unknown";
        }
    }

    public static String varToRepr(IVariable var) {
        return var.getName();
    }

    public static String varToRepr(String id) {
        return id;
    }

    public static String invkToRepr(CFG.Invoke invk) {
        return TextFormat.shortDebugString(invk);
    }

    public static CFG.Invoke reprToInvk(String invkRepr) {
        try {
            return TextFormat.parse(invkRepr, CFG.Invoke.class);
        } catch (TextFormat.ParseException e) {
            Messages.error("CParser: cannot parse CFG.Invoke from string {%s}: %s", invkRepr, e.getMessage());
            return null;
        }
    }
    public static String exprToRepr(Expr.Expression e) {
        return TextFormat.shortDebugString(e);
    }

    public static String cfgnodeToRepr(CFG.CFGNode node) {
        return TextFormat.shortDebugString(node);
    }

    public static CFG.CFGNode reprToCfgNode(String pRepr) {
        try {
            return TextFormat.parse(pRepr, CFG.CFGNode.class);
        } catch (TextFormat.ParseException e) {
            Messages.error("CParser: cannot parse CFG.CFGNode from string {%s}: %s", pRepr, e.getMessage());
            return null;
        }
    }

}
