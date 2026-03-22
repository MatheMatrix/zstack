package org.zstack.header.vm.metadata;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MetadataImpact {
    Impact value();

    /**
     * Spring bean name of the {@link VmUuidFromApiResolver} that extracts vmUuid(s)
     * from this API message. Must be specified for {@link Impact#CONFIG} and
     * {@link Impact#STORAGE}; ignored for {@link Impact#NONE}.
     *
     * <p>The bean must be registered in Spring XML. Interceptor looks it up at
     * startup via {@code ComponentLoader.getComponentByBeanName()}.</p>
     */
    String resolver() default "";

    boolean updateOnFailure() default false;

    enum Impact {
        NONE,
        CONFIG,
        STORAGE
    }
}