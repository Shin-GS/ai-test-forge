package com.aitestforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 해당 API가 읽기 전용(부작용 없음)임을 AI Test Forge에 알립니다.
 * AI가 안전하게 반복 호출할 수 있는 API를 식별하는 데 사용됩니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeReadOnly {
}
