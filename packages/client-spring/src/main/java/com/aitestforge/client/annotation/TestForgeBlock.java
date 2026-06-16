package com.aitestforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI Test Forge에서 해당 API 호출을 차단합니다.
 * 위험한 API(삭제, 초기화 등)에 사용하여 AI가 실수로 호출하지 못하도록 합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeBlock {
    String reason() default "";
}
