package org.zstack.header.message;

import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.search.Inventory;
import org.zstack.header.tag.TagPatternVO;
import org.zstack.header.tag.TagResourceType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class APICreateMessage extends APIMessage {
    /**
     * @desc resource uuid which must be of version 4(random) with dash stripped. For example,
     * '5d94103e-1925-4d86-96c0-f05489c259ab' is stripped as '5d94103e19254d8696c0f05489c259ab'.
     * When the field is provided, it's used as uuid for resource to be created. An internal error
     * is raised if the uuid conflicted with any existing resource uuid
     */
    private String resourceUuid;

    @APIParam(required = false, resourceType = TagPatternVO.class)
    private List<String> tagUuids;

    public void addSystemTag(String tag) {
        if (systemTags == null) {
            systemTags = new ArrayList<String>();
        }
        systemTags.add(tag);
    }

    public String getResourceUuid() {
        return resourceUuid;
    }

    public void setResourceUuid(String resourceUuid) {
        this.resourceUuid = resourceUuid;
    }

    public List<String> getTagUuids() {
        return tagUuids;
    }

    public void setTagUuids(List<String> tagUuids) {
        this.tagUuids = tagUuids;
    }

    public Class<?> resourceType() {
        return findResourceType(getClass());
    }

    public static Class<?> findResourceType(Class<? extends APICreateMessage> myClass) {
        final TagResourceType annotation = myClass.getAnnotation(TagResourceType.class);
        if (annotation != null) {
            return annotation.value();
        }

        RestRequest restRequest = myClass.getAnnotation(RestRequest.class);
        if (restRequest == null) {
            throw new CloudRuntimeException("failed to find resource type for class " + myClass.getName());
        }

        Class<?> responseClass = restRequest.responseClass();

        // field responseClass.inventory
        try {
            final Field inventory = responseClass.getField("inventory");
            Class<?> inventoryClass = inventory.getType();

            Inventory inventoryAnnotation = inventoryClass.getAnnotation(Inventory.class);
            if (inventoryAnnotation != null) {
                return inventoryAnnotation.mappingVOClass();
            }
        } catch (NoSuchFieldException ignored) {}

        throw new CloudRuntimeException("failed to find resource type for class " + myClass.getName());
    }
}
