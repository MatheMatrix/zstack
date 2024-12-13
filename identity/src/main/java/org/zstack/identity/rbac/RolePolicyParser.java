package org.zstack.identity.rbac;

import org.zstack.core.db.Q;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.header.identity.role.RolePolicyEffect;
import org.zstack.header.identity.role.RolePolicyStatement;
import org.zstack.header.identity.role.RolePolicyVO;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.vo.ResourceVO_;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.StringDSL;

import javax.persistence.Tuple;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.err;
import static org.zstack.header.identity.IdentityErrors.INVALID_ROLE_POLICY;
import static org.zstack.header.identity.role.RolePolicyResourceEffect.*;
import static org.zstack.header.identity.role.RolePolicyStatement.Resource;
import static org.zstack.utils.CollectionUtils.*;

/**
 * Thread unsafe
 */
public class RolePolicyParser {
    private final List<RolePolicyStatement> results = new ArrayList<>();

    public ErrorableValue<List<RolePolicyStatement>> parse(List<Object> objects) {
        results.clear();

        if (CollectionUtils.isEmpty(objects)) {
            return ErrorableValue.of(new ArrayList<>());
        }

        for (Object object : objects) {
            final ErrorCode error = parseOne(object);
            if (error != null) {
                return ErrorableValue.ofErrorCode(error);
            }
        }

        // fill RolePolicyStatement.Resource.resourceType
        ErrorCode errorCode = fillResourceType();
        if (errorCode != null) {
            return ErrorableValue.ofErrorCode(errorCode);
        }

        // ${actions} -> ${vm.uuid},${l3.uuid},${image.uuid}
        // will be split to:
        // [${actions} -> ${vm.uuid}], [${actions} -> ${l3.uuid}], [${actions} -> ${image.uuid}]
        split();
        return ErrorableValue.of(results);
    }

    @SuppressWarnings("unchecked")
    private ErrorCode parseOne(Object object) {
        if (object instanceof String) {
            return parseString(((String) object));
        } else if (object instanceof Map) {
            return parseMap((Map<String, Object>) object);
        } else if (object instanceof RolePolicyVO) {
            return parseVO((RolePolicyVO) object);
        } else {
            return err(INVALID_ROLE_POLICY, "invalid role policy: " + object);
        }
    }

    private ErrorCode parseVO(RolePolicyVO policy) {
        String text = RolePolicyStatement.toStringStatement(policy);
        return parseString(text);
    }

    private ErrorCode parseString(String policy) {
        String statement = policy.trim();

        RolePolicyStatement value = new RolePolicyStatement();
        if (statement.startsWith("Allow:")) {
            value.effect = RolePolicyEffect.Allow;
            statement = statement.substring("Allow:".length()).trim();
        } else if (statement.startsWith("Exclude:")) {
            value.effect = RolePolicyEffect.Exclude;
            statement = statement.substring("Exclude:".length()).trim();
        }

        String[] split = statement.split("->");
        if (split.length == 2) {
            value.resources.addAll(parseResource(split[1]));
        } else if (split.length > 2) {
            return err(INVALID_ROLE_POLICY, "invalid role policy: " + policy);
        }

        String actionAndType = split[0].trim();
        split = actionAndType.split(" ");
        if (split.length > 2) {
            return err(INVALID_ROLE_POLICY, "invalid role policy: " + policy);
        } else if (split.length == 2) {
            value.affectedResourceType = split[1].trim();
        }

        value.actions = parseAction(split[0]);

        final ErrorCode error = checkStatement(value);
        if (error != null) {
            return error;
        }

        results.add(value);
        return null;
    }

    public static List<Resource> parseResource(String statement) {
        final String[] split = statement.trim().split(",");
        return Arrays.stream(split)
                .map(script -> parseResourceScript(script.trim()))
                .collect(Collectors.toList());
    }

    private static Resource parseResourceScript(String script) {
        if (script.startsWith("Range:")) {
            return new Resource(script.substring("Range:".length()).trim(), Range);
        }
        return new Resource(script.trim(), Single);
    }

    private static String parseAction(String statement) {
        return RolePolicyStatement.parseAction(statement);
    }

    private ErrorCode parseMap(Map<String, Object> map) {
        RolePolicyEffect effect;
        String resourceType;
        List<Resource> resources = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        final Object effectObject = map.get("effect");
        if (effectObject == null || "Allow".equals(effectObject)) {
            effect = RolePolicyEffect.Allow;
        } else if ("Exclude".equals(effectObject)) {
            effect = RolePolicyEffect.Exclude;
        } else {
            return err(INVALID_ROLE_POLICY, "invalid role policy effect: " + effectObject);
        }

        final Object resourcesObject = map.get("resources");
        if (resourcesObject == null) {
            // do-nothing
        } else if (resourcesObject instanceof String) {
            resources.addAll(parseResource((String) resourcesObject));
        } else if (resourcesObject instanceof List) {
            ((List<?>) resourcesObject).forEach(r -> resources.addAll(parseResource((String) r)));
        } else {
            return err(INVALID_ROLE_POLICY, "invalid role policy resources: " + resourcesObject);
        }

        final Object actionsObject = map.get("actions");
        if (actionsObject instanceof String) {
            actions.add(parseAction((String) actionsObject));
        } else if (actionsObject instanceof List) {
            ((List<?>) actionsObject).forEach(r -> actions.add(parseAction(Objects.toString(r))));
        } else {
            return err(INVALID_ROLE_POLICY, "invalid role policy actions: " + actionsObject);
        }

        final Object typeObject = map.get("resourceType");
        if (typeObject instanceof String) {
            resourceType = (String) typeObject;
        } else {
            resourceType = null;
        }

        final List<RolePolicyStatement> list = transform(actions, action -> {
            RolePolicyStatement statement = new RolePolicyStatement();
            statement.effect = effect;
            statement.actions = action;
            statement.affectedResourceType = resourceType;
            statement.resources = new ArrayList<>(resources);
            return statement;
        });

        for (RolePolicyStatement statement : list) {
            ErrorCode error = checkStatement(statement);
            if (error != null) {
                return error;
            }
        }

        results.addAll(list);
        return null;
    }

    private ErrorCode checkStatement(RolePolicyStatement statement) {
        if (statement.affectedResourceType == null) {
            boolean rangeResourceExists = statement.resources.stream()
                    .anyMatch(resource -> resource.effect == Range);
            if (rangeResourceExists) {
                return err(INVALID_ROLE_POLICY,
                        "invalid role policy %s: range resource statement must specific affect resource type", statement);
            }
        }

        for (Resource resource : statement.resources) {
            // resource.uuid must be in uuid format
            if (!StringDSL.isZStackUuid(resource.uuid)) {
                return err(INVALID_ROLE_POLICY, "invalid role policy %s: invalid resources format");
            }
        }

        return null;
    }

    private ErrorCode fillResourceType() {
        Map<String, List<RolePolicyStatement.Resource>> resourceMap = results.stream()
                .flatMap(statement -> statement.resources.stream())
                .collect(Collectors.groupingBy(resource -> resource.uuid));

        if (!resourceMap.isEmpty()) {
            List<Tuple> tuples = Q.New(ResourceVO.class)
                    .select(ResourceVO_.uuid, ResourceVO_.resourceType)
                    .in(ResourceVO_.uuid, resourceMap.keySet())
                    .listTuple();
            if (resourceMap.size() != tuples.size()) {
                for (Tuple tuple : tuples) {
                    resourceMap.remove(tuple.get(0, String.class));
                }
                return err(INVALID_ROLE_POLICY,
                        "invalid role policy resource: resource[uuid:%s] is not found",
                        resourceMap.keySet());
            }

            for (Tuple tuple : tuples) {
                resourceMap.get(tuple.get(0, String.class)).forEach(it -> it.resourceType = tuple.get(1, String.class));
            }
        }

        return null;
    }

    private void split() {
        List<RolePolicyStatement> list = new ArrayList<>();

        for (RolePolicyStatement statement : results) {
            if (statement.affectedResourceType != null || statement.resources.isEmpty()) {
                list.add(statement);
                continue;
            }

            Map<String, RolePolicyStatement> typeStatementMap = new HashMap<>();
            for (Resource resource : statement.resources) {
                typeStatementMap.compute(resource.resourceType, (type, statement2) -> {
                    if (statement2 == null) {
                        statement2 = cloneWithoutResource(statement);
                        statement2.affectedResourceType = type;
                    }
                    statement2.resources.add(resource);
                    return statement2;
                });
            }
            list.addAll(typeStatementMap.values());
        }

        results.clear();
        results.addAll(list);
    }

    private RolePolicyStatement cloneWithoutResource(RolePolicyStatement statement) {
        RolePolicyStatement ret = new RolePolicyStatement();
        ret.actions = statement.actions;
        ret.effect = statement.effect;
        ret.affectedResourceType = statement.affectedResourceType;
        return ret;
    }
}
