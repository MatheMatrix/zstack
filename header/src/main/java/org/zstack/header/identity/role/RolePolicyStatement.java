package org.zstack.header.identity.role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.zstack.header.identity.AccountConstant.POLICY_BASE_PACKAGE;
import static org.zstack.utils.CollectionUtils.*;

/**
 * <p>The complete statement is:</p>
 *
 * <blockquote><code>
 * ${effect}: ${actions} ${affectedResourceType} -> ${resources[0].effect}:${resources[0].uuid}
 * </code></blockquote>
 *
 * <p>Example:
 *
 * <li><code>Allow: org.zstack.header.vm.APIUpdateVmInstanceMsg</code>
 * <br/>(equals to <code>.header.vm.APIUpdateVmInstanceMsg</code>)
 *
 * <li><code>Allow: org.zstack.header.vm.APIUpdateVmInstanceMsg VmInstanceVO -> Single:90ededa347f452ecbae617c5692045dc</code>
 * <br/>(equals to <code>.header.vm.APIUpdateVmInstanceMsg -> 90ededa347f452ecbae617c5692045dc</code>)
 *
 * <li><code>Allow: org.zstack.header.vm.APIUpdateVmInstanceMsg VmInstanceVO -> Range:3cd2bb686e655f5882f54f971e99ed0e</code>
 */
public class RolePolicyStatement {
    public RolePolicyEffect effect = RolePolicyEffect.Allow;
    public String actions;
    public List<Resource> resources = new ArrayList<>();
    // also is resource type
    public String affectedResourceType;

    public static class Resource {
        public String uuid;
        public RolePolicyResourceEffect effect;
        // the resource type of uuid
        public transient String resourceType;
        public Resource(String uuid, RolePolicyResourceEffect effect) {
            this.uuid = uuid;
            this.effect = effect;
        }
    }

    public static String toStringStatement(RolePolicyVO vo) {
        if (isEmpty(vo.getResourceRefs())) {
            return toStringStatement(vo.getEffect(), vo.getActions());
        }
        return toStringStatement(vo.getEffect(), vo.getActions(), vo.getResourceType(), vo.getResourceRefs());
    }

    public static String toStringStatement(RolePolicyInventory inventory) {
        return toStringStatement(
                RolePolicyEffect.valueOf(inventory.getEffect()), inventory.getActions());
    }

    public static String toStringStatement(RolePolicyEffect effect, String action) {
        String prefix = RolePolicyEffect.Allow.equals(effect) ? "" : effect + ": ";
        return String.format("%s%s", prefix, action);
    }

    public static String toStringStatement(RolePolicyEffect effect,
                                           String action,
                                           String affectedResourceType,
                                           Set<RolePolicyResourceRefVO> resourceRefs) {
        String prefix = RolePolicyEffect.Allow.equals(effect) ? "" : effect + ": ";

        List<RolePolicyResourceRefVO> refs = new ArrayList<>(resourceRefs);
        refs.sort(Comparator.comparing(RolePolicyResourceRefVO::getResourceUuid));

        String suffix = String.join(",", transform(refs,
                ref -> ref.getEffect() == RolePolicyResourceEffect.Single ?
                        ref.getResourceUuid() :
                        ref.getEffect() + ":" + ref.getResourceUuid()));

        // Exclude: <api> <affectedResourceType> -> <resourceUuid>,<resourceUuid>,<resourceUuid>,Range:<resourceUuid>
        return String.format("%s%s %s -> %s", prefix, action, affectedResourceType, suffix);
    }

    public RolePolicyVO toVO() {
        RolePolicyVO policy = new RolePolicyVO();
        policy.setActions(actions);
        policy.setEffect(effect);

        if (resources.isEmpty()) {
            return policy;
        }

        policy.setResourceType(affectedResourceType);
        policy.setResourceRefs(transformToSet(resources, resource -> {
            RolePolicyResourceRefVO ref = new RolePolicyResourceRefVO();
            ref.setResourceUuid(resource.uuid);
            ref.setEffect(resource.effect);
            return ref;
        }));

        return policy;
    }

    public static String parseAction(String statement) {
        statement = statement.trim();

        if (statement.startsWith(POLICY_BASE_PACKAGE)) {
            statement = "." + statement.substring(POLICY_BASE_PACKAGE.length());
        }

        return statement;
    }
}
