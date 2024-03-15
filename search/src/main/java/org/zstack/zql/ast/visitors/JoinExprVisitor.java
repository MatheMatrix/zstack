package org.zstack.zql.ast.visitors;

import org.zstack.header.zql.ASTNode;
import org.zstack.header.zql.ASTVisitor;
import org.zstack.zql.ast.ZQLMetadata;

public class JoinExprVisitor implements ASTVisitor<String, ASTNode.JoinExpr> {
    @Override
    public String visit(ASTNode.JoinExpr node) {
        String leftTable = node.getLeftTable();
        String leftField = node.getLeftField();
        String operator = node.getOperator();
        String rightTable = node.getRightTable();
        String rightField = node.getRightField();

        String leftInventoryTarget = ZQLMetadata.findInventoryMetadata(leftTable).simpleInventoryName();
        String rightInventoryTarget = ZQLMetadata.findInventoryMetadata(rightTable).simpleInventoryName();

        return String.format("%s.%s %s %s.%s",
                leftInventoryTarget,
                leftField,
                operator,
                rightInventoryTarget,
                rightField);
    }
}
