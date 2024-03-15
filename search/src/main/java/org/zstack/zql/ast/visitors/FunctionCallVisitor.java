package org.zstack.zql.ast.visitors;

import org.apache.commons.lang.StringUtils;
import org.zstack.header.zql.ASTNode;
import org.zstack.header.zql.ASTVisitor;

public class FunctionCallVisitor implements ASTVisitor<String, ASTNode.FunctionCall> {
    @Override
    public String visit(ASTNode.FunctionCall node) {

        String functionName = node.getFunctionName();
        String singleColumn = node.getSingleColumn();
        String entityColumn = (String) node.getEntityColumn().accept(new QueryTargetVisitor());

        return String.format(" %s(%s) ",
                functionName, StringUtils.isNotBlank(singleColumn) ? singleColumn : entityColumn);
    }
}
