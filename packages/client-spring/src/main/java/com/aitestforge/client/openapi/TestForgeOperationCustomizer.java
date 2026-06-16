package com.aitestforge.client.openapi;

import com.aitestforge.client.annotation.TestForgeBlock;
import com.aitestforge.client.annotation.TestForgeConfirm;
import com.aitestforge.client.annotation.TestForgeExclude;
import com.aitestforge.client.annotation.TestForgeGroup;
import com.aitestforge.client.annotation.TestForgeGroups;
import com.aitestforge.client.annotation.TestForgeHint;
import com.aitestforge.client.annotation.TestForgeReadOnly;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * springdoc OperationCustomizer 구현체.
 * TestForge 어노테이션을 OpenAPI 확장 필드(x-test-forge-*)로 변환합니다.
 *
 * compileOnly 의존성이므로, springdoc이 클래스패스에 없으면
 * @ConditionalOnClass에 의해 이 빈이 등록되지 않습니다.
 */
public class TestForgeOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // 1. 클래스 레벨 @TestForgeExclude 확인
        Class<?> beanType = handlerMethod.getBeanType();
        TestForgeExclude classExclude = beanType.getAnnotation(TestForgeExclude.class);
        if (classExclude != null) {
            addExcludeExtension(operation, classExclude);
            return operation;
        }

        // 2. 메서드 레벨 어노테이션 확인
        TestForgeExclude methodExclude = handlerMethod.getMethodAnnotation(TestForgeExclude.class);
        if (methodExclude != null) {
            addExcludeExtension(operation, methodExclude);
        }

        TestForgeBlock block = handlerMethod.getMethodAnnotation(TestForgeBlock.class);
        if (block != null) {
            addBlockExtension(operation, block);
        }

        TestForgeConfirm confirm = handlerMethod.getMethodAnnotation(TestForgeConfirm.class);
        if (confirm != null) {
            addConfirmExtension(operation, confirm);
        }

        TestForgeReadOnly readOnly = handlerMethod.getMethodAnnotation(TestForgeReadOnly.class);
        if (readOnly != null) {
            operation.addExtension("x-test-forge-readonly", true);
        }

        TestForgeHint hint = handlerMethod.getMethodAnnotation(TestForgeHint.class);
        if (hint != null) {
            operation.addExtension("x-test-forge-hint", hint.value());
        }

        // @TestForgeGroup — @Repeatable 처리
        addGroupExtension(operation, handlerMethod);

        return operation;
    }

    private void addExcludeExtension(Operation operation, TestForgeExclude exclude) {
        if (exclude.reason().isEmpty()) {
            operation.addExtension("x-test-forge-exclude", true);
        } else {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("reason", exclude.reason());
            operation.addExtension("x-test-forge-exclude", value);
        }
    }

    private void addBlockExtension(Operation operation, TestForgeBlock block) {
        if (block.reason().isEmpty()) {
            operation.addExtension("x-test-forge-block", Map.of());
        } else {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("reason", block.reason());
            operation.addExtension("x-test-forge-block", value);
        }
    }

    private void addConfirmExtension(Operation operation, TestForgeConfirm confirm) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("message", confirm.message());
        operation.addExtension("x-test-forge-confirm", value);
    }

    private void addGroupExtension(Operation operation, HandlerMethod handlerMethod) {
        List<String> groups = new ArrayList<>();

        // 컨테이너 어노테이션(@TestForgeGroups)에서 가져오기
        TestForgeGroups container = handlerMethod.getMethodAnnotation(TestForgeGroups.class);
        if (container != null) {
            for (TestForgeGroup group : container.value()) {
                groups.add(group.value());
            }
        }

        // 단일 @TestForgeGroup (컨테이너가 없을 때)
        if (groups.isEmpty()) {
            TestForgeGroup single = handlerMethod.getMethodAnnotation(TestForgeGroup.class);
            if (single != null) {
                groups.add(single.value());
            }
        }

        if (!groups.isEmpty()) {
            operation.addExtension("x-test-forge-group", groups);
        }
    }
}
