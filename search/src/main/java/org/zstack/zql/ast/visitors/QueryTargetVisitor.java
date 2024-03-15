package org.zstack.zql.ast.visitors;

import org.apache.commons.lang.StringUtils;
import org.zstack.header.zql.ASTNode;
import org.zstack.header.zql.ASTVisitor;
import org.zstack.zql.ast.ZQLMetadata;

import java.util.List;

public class QueryTargetVisitor implements ASTVisitor<String, ASTNode.QueryTarget> {
    @Override
    public String visit(ASTNode.QueryTarget node) {
        return adapterQueryTarget(node.getEntity(), node.getFields());
    }

    private String adapterQueryTarget(String entity, List<String> fields) {
        if (StringUtils.isBlank(entity)) return "";
        String alias = ZQLMetadata.findInventoryMetadata(entity).simpleInventoryName();

        if (fields == null || fields.isEmpty()) {
            String tableName = ZQLMetadata.findInventoryMetadata(entity).inventoryAnnotation.mappingVOClass().getSimpleName();
            return String.format("%s %s", tableName, alias);        // VmInstanceVO VmInstanceInventory
        } else if (fields.size() == 1) {
            return String.format("%s.%s", alias, fields.get(0));    // VmInstanceInventory.name
        } else {
            StringBuilder expr = new StringBuilder();
            for (String field : fields) {
                expr.append(String.format("%s.%s ", alias, field));
            }
            return expr.toString();                                 // VmInstanceInventory.ip, VmInstanceInventory.name ...
        }
    }
}
