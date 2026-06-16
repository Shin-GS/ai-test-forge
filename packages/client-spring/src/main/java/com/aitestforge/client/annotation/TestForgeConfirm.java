package com.aitestforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI Test Forge에서 해당 API 호출 전 사용자 확인을 요구합니다.
 * 민감하지만 완전히 차단할 필요는 없는 API에 사용합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeConfirm {
    String message() default "";
}
