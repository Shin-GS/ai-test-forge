package com.aitestforge.service.recipe;

import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.enums.RecipeValidationStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.recipe.response.RecipeValidationResult;
import com.aitestforge.dto.recipe.ValidationIssue;
import com.aitestforge.dto.recipe.ValidationIssueType;
import com.aitestforge.repository.RecipeRepository;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeSpecValidator {

    private final SubdomainSpecRepository subdomainSpecRepository;
    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper;

    /**
     * 레시피의 각 step을 현재 DB의 서브도메인 스펙과 비교하여 호환성 검증.
     * @return 검증 결과 (status + issues)
     */
    public RecipeValidationResult validate(Recipe recipe) {
        List<ValidationIssue> issues = new ArrayList<>();

        JsonNode steps = parseSteps(recipe.getStepsJson());
        if (steps == null || !steps.isArray()) {
            log.warn("Failed to parse stepsJson for recipe: {}", recipe.getId());
            return new RecipeValidationResult(RecipeValidationStatus.VALID, List.of());
        }

        for (int i = 0; i < steps.size(); i++) {
            JsonNode step = steps.get(i);
            validateStep(i, step, issues);
        }

        RecipeValidationStatus status = determineStatus(issues);
        return new RecipeValidationResult(status, issues);
    }

    /**
     * 특정 서브도메인의 스펙이 갱신되었을 때, 해당 서브도메인을 사용하는 모든 레시피를 검증.
     * 결과를 각 Recipe 엔티티의 validationStatus, validationMessage에 반영.
     */
    @Async
    @Transactional
    public void validateAllForSubdomain(String subdomainName, String environment) {
        log.info("Background validation started for subdomain: {} ({})", subdomainName, environment);

        List<Recipe> recipes = recipeRepository.findByStepsJsonContaining(subdomainName);
        if (recipes.isEmpty()) {
            log.info("No recipes found referencing subdomain: {}", subdomainName);
            return;
        }

        int updatedCount = 0;
        for (Recipe recipe : recipes) {
            try {
                RecipeValidationResult result = validate(recipe);
                recipe.updateValidation(result.status(), buildValidationMessage(result));
                updatedCount++;
            } catch (Exception e) {
                log.warn("Failed to validate recipe {}: {}", recipe.getId(), e.getMessage());
            }
        }

        log.info("Background validation completed for subdomain: {} ({}), updated {} recipes",
                subdomainName, environment, updatedCount);
    }

    private void validateStep(int stepIndex, JsonNode step, List<ValidationIssue> issues) {
        String subdomain = getTextOrNull(step, "subdomain");
        String environment = getTextOrDefault(step, "environment", "default");
        String method = getTextOrNull(step, "method");
        String path = getTextOrNull(step, "path");
        String stepName = buildStepName(method, path, subdomain);

        if (subdomain == null) {
            return; // subdomain 정보 없는 step은 스킵
        }

        // 1. 서브도메인 스펙 조회
        Optional<SubdomainSpec> specOpt = subdomainSpecRepository.findByNameAndEnvironment(subdomain, environment);
        if (specOpt.isEmpty()) {
            issues.add(new ValidationIssue(stepIndex, stepName,
                    ValidationIssueType.SUBDOMAIN_NOT_FOUND,
                    "Subdomain not found: " + subdomain + " (" + environment + ")"));
            return;
        }

        SubdomainSpec spec = specOpt.get();
        if (spec.getSpecJson() == null || spec.getSpecJson().isBlank()) {
            return; // 스펙 JSON 없으면 검증 불가 (에러로 처리하지 않음)
        }

        if (method == null || path == null) {
            return; // method/path 없는 step은 스킵
        }

        // 2. paths에서 operation 찾기
        JsonNode operation = findOperation(spec.getSpecJson(), method, path);
        if (operation == null) {
            issues.add(new ValidationIssue(stepIndex, stepName,
                    ValidationIssueType.API_NOT_FOUND,
                    "API not found in spec: " + method.toUpperCase() + " " + path
                            + " (subdomain: " + subdomain + ")"));
            return;
        }

        // 3. body 필드 검증 (bodyStrategy가 fixed 또는 gen인 step에서만)
        String bodyStrategy = getTextOrNull(step, "bodyStrategy");
        if (bodyStrategy != null
                && !bodyStrategy.equalsIgnoreCase("fixed")
                && !bodyStrategy.equalsIgnoreCase("gen")) {
            return; // ai-generate/ai-fill 등은 AI가 생성하므로 스킵
        }

        validateBodyFields(stepIndex, stepName, step, operation, issues);
    }

    private void validateBodyFields(int stepIndex, String stepName, JsonNode step, JsonNode operation, List<ValidationIssue> issues) {
        // requestBody → content → application/json → schema → required
        JsonNode schema = operation.path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema");

        if (schema.isMissingNode() || schema.isEmpty()) {
            return; // requestBody 스키마 없으면 검증 스킵
        }

        // required 필드 목록
        Set<String> requiredFields = new HashSet<>();
        JsonNode requiredNode = schema.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode field : requiredNode) {
                requiredFields.add(field.asText());
            }
        }

        // schema의 properties에서 정의된 필드 목록
        Set<String> definedFields = new HashSet<>();
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            properties.fieldNames().forEachRemaining(definedFields::add);
        }

        // step.body의 key 목록
        JsonNode body = step.get("body");
        Set<String> bodyFields = new HashSet<>();
        if (body != null && body.isObject()) {
            body.fieldNames().forEachRemaining(bodyFields::add);
        }

        // required인데 body에 없는 필드 체크
        for (String requiredField : requiredFields) {
            if (!bodyFields.contains(requiredField)) {
                issues.add(new ValidationIssue(stepIndex, stepName,
                        ValidationIssueType.REQUIRED_FIELD_MISSING,
                        "Required field missing in body: " + requiredField));
            }
        }

        // body에 있지만 스키마에 없는 필드 체크 (WARN 레벨)
        if (!definedFields.isEmpty()) {
            for (String bodyField : bodyFields) {
                if (!definedFields.contains(bodyField)) {
                    issues.add(new ValidationIssue(stepIndex, stepName,
                            ValidationIssueType.UNKNOWN_FIELD,
                            "Unknown field in body (not in schema): " + bodyField));
                }
            }
        }
    }

    private JsonNode findOperation(String specJson, String method, String path) {
        try {
            JsonNode root = objectMapper.readTree(specJson);
            JsonNode paths = root.get("paths");
            if (paths == null || !paths.isObject()) {
                return null;
            }

            JsonNode pathNode = paths.get(path);
            if (pathNode == null || !pathNode.isObject()) {
                return null;
            }

            JsonNode operation = pathNode.get(method.toLowerCase());
            return (operation != null && operation.isObject()) ? operation : null;
        } catch (Exception e) {
            log.warn("Failed to parse specJson for operation lookup: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode parseSteps(String stepsJson) {
        if (stepsJson == null || stepsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(stepsJson);
        } catch (Exception e) {
            log.warn("Failed to parse stepsJson: {}", e.getMessage());
            return null;
        }
    }

    private RecipeValidationStatus determineStatus(List<ValidationIssue> issues) {
        if (issues.isEmpty()) {
            return RecipeValidationStatus.VALID;
        }

        boolean hasCritical = issues.stream()
                .anyMatch(issue -> issue.issueType() == ValidationIssueType.API_NOT_FOUND
                        || issue.issueType() == ValidationIssueType.REQUIRED_FIELD_MISSING
                        || issue.issueType() == ValidationIssueType.SUBDOMAIN_NOT_FOUND);

        return hasCritical ? RecipeValidationStatus.BROKEN : RecipeValidationStatus.WARN;
    }

    private String buildValidationMessage(RecipeValidationResult result) {
        if (result.issues().isEmpty()) {
            return null;
        }
        return result.issues().size() + " issue(s) found: "
                + result.issues().stream()
                .map(i -> "[" + i.issueType() + "] " + i.description())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        String value = getTextOrNull(node, field);
        return value != null ? value : defaultValue;
    }

    private String buildStepName(String method, String path, String subdomain) {
        if (method != null && path != null) {
            return method.toUpperCase() + " " + path;
        }
        return subdomain != null ? subdomain : "unknown";
    }
}
