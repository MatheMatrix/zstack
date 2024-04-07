package org.zstack.zql.ast.parser.visitors;

import org.zstack.header.zql.ASTNode;
import org.zstack.zql.antlr4.ZQLBaseVisitor;
import org.zstack.zql.antlr4.ZQLParser;

import java.util.stream.Collectors;

/**
 * Created by MaJin on 2019/6/3.
 */
public class QueryTargetWithFunctionVisitor extends ZQLBaseVisitor<ASTNode.QueryTargetWithFunction> {
    @Override
    public ASTNode.QueryTargetWithFunction visitWithFunction(ZQLParser.WithFunctionContext ctx) {
        ASTNode.QueryTargetWithFunction q = new ASTNode.QueryTargetWithFunction();
        q.setFunction(ctx.function().accept(new FunctionVistor()));
        q.setSubTarget(ctx.queryTargetWithFunction().accept(new QueryTargetWithFunctionVisitor()));
        return q;
    }

    @Override
    public ASTNode.QueryTargetWithFunction visitWithoutFunction(ZQLParser.WithoutFunctionContext ctx) {
        ASTNode.QueryTargetWithFunction queryTargetWithFunction
                = ASTNode.QueryTargetWithFunction.valueOf(ctx.queryTarget().accept(new QueryTargetVisitor()));
        queryTargetWithFunction.setJoinClauseList(ctx.joinClause()
                .stream()
                .map(it->it.accept(new JoinClauseVisitor()))
                .collect(Collectors.toList()));
        return queryTargetWithFunction;
    }
}
