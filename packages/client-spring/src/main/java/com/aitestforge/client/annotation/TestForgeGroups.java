package com.aitestforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link TestForgeGroup}의 컨테이너 어노테이션입니다.
 * 직접 사용하지 않으며, @Repeatable 메커니즘에 의해 자동으로 적용됩니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeGroups {
    TestForgeGroup[] value();
}
