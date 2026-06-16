package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.RecipeVariableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 레시피 변수 치환 및 JSONPath 추출을 담당하는 유틸리티 서비스.
 * 상태를 가지지 않으며, 모든 메서드는 입력에 대해 순수한 결과를 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeVariableResolver {

    // {{gen:타입}} 또는 {{변수명}} 패턴
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");
    private static final String GEN_PREFIX = "gen:";

    // gen:koreanName 용 한국어 성
    private static final String[] KOREAN_SURNAMES = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};

    // gen:date 포맷
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 랜덤 영숫자 문자열 생성용
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final ObjectMapper objectMapper;

    /**
     * body JSON 문자열에서 변수를 치환한다.
     *
     * @param bodyJson step의 body JSON 문자열
     * @param context  변수 컨텍스트 (이전 step extract 결과 + input 변수)
     * @return 치환된 body JSON 문자열
     * @throws RecipeVariableException 미지원 gen 타입, 미정의 변수 참조 시
     */
    public String resolveBody(String bodyJson, Map<String, String> context) {
        if (bodyJson == null || bodyJson.isBlank()) {
            return "{}";
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(bodyJson);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String resolved = resolveExpression(expression, context);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 응답 body에서 JSONPath로 값을 추출한다.
     *
     * @param responseBody HTTP 응답 body 문자열
     * @param extracts     Map<변수명, JSONPath 표현식>
     * @return 추출된 변수 Map
     * @throws RecipeVariableException JSONPath 추출 실패 시
     */
    public Map<String, String> extractVariables(String responseBody, Map<String, String> extracts) {
        if (extracts == null || extracts.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : extracts.entrySet()) {
            String variableName = entry.getKey();
            String jsonPathExpr = entry.getValue();

            try {
                Object extracted = JsonPath.read(responseBody, jsonPathExpr);
                result.put(variableName, convertToString(extracted));
            } catch (PathNotFoundException e) {
                String preview = truncateBody(responseBody, 500);
                throw new RecipeVariableException(
                        "JSONPath extraction failed: path '%s' not found for variable '%s'"
                                .formatted(jsonPathExpr, variableName),
                        variableName,
                        preview
                );
            } catch (Exception e) {
                String preview = truncateBody(responseBody, 500);
                throw new RecipeVariableException(
                        "JSONPath extraction failed: '%s' for variable '%s' - %s"
                                .formatted(jsonPathExpr, variableName, e.getMessage()),
                        variableName,
                        preview
                );
            }
        }

        return result;
    }

    /**
     * 단일 표현식을 해석하여 값을 반환한다.
     * gen:* 패턴이면 자동 생성, 아니면 context에서 조회.
     */
    private String resolveExpression(String expression, Map<String, String> context) {
        if (expression.startsWith(GEN_PREFIX)) {
            String genType = expression.substring(GEN_PREFIX.length());
            return generateValue(genType);
        }

        // 변수 참조 — context에서 조회
        String value = context.get(expression);
        if (value == null) {
            throw new RecipeVariableException(
                    "Undefined variable reference: '%s'".formatted(expression),
                    expression
            );
        }
        return value;
    }

    /**
     * gen 타입에 따라 랜덤 값을 생성한다.
     */
    private String generateValue(String genType) {
        return switch (genType) {
            case "email" -> generateEmail();
            case "koreanName" -> generateKoreanName();
            case "phone" -> generatePhone();
            case "uuid" -> UUID.randomUUID().toString();
            case "number" -> generateNumber();
            case "date" -> generateDate();
            default -> throw new RecipeVariableException(
                    "Unsupported gen type: '%s'".formatted(genType),
                    "gen:" + genType
            );
        };
    }

    /**
     * test_{8자 랜덤 영숫자}@test.com
     */
    private String generateEmail() {
        return "test_" + randomAlphanumeric(8) + "@test.com";
    }

    /**
     * 한국어 성(10종 중 1) + 이름 2자(가~힣 중 랜덤)
     */
    private String generateKoreanName() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String surname = KOREAN_SURNAMES[random.nextInt(KOREAN_SURNAMES.length)];

        // 가(0xAC00) ~ 힣(0xD7A3) 범위에서 랜덤 2자
        char first = (char) random.nextInt(0xAC00, 0xD7A4);
        char second = (char) random.nextInt(0xAC00, 0xD7A4);

        return surname + first + second;
    }

    /**
     * 010-{4자리랜덤}-{4자리랜덤}
     */
    private String generatePhone() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int middle = random.nextInt(1000, 10000);
        int last = random.nextInt(1000, 10000);
        return "010-%04d-%04d".formatted(middle, last);
    }

    /**
     * 1~9999 사이 랜덤 정수 문자열
     */
    private String generateNumber() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(1, 10000));
    }

    /**
     * 오늘 ±30일 범위 yyyy-MM-dd 형식
     */
    private String generateDate() {
        int offset = ThreadLocalRandom.current().nextInt(-30, 31);
        return LocalDate.now().plusDays(offset).format(DATE_FORMATTER);
    }

    /**
     * 지정 길이의 랜덤 영숫자 문자열 생성
     */
    private String randomAlphanumeric(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * 추출 결과를 String으로 변환.
     * 숫자면 toString(), 객체/배열이면 JSON 직렬화.
     */
    private String convertToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        // 객체 또는 배열 — JSON 직렬화
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    /**
     * 응답 body를 지정 길이로 잘라서 preview 생성
     */
    private String truncateBody(String body, int maxLength) {
        if (body == null) return "";
        if (body.length() <= maxLength) return body;
        return body.substring(0, maxLength) + "...(truncated)";
    }
}
