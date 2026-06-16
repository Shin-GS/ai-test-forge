package com.aitestforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 해당 API를 논리적 그룹에 포함시킵니다.
 * 2-Stage Strategy에서 관련 API를 필터링할 때 활용됩니다.
 * 복수 그룹에 속할 수 있습니다 (Repeatable).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(TestForgeGroups.class)
public @interface TestForgeGroup {
    String value();
}
