package org.zstack.zql.ast.parser.visitors;

import org.zstack.core.Platform;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.zql.ASTNode;
import org.zstack.zql.antlr4.ZQLBaseVisitor;
import org.zstack.zql.antlr4.ZQLParser;

public class FunctionCallVisitor extends ZQLBaseVisitor<ASTNode.FunctionCall> {
    @Override
    public ASTNode.FunctionCall visitSingleColumnFunctionCall(ZQLParser.SingleColumnFunctionCallContext ctx) {
        if (ctx.ID(0) == null || ctx.ID(1) == null) {
            throw new OperationFailureException(Platform.operr("Function name or column name is missing in the function call."));
        }
        String functionName = ctx.ID(0).getText();
        String singleColumn = ctx.ID(1).getText();

        ASTNode.FunctionCall fc = new ASTNode.FunctionCall();
        fc.setFunctionName(functionName);
        fc.setSingleColumn(singleColumn);
        return fc;
    }

    @Override
    public ASTNode.FunctionCall visitEntityColumnFunctionCall(ZQLParser.EntityColumnFunctionCallContext ctx) {
        if (ctx.ID() == null) {
            throw new IllegalArgumentException("Function name is missing in the function call.");
        }
        String functionName = ctx.ID().getText();
        ASTNode.QueryTarget entityColumn = ctx.queryTarget().accept(new QueryTargetVisitor());

        ASTNode.FunctionCall fc = new ASTNode.FunctionCall();
        fc.setFunctionName(functionName);
        fc.setEntityColumn(entityColumn);
        return fc;
    }
}
