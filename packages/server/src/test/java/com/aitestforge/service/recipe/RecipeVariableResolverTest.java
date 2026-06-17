package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.RecipeVariableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecipeVariableResolver 단위 테스트")
class RecipeVariableResolverTest {

    private final RecipeVariableResolver resolver =
            new RecipeVariableResolver(new ObjectMapper());

    @Nested
    @DisplayName("resolveBody")
    class ResolveBody {

        @Test
        @DisplayName("정상: context 변수 치환")
        void success_resolve_context_variable() {
            // given
            String body = "{\"memberId\": \"{{memberId}}\"}";
            Map<String, String> context = Map.of("memberId", "123");

            // when
            String result = resolver.resolveBody(body, context);

            // then
            assertThat(result).isEqualTo("{\"memberId\": \"123\"}");
        }

        @Test
        @DisplayName("정상: gen:email 치환 시 test_*@test.com 형태")
        void success_resolve_gen_email() {
            String body = "{\"email\": \"{{gen:email}}\"}";
            String result = resolver.resolveBody(body, Map.of());
            assertThat(result).matches(".*test_[a-z0-9]{8}@test\\.com.*");
        }

        @Test
        @DisplayName("정상: gen:uuid 치환 시 UUID 형태")
        void success_resolve_gen_uuid() {
            String body = "{\"id\": \"{{gen:uuid}}\"}";
            String result = resolver.resolveBody(body, Map.of());
            assertThat(result).containsPattern(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("정상: gen:phone 치환 시 010-XXXX-XXXX 형태")
        void success_resolve_gen_phone() {
            String body = "{\"phone\": \"{{gen:phone}}\"}";
            String result = resolver.resolveBody(body, Map.of());
            assertThat(result).containsPattern("010-\\d{4}-\\d{4}");
        }

        @Test
        @DisplayName("정상: gen:koreanName 치환 시 3자 한국어 이름")
        void success_resolve_gen_korean_name() {
            String body = "{\"name\": \"{{gen:koreanName}}\"}";
            String result = resolver.resolveBody(body, Map.of());
            // 한국어 이름 3자 (성1 + 이름2)
            assertThat(result).containsPattern("[가-힣]{3}");
        }

        @Test
        @DisplayName("정상: gen:date 치환 시 yyyy-MM-dd 형태")
        void success_resolve_gen_date() {
            String body = "{\"date\": \"{{gen:date}}\"}";
            String result = resolver.resolveBody(body, Map.of());
            assertThat(result).containsPattern("\\d{4}-\\d{2}-\\d{2}");
        }

        @Test
        @DisplayName("정상: null body이면 빈 JSON 반환")
        void success_null_body_returns_empty() {
            String result = resolver.resolveBody(null, Map.of());
            assertThat(result).isEqualTo("{}");
        }

        @Test
        @DisplayName("실패: 미정의 변수 참조 시 RecipeVariableException")
        void fail_undefined_variable() {
            String body = "{\"id\": \"{{unknownVar}}\"}";
            assertThatThrownBy(() -> resolver.resolveBody(body, Map.of()))
                    .isInstanceOf(RecipeVariableException.class);
        }

        @Test
        @DisplayName("실패: 미지원 gen 타입 시 RecipeVariableException")
        void fail_unsupported_gen_type() {
            String body = "{\"x\": \"{{gen:unsupported}}\"}";
            assertThatThrownBy(() -> resolver.resolveBody(body, Map.of()))
                    .isInstanceOf(RecipeVariableException.class);
        }
    }

    @Nested
    @DisplayName("extractVariables")
    class ExtractVariables {

        @Test
        @DisplayName("정상: JSONPath로 값 추출")
        void success_extract_jsonpath() {
            String responseBody = "{\"data\":{\"id\":456,\"name\":\"test\"}}";
            Map<String, String> extracts = Map.of(
                    "memberId", "$.data.id",
                    "memberName", "$.data.name"
            );

            Map<String, String> result = resolver.extractVariables(responseBody, extracts);

            assertThat(result).containsEntry("memberId", "456");
            assertThat(result).containsEntry("memberName", "test");
        }

        @Test
        @DisplayName("정상: extracts가 null이면 빈 Map 반환")
        void success_null_extracts() {
            Map<String, String> result = resolver.extractVariables("{}", null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 JSONPath면 RecipeVariableException")
        void fail_path_not_found() {
            String responseBody = "{\"data\":{\"id\":1}}";
            Map<String, String> extracts = Map.of("x", "$.missing.path");

            assertThatThrownBy(() -> resolver.extractVariables(responseBody, extracts))
                    .isInstanceOf(RecipeVariableException.class);
        }
    }

    // === Helper Methods ===
}
