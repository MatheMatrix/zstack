package org.zstack.header.message;

import org.zstack.header.vo.ResourceVO;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(java.lang.annotation.ElementType.FIELD)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface ResourceValidation {
    /**
     * <p>Indicate which types of resources are allowed for this field.
     * An empty list indicates that any type of resource is allowed.
     *
     * <p>If an API parameters allow VmInstanceVO:
     * <blockquote><pre>
     * resourceTypes = { VmInstanceVO.class }
     * </pre></blockquote><p>
     *
     * <p>If an API parameters allow AccountVO or AccountGroupVO:
     * <blockquote><pre>
     * resourceTypes = { AccountVO.class, AccountGroupVO.class }
     * </pre></blockquote><p>
     * </p>
     *
     * @see ResourceVO#getResourceType()
     */
    Class<?>[] value();

    /**
     * <p>Indicate which user operations are allowed in the current API.
     *
     * <p>Any shared account has the right to start and stop VM,
     * However, deleting some resources can only be done by the OWNER.
     *
     * <p>Default value AUTO means:
     * <ol>
     * <li>If this resource is globally visible, its scope is {@link #SCOPE_ALLOWED_ALL}
     * <li>If this resource is NOT globally visible, its scope is {@link #SCOPE_ALLOWED_SHARING}
     * </li>
     * </ol>
     *
     * <p>If this resource is AccountVO:
     * <ol>
     * <li>{@link #SCOPE_ALLOWED_ALL} means everyone is allowed
     * <li>{@link #SCOPE_ALLOWED_SHARING} means everyone is allowed (the same as AllowedAll)
     * <li>{@link #SCOPE_MUST_OWNER} means only myself is allowed
     * </li>
     * </ol>
     * </p>
     *
     * @see #SCOPE_AUTO
     * @see #SCOPE_MUST_OWNER
     * @see #SCOPE_ALLOWED_SHARING
     * @see #SCOPE_ALLOWED_ALL
     */
    String scope() default SCOPE_AUTO;

    public static final String SCOPE_NONE = "None"; // remove soon (after removing APIParam.operationTarget and checkAccount)
    public static final String SCOPE_AUTO = "Auto";
    public static final String SCOPE_MUST_OWNER = "MustOwner";
    public static final String SCOPE_ALLOWED_SHARING = "AllowedSharing";
    public static final String SCOPE_ALLOWED_ALL = "AllowedAll";

    boolean successIfResourceNotExisting() default false;
}
