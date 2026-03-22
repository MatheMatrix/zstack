package org.zstack.header.vm.metadata;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MetadataImpact {
    Impact value();

    /**
     * Spring bean name of the {@link VmUuidFromApiResolver} that converts the
     * field value(s) into VM UUID(s).
     *
     * <p>Must be specified for {@link Impact#CONFIG} and {@link Impact#STORAGE};
     * ignored for {@link Impact#NONE}.</p>
     */
    String resolver() default "";

    /**
     * The field name in the API message whose value will be extracted (via getter
     * reflection) and passed to the resolver.
     *
     * <p>For example, {@code field = "volumeUuid"} means the interceptor will call
     * {@code msg.getVolumeUuid()} and pass the result to
     * {@link VmUuidFromApiResolver#resolveVmUuids(String)}.</p>
     *
     * <p>The field value may be a {@code String} or {@code List<String>}.</p>
     */
    String field() default "";

    boolean updateOnFailure() default false;

    enum Impact {
        NONE,
        CONFIG,
        STORAGE
    }
}
