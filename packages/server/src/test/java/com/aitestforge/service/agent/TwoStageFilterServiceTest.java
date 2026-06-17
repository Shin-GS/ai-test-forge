package com.aitestforge.service.agent;

import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ToolControl;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("TwoStageFilterService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class TwoStageFilterServiceTest {

    @Mock
    private AiService aiService;

    @InjectMocks
    private TwoStageFilterService twoStageFilterService;

    @Nested
    @DisplayName("filterTools")
    class FilterTools {

        @Test
        @DisplayName("정상: threshold 미만이면 전체 반환, AI 미호출")
        void success_skip_when_below_threshold() {
            ReflectionTestUtils.setField(twoStageFilterService,
                    "twoStageThreshold", 30);
            List<ToolDefinition> tools = ungrouped("svc", 10);

            var result = twoStageFilterService.filterTools(tools, "회원 생성");

            assertThat(result).hasSize(10);
            then(aiService).should(never()).chat(any(), any());
        }

        @Test
        @DisplayName("정상: threshold 이상이면 AI 호출하여 필터링")
        void success_filters_when_above_threshold() {
            ReflectionTestUtils.setField(twoStageFilterService,
                    "twoStageThreshold", 5);
            List<ToolDefinition> tools = new ArrayList<>();
            tools.addAll(grouped("user-service", 5));
            tools.addAll(grouped("payment-service", 5));

            given(aiService.chat(any(), any()))
                    .willReturn(new AiChatResponse("user-service", List.of()));

            var result = twoStageFilterService.filterTools(tools, "회원 생성");

            assertThat(result).allMatch(t -> t.name().startsWith("user-service__"));
            then(aiService).should().chat(any(), any());
        }

        @Test
        @DisplayName("정상: 존재하지 않는 서브도메인은 무시 (할루시네이션 방지)")
        void success_ignores_hallucinated_subdomains() {
            ReflectionTestUtils.setField(twoStageFilterService,
                    "twoStageThreshold", 5);
            List<ToolDefinition> tools = new ArrayList<>();
            tools.addAll(grouped("user-service", 5));
            tools.addAll(grouped("payment-service", 5));

            given(aiService.chat(any(), any()))
                    .willReturn(new AiChatResponse("user-service, ghost-svc", List.of()));

            var result = twoStageFilterService.filterTools(tools, "주문");

            assertThat(result).allMatch(t -> t.name().startsWith("user-service__"));
            assertThat(result).noneMatch(t -> t.name().contains("ghost"));
        }

        @Test
        @DisplayName("정상: AI 빈 결과 + 재시도 빈 결과 → 전체 fallback")
        void success_fallback_when_all_empty() {
            ReflectionTestUtils.setField(twoStageFilterService,
                    "twoStageThreshold", 5);
            List<ToolDefinition> tools = new ArrayList<>();
            tools.addAll(grouped("user-service", 5));
            tools.addAll(grouped("payment-service", 5));

            given(aiService.chat(any(), any()))
                    .willReturn(new AiChatResponse("", List.of()));

            var result = twoStageFilterService.filterTools(tools, "뭔가");

            assertThat(result).hasSize(10);
        }
    }

    @Nested
    @DisplayName("addSubdomainTools")
    class AddSubdomainTools {

        @Test
        @DisplayName("정상: 새 서브도메인 tool 추가")
        void success_adds_new_subdomain() {
            var existing = new ArrayList<>(ungrouped("user-service", 3));
            var all = new ArrayList<ToolDefinition>();
            all.addAll(ungrouped("user-service", 3));
            all.addAll(ungrouped("payment-service", 3));

            var result = twoStageFilterService.addSubdomainTools(
                    existing, all, "payment-service");

            assertThat(result).hasSize(6);
        }

        @Test
        @DisplayName("정상: 이미 존재하면 중복 추가 안됨")
        void success_no_duplicates() {
            var existing = new ArrayList<>(ungrouped("user-service", 3));
            var all = new ArrayList<>(ungrouped("user-service", 3));

            var result = twoStageFilterService.addSubdomainTools(
                    existing, all, "user-service");

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("정상: 없는 서브도메인이면 기존 목록 그대로")
        void success_returns_existing_when_not_found() {
            var existing = new ArrayList<>(ungrouped("user-service", 3));
            var all = new ArrayList<>(ungrouped("user-service", 3));

            var result = twoStageFilterService.addSubdomainTools(
                    existing, all, "ghost-service");

            assertThat(result).hasSize(3);
        }
    }

    // === Helper Methods ===

    private List<ToolDefinition> ungrouped(String subdomain, int count) {
        List<ToolDefinition> tools = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tools.add(new ToolDefinition(
                    subdomain + "__GET__api_r" + i, "desc " + i, "{}"));
        }
        return tools;
    }

    private List<ToolDefinition> grouped(String subdomain, int count) {
        List<ToolDefinition> tools = new ArrayList<>();
        var ctrl = new ToolControl(false, null, null, false, List.of("grp"));
        for (int i = 0; i < count; i++) {
            tools.add(new ToolDefinition(
                    subdomain + "__GET__api_r" + i, "desc " + i, "{}", ctrl));
        }
        return tools;
    }
}
