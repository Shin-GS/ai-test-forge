package com.aitestforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI Test Forge에 해당 API에 대한 힌트 정보를 제공합니다.
 * AI가 이 API를 적절히 사용하기 위한 추가 맥락을 전달합니다.
 * 예: "이 API 호출 전에 반드시 회원 가입이 필요합니다"
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeHint {
    String value();
}
