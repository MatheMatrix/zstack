package org.zstack.zql.ast.parser.visitors;

import org.zstack.header.zql.ASTNode;
import org.zstack.zql.antlr4.ZQLBaseVisitor;
import org.zstack.zql.antlr4.ZQLParser;

import java.util.stream.Collectors;

public class JoinClauseVisitor extends ZQLBaseVisitor<ASTNode.JoinClause> {

    @Override
    public ASTNode.JoinClause visitInnerJoin(ZQLParser.InnerJoinContext ctx) {
        ASTNode.JoinClause innerJoin = new ASTNode.JoinClause();
        innerJoin.setJoinType(ctx.INNER().getText());
        innerJoin.setJoin(ctx.JOIN().getText());
        innerJoin.setQueryTarget(ctx.queryTarget().accept(new QueryTargetVisitor()));
        innerJoin.setOn(ctx.ON().getText());
        innerJoin.setConditions(ctx.condition().stream()
                .map(it -> it.accept(new ConditionVisitor()))
                .collect(Collectors.toList()));

        return innerJoin;
    }

    @Override
    public ASTNode.JoinClause visitOuterJoin(ZQLParser.OuterJoinContext ctx) {
        ASTNode.JoinClause outerJoin = new ASTNode.JoinClause();
        outerJoin.setJoinType(ctx.LEFT() != null
                ? ctx.LEFT().getText()
                : ctx.RIGHT().getText());
        outerJoin.setJoin(ctx.JOIN().getText());
        outerJoin.setQueryTarget(ctx.queryTarget().accept(new QueryTargetVisitor()));
        outerJoin.setOn(ctx.ON().getText());
        outerJoin.setConditions(ctx.condition().stream()
                .map(it -> it.accept(new ConditionVisitor()))
                .collect(Collectors.toList()));
        return outerJoin;
    }
}
