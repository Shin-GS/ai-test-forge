package com.aitestforge.service.spec;

import com.aitestforge.infra.ai.dto.ToolControl;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpecControlFilter 단위 테스트")
class SpecControlFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("applyGlobalExclude")
    class ApplyGlobalExclude {

        @Test
        @DisplayName("정상: DELETE method가 제외된다")
        void success_exclude_by_method() {
            var filter = new SpecControlFilter(
                    List.of("DELETE"), List.of(), List.of());
            var del = tool("svc__DELETE__api_x", "삭제");
            var get = tool("svc__GET__api_y", "조회");

            var result = filter.applyGlobalExclude(List.of(del, get));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).contains("GET");
        }

        @Test
        @DisplayName("정상: admin 경로가 제외된다")
        void success_exclude_by_path() {
            var filter = new SpecControlFilter(
                    List.of(), List.of("/**/admin/**"), List.of());
            var admin = tool("svc__GET__api_admin_x", "어드민");
            var normal = tool("svc__GET__api_members", "회원");

            var result = filter.applyGlobalExclude(List.of(admin, normal));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).contains("members");
        }

        @Test
        @DisplayName("정상: deprecated 태그가 제외된다")
        void success_exclude_by_tag() {
            var filter = new SpecControlFilter(
                    List.of(), List.of(), List.of("deprecated"));
            var ctrl = new ToolControl(false, null, null, false, List.of("deprecated"));
            var dep = new ToolDefinition("svc__GET__api_old", "레거시", "{}", ctrl);
            var normal = tool("svc__GET__api_new", "신규");

            var result = filter.applyGlobalExclude(List.of(dep, normal));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).contains("new");
        }

        @Test
        @DisplayName("정상: 빈 규칙이면 전체 반환")
        void success_no_filter_when_empty() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            var t1 = tool("svc__DELETE__api_x", "삭제");
            var t2 = tool("svc__GET__api_y", "조회");

            var result = filter.applyGlobalExclude(List.of(t1, t2));

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("isExcluded")
    class IsExcluded {

        @Test
        @DisplayName("정상: true이면 제외")
        void success_excluded_when_true() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            op.put("x-test-forge-exclude", true);
            assertThat(filter.isExcluded(op)).isTrue();
        }

        @Test
        @DisplayName("정상: 필드 없으면 제외 아님")
        void success_not_excluded_when_absent() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            assertThat(filter.isExcluded(op)).isFalse();
        }
    }

    @Nested
    @DisplayName("parseControlFromOperation")
    class ParseControl {

        @Test
        @DisplayName("정상: block reason 파싱")
        void success_parse_block() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            ObjectNode block = objectMapper.createObjectNode();
            block.put("reason", "실 결제");
            op.set("x-test-forge-block", block);

            ToolControl ctrl = filter.parseControlFromOperation(op);

            assertThat(ctrl.blocked()).isTrue();
            assertThat(ctrl.blockReason()).isEqualTo("실 결제");
        }

        @Test
        @DisplayName("정상: confirm message 파싱")
        void success_parse_confirm() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            ObjectNode confirm = objectMapper.createObjectNode();
            confirm.put("message", "실행?");
            op.set("x-test-forge-confirm", confirm);

            ToolControl ctrl = filter.parseControlFromOperation(op);

            assertThat(ctrl.confirmMessage()).isEqualTo("실행?");
        }

        @Test
        @DisplayName("정상: readonly true 파싱")
        void success_parse_readonly() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            op.put("x-test-forge-readonly", true);

            ToolControl ctrl = filter.parseControlFromOperation(op);

            assertThat(ctrl.readonly()).isTrue();
        }

        @Test
        @DisplayName("정상: group 배열 파싱")
        void success_parse_group_array() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            op.set("x-test-forge-group",
                    objectMapper.createArrayNode().add("A").add("B"));

            ToolControl ctrl = filter.parseControlFromOperation(op);

            assertThat(ctrl.groups()).containsExactly("A", "B");
        }

        @Test
        @DisplayName("정상: null이면 ToolControl.none()")
        void success_null_returns_none() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ToolControl ctrl = filter.parseControlFromOperation(null);
            assertThat(ctrl.blocked()).isFalse();
            assertThat(ctrl.groups()).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseHint")
    class ParseHint {

        @Test
        @DisplayName("정상: 힌트 문자열 반환")
        void success_parse_hint() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            op.put("x-test-forge-hint", "관리자 권한 필요");
            assertThat(filter.parseHint(op)).isEqualTo("관리자 권한 필요");
        }

        @Test
        @DisplayName("정상: 필드 없으면 null")
        void success_returns_null() {
            var filter = new SpecControlFilter(List.of(), List.of(), List.of());
            ObjectNode op = objectMapper.createObjectNode();
            assertThat(filter.parseHint(op)).isNull();
        }
    }

    // === Helper Methods ===

    private ToolDefinition tool(String name, String desc) {
        return new ToolDefinition(name, desc, "{}");
    }
}
