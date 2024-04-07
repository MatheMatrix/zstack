package org.zstack.zql.ast.parser.visitors;

import org.zstack.header.zql.ASTNode;
import org.zstack.zql.antlr4.ZQLBaseVisitor;
import org.zstack.zql.antlr4.ZQLParser;

/**
 * Created by MaJin on 2021/9/28.
 */
public class ExprAtomVisitor extends ZQLBaseVisitor<ASTNode.ExprAtom> {
    @Override public ASTNode.ExprAtom visitColumnNameExprAtom(ZQLParser.ColumnNameExprAtomContext ctx) {
        ASTNode.ExprAtom o = new ASTNode.ExprAtom();
        o.getFields().add(ctx.ID().getText());
        o.setText(ctx.getText());
        return o;
    }

    @Override public ASTNode.ExprAtom visitMathExprAtom(ZQLParser.MathExprAtomContext ctx) {
        ASTNode.ExprAtom o = new ASTNode.ExprAtom();
        o.getFields().addAll(ctx.left.accept(new ExprAtomVisitor()).getFields());
        o.getFields().addAll(ctx.right.accept(new ExprAtomVisitor()).getFields());
        o.setText(ctx.getText());
        return o;
    }

    @Override public ASTNode.ExprAtom visitNestedExprAtom(ZQLParser.NestedExprAtomContext ctx) {
        ASTNode.ExprAtom o = new ASTNode.ExprAtom();
        ctx.exprAtom().forEach(e -> o.getFields().addAll(e.accept(new ExprAtomVisitor()).getFields()));
        o.setText(ctx.getText());
        return o;
    }

    @Override
    public ASTNode.ExprAtom visitRelationshipEntityExprAtom(ZQLParser.RelationshipEntityExprAtomContext ctx) {
        ASTNode.ExprAtom o = new ASTNode.ExprAtom();
        o.setQueryTarget(ctx.queryTarget().accept(new QueryTargetVisitor()));
        return o;
    }

    @Override
    public ASTNode.ExprAtom visitFunctionCallExpressionAtom(ZQLParser.FunctionCallExpressionAtomContext ctx) {
        ASTNode.ExprAtom o = new ASTNode.ExprAtom();
        o.setFunctionCall(ctx.functionCall().accept(new FunctionCallVisitor()));
        return o;
    }
}
