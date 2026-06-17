package com.aitestforge.service.agent;

import com.aitestforge.domain.chat.ChatMessage;
import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.chat.MessageRole;
import com.aitestforge.dto.chat.ToolResultRequest;
import com.aitestforge.dto.settings.SettingsResponse;
import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ToolCall;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import com.aitestforge.repository.ChatMessageRepository;
import com.aitestforge.repository.ChatSessionRepository;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.aitestforge.service.chat.ChatService;
import com.aitestforge.service.settings.SettingsService;
import com.aitestforge.service.spec.SpecControlFilter;
import com.aitestforge.service.spec.SpecToolConverter;
import com.aitestforge.service.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentLoopServiceTest {

    @Mock
    private AiService aiService;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private SubdomainSpecRepository specRepository;

    @Mock
    private SpecToolConverter specToolConverter;

    @Mock
    private SpecControlFilter specControlFilter;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private TwoStageFilterService twoStageFilterService;

    @Mock
    private SettingsService settingsService;

    private AgentLoopService agentLoopService;

    @BeforeEach
    void setUp() {
        agentLoopService = new AgentLoopService(
                aiService, chatService, messageRepository, sessionRepository,
                specRepository, specToolConverter, specControlFilter,
                workspaceService, twoStageFilterService, settingsService
        );
        ReflectionTestUtils.setField(agentLoopService, "maxIterations", 20);
        ReflectionTestUtils.setField(agentLoopService, "timeoutSeconds", 120);
        ReflectionTestUtils.setField(agentLoopService, "maxToolCallsPerTurn", 5);
        ReflectionTestUtils.setField(agentLoopService, "maxConcurrent", 10);
    }

    @Nested
    @DisplayName("startLoop")
    class StartLoop {

        @Test
        @DisplayName("실패: 동시 실행 한도 초과 시 E004 에러 SSE 이벤트 발생")
        void fail_concurrent_limit_exceeded_sends_e004_error() throws Exception {
            // given
            Long sessionId = 1L;
            ReflectionTestUtils.setField(agentLoopService, "maxConcurrent", 2);

            AtomicInteger activeLoopCount = (AtomicInteger) ReflectionTestUtils.getField(agentLoopService, "activeLoopCount");
            activeLoopCount.set(2); // 이미 한도에 도달

            SseEmitter emitter = spy(agentLoopService.createEmitter(sessionId));
            getEmitters().put(sessionId, emitter);

            // when
            agentLoopService.startLoop(sessionId, "테스트 메시지");

            // then
            ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(emitter, atLeast(2)).send(captor.capture());

            // activeLoopCount가 증가하지 않았는지 확인 (한도 초과라 시작 안 됨)
            assertThat(activeLoopCount.get()).isEqualTo(2);

            // chatService.addUserMessage가 호출되지 않았는지 확인
            then(chatService).should(never()).addUserMessage(anyLong(), anyString());
        }

        @Test
        @DisplayName("정상: AI가 텍스트 응답만 반환 → message SSE + done 이벤트")
        void success_ai_text_response_sends_message_and_done() throws Exception {
            // given
            Long sessionId = 1L;
            ChatSession session = createChatSession(sessionId, 100L);
            ChatMessage userMsg = createChatMessage(MessageRole.USER, "안녕하세요");

            SseEmitter emitter = spy(agentLoopService.createEmitter(sessionId));
            getEmitters().put(sessionId, emitter);

            given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));
            given(workspaceService.getDefaultMappings(100L)).willReturn(List.of());
            given(specRepository.findByStatus(any())).willReturn(List.of());
            given(specToolConverter.convertAll(anyList())).willReturn(List.of());
            given(specControlFilter.applyGlobalExclude(anyList())).willReturn(List.of());
            given(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .willReturn(List.of(userMsg));

            AiChatResponse aiResponse = new AiChatResponse("안녕하세요! 무엇을 도와드릴까요?", null);
            given(aiService.chat(anyList(), anyList())).willReturn(aiResponse);

            // when
            agentLoopService.startLoop(sessionId, "안녕하세요");

            // then
            then(chatService).should().addUserMessage(sessionId, "안녕하세요");
            then(chatService).should().addAssistantMessage(sessionId, "안녕하세요! 무엇을 도와드릴까요?");

            // SSE 이벤트 전송 검증 (message + done)
            verify(emitter, atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        @DisplayName("정상: AI가 tool_call을 반환 → tool_call_start SSE 이벤트 전송")
        void success_ai_tool_call_sends_tool_call_start() throws Exception {
            // given
            Long sessionId = 1L;
            ChatSession session = createChatSession(sessionId, 100L);
            ChatMessage userMsg = createChatMessage(MessageRole.USER, "회원 생성해줘");

            SseEmitter emitter = spy(agentLoopService.createEmitter(sessionId));
            getEmitters().put(sessionId, emitter);

            given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));
            given(workspaceService.getDefaultMappings(100L)).willReturn(List.of());
            given(specRepository.findByStatus(any())).willReturn(List.of());
            given(specToolConverter.convertAll(anyList())).willReturn(List.of());
            given(specControlFilter.applyGlobalExclude(anyList())).willReturn(List.of());
            given(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .willReturn(List.of(userMsg));

            ToolCall toolCall = new ToolCall(
                    "call_001",
                    "user-service__POST__api_members",
                    "{\"email\":\"test@test.com\"}"
            );
            AiChatResponse aiResponse = new AiChatResponse("회원을 생성하겠습니다.", List.of(toolCall));
            given(aiService.chat(anyList(), anyList())).willReturn(aiResponse);

            // when
            agentLoopService.startLoop(sessionId, "회원 생성해줘");

            // then
            then(chatService).should().addUserMessage(sessionId, "회원 생성해줘");
            then(chatService).should().addAssistantMessage(sessionId, "회원을 생성하겠습니다.");

            // tool_call_start SSE 이벤트 전송 검증
            verify(emitter, atLeast(1)).send(any(SseEmitter.SseEventBuilder.class));
            // emitter가 complete 되지 않음 (tool result를 기다림)
            verify(emitter, never()).complete();
        }
    }

    @Nested
    @DisplayName("handleToolResult")
    class HandleToolResult {

        @Test
        @DisplayName("정상: tool 결과 수신 후 다음 턴 진행 (AI 텍스트 응답 → 완료)")
        void success_tool_result_then_ai_text_completes_loop() throws Exception {
            // given
            Long sessionId = 1L;
            ChatSession session = createChatSession(sessionId, 100L);

            SseEmitter emitter = spy(agentLoopService.createEmitter(sessionId));
            getEmitters().put(sessionId, emitter);

            // 루프 상태를 수동으로 초기화 (startLoop가 이미 실행된 상태 시뮬레이션)
            initLoopState(sessionId);

            given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));
            given(workspaceService.getDefaultMappings(100L)).willReturn(List.of());
            given(specRepository.findByStatus(any())).willReturn(List.of());
            given(specToolConverter.convertAll(anyList())).willReturn(List.of());
            given(specControlFilter.applyGlobalExclude(anyList())).willReturn(List.of());

            ChatMessage userMsg = createChatMessage(MessageRole.USER, "회원 생성해줘");
            ChatMessage toolMsg = createChatMessage(MessageRole.TOOL, "{\"id\":456}");
            given(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                    .willReturn(List.of(userMsg, toolMsg));

            AiChatResponse aiResponse = new AiChatResponse("회원 생성이 완료되었습니다. (ID: 456)", null);
            given(aiService.chat(anyList(), anyList())).willReturn(aiResponse);

            SettingsResponse settingsResponse = new SettingsResponse("mock", "gpt-4o", 20, 5, 120, false);
            given(settingsService.getSettings()).willReturn(settingsResponse);

            ToolResultRequest toolResult = new ToolResultRequest("call_001", 200, "{\"id\":456}");

            // when
            agentLoopService.handleToolResult(sessionId, toolResult);

            // then
            then(chatService).should().addToolResultMessage(sessionId, "call_001", "{\"id\":456}");
            then(chatService).should().addAssistantMessage(sessionId, "회원 생성이 완료되었습니다. (ID: 456)");
            then(chatService).should().completeSession(sessionId);

            // done 이벤트 전송 후 emitter complete
            verify(emitter).complete();
        }
    }

    @Nested
    @DisplayName("checkConstraints")
    class CheckConstraints {

        @Test
        @DisplayName("실패: 최대 반복 횟수 초과 시 에러 이벤트 전송 후 루프 종료")
        void fail_max_iterations_exceeded_sends_error() throws Exception {
            // given
            Long sessionId = 1L;
            ReflectionTestUtils.setField(agentLoopService, "maxIterations", 3);

            SseEmitter emitter = spy(agentLoopService.createEmitter(sessionId));
            getEmitters().put(sessionId, emitter);

            // 반복 횟수를 한도까지 미리 설정
            initLoopStateWithIterations(sessionId, 3);

            ChatSession session = createChatSession(sessionId, 100L);
            given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));
            given(workspaceService.getDefaultMappings(100L)).willReturn(List.of());
            given(specRepository.findByStatus(any())).willReturn(List.of());
            given(specToolConverter.convertAll(anyList())).willReturn(List.of());
            given(specControlFilter.applyGlobalExclude(anyList())).willReturn(List.of());

            ToolResultRequest toolResult = new ToolResultRequest("call_001", 200, "{}");

            // when
            agentLoopService.handleToolResult(sessionId, toolResult);

            // then
            // AI 호출은 일어나지 않음 (제약 검사에서 이미 중단됨)
            then(aiService).should(never()).chat(anyList(), anyList());

            // 에러 메시지가 assistant로 추가되었는지 확인
            then(chatService).should().addAssistantMessage(eq(sessionId), contains("최대 반복 횟수"));

            // emitter가 complete됨
            verify(emitter).complete();
        }
    }

    // === Helper Methods ===

    @SuppressWarnings("unchecked")
    private Map<Long, SseEmitter> getEmitters() {
        return (Map<Long, SseEmitter>) ReflectionTestUtils.getField(agentLoopService, "emitters");
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> getLoopStates() {
        return (Map<Long, Object>) ReflectionTestUtils.getField(agentLoopService, "loopStates");
    }

    /**
     * 루프 상태를 초기화한다 (tool call을 실행한 상태로).
     * startLoop 이후, handleToolResult 전 상태를 시뮬레이션.
     */
    private void initLoopState(Long sessionId) {
        // startLoop를 통하지 않고 직접 상태를 주입하기 위해 reflection 사용
        Map<Long, Object> loopStates = getLoopStates();
        AtomicInteger activeLoopCount = (AtomicInteger) ReflectionTestUtils.getField(agentLoopService, "activeLoopCount");
        activeLoopCount.incrementAndGet();

        try {
            // LoopState는 private inner class이므로 reflection으로 생성
            Class<?> loopStateClass = Class.forName("com.aitestforge.service.agent.AgentLoopService$LoopState");
            var constructor = loopStateClass.getDeclaredConstructor(java.time.Instant.class, int.class, String.class);
            constructor.setAccessible(true);
            Object loopState = constructor.newInstance(java.time.Instant.now(), 1, "회원 생성해줘");

            // markToolCallExecuted 호출
            var markMethod = loopStateClass.getDeclaredMethod("markToolCallExecuted");
            markMethod.setAccessible(true);
            markMethod.invoke(loopState);

            loopStates.put(sessionId, loopState);
        } catch (Exception e) {
            throw new RuntimeException("LoopState 초기화 실패", e);
        }
    }

    /**
     * 지정된 반복 횟수로 루프 상태를 초기화한다.
     */
    private void initLoopStateWithIterations(Long sessionId, int iterations) {
        Map<Long, Object> loopStates = getLoopStates();
        AtomicInteger activeLoopCount = (AtomicInteger) ReflectionTestUtils.getField(agentLoopService, "activeLoopCount");
        activeLoopCount.incrementAndGet();

        try {
            Class<?> loopStateClass = Class.forName("com.aitestforge.service.agent.AgentLoopService$LoopState");
            var constructor = loopStateClass.getDeclaredConstructor(java.time.Instant.class, int.class, String.class);
            constructor.setAccessible(true);
            Object loopState = constructor.newInstance(java.time.Instant.now(), iterations, "테스트");

            var markMethod = loopStateClass.getDeclaredMethod("markToolCallExecuted");
            markMethod.setAccessible(true);
            markMethod.invoke(loopState);

            loopStates.put(sessionId, loopState);
        } catch (Exception e) {
            throw new RuntimeException("LoopState 초기화 실패", e);
        }
    }

    private ChatSession createChatSession(Long id, Long userId) {
        ChatSession session = ChatSession.builder()
                .title("테스트 세션")
                .userId(userId)
                .build();
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private ChatMessage createChatMessage(MessageRole role, String content) {
        return ChatMessage.builder()
                .role(role)
                .content(content)
                .build();
    }

    /**
     * String contains matcher for BDDMockito argThat.
     */
    private static String contains(String substring) {
        return argThat(arg -> arg != null && arg.contains(substring));
    }
}
