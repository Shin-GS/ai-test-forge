package com.aitestforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 해당 메서드 또는 클래스를 AI Test Forge 테스트 대상에서 제외합니다.
 * 클래스 레벨에 적용하면 해당 컨트롤러의 모든 엔드포인트가 제외됩니다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeExclude {
    String reason() default "";
}
