package org.zstack.header.message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Created by Wenhao.Zhang on 2024/08/09
 */
@Target({java.lang.annotation.ElementType.FIELD, ElementType.TYPE})
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface OverriddenResourceValidation {
    String field();

    ResourceValidation validation();
}
