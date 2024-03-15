package org.zstack.zql.ast.parser.visitors;

import org.zstack.header.zql.ASTNode;
import org.zstack.zql.antlr4.ZQLBaseVisitor;
import org.zstack.zql.antlr4.ZQLParser;

public class JoinExprVisitor extends ZQLBaseVisitor<ASTNode.JoinExpr> {
    @Override
    public ASTNode.JoinExpr visitJoinExpr(ZQLParser.JoinExprContext ctx) {
        ASTNode.JoinExpr e = new ASTNode.JoinExpr();
        e.setLeftTable(ctx.leftExpr().entity().getText());
        e.setLeftField(ctx.leftExpr().ID().getText());
        e.setOperator(ctx.operator().getText());
        e.setRightTable(ctx.rightExpr().entity().getText());
        e.setRightField(ctx.rightExpr().ID().getText());
        return e;
    }
}
