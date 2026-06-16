package com.aitestforge.controller.chat;

import com.aitestforge.domain.auth.User;
import com.aitestforge.dto.chat.*;
import com.aitestforge.service.agent.AgentLoopService;
import com.aitestforge.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "Chat", description = "채팅 세션 및 메시지 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final AgentLoopService agentLoopService;

    @Operation(summary = "채팅 세션 생성", description = "새로운 채팅 세션을 생성합니다.")
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @RequestBody(required = false) CreateSessionRequest request,
            @AuthenticationPrincipal User user) {
        if (request == null) {
            request = new CreateSessionRequest(null);
        }
        return ResponseEntity.ok(chatService.createSession(request, user.getId()));
    }

    @Operation(summary = "채팅 세션 목록 조회", description = "현재 사용자의 채팅 세션을 최신 순으로 조회합니다.")
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getAllSessions(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatService.getAllSessions(user.getId()));
    }

    @Operation(summary = "채팅 세션 상세 조회", description = "특정 채팅 세션의 정보를 조회합니다.")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(
            @Parameter(description = "세션 ID") @PathVariable Long sessionId) {
        return ResponseEntity.ok(chatService.getSession(sessionId));
    }

    @Operation(summary = "세션 메시지 목록 조회", description = "특정 세션의 모든 메시지를 시간순으로 조회합니다.")
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @Parameter(description = "세션 ID") @PathVariable Long sessionId) {
        return ResponseEntity.ok(chatService.getMessages(sessionId));
    }

    @Operation(summary = "메시지 전송", description = "사용자 메시지를 전송하고 Agent Loop를 시작합니다. 세션이 WAITING 상태이면 자동으로 ACTIVE로 복원 후 재개합니다.")
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Void> sendMessage(
            @Parameter(description = "세션 ID") @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        if (chatService.isWaiting(sessionId)) {
            chatService.resumeSession(sessionId);
        }
        agentLoopService.startLoop(sessionId, request.message());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "SSE 스트림 연결", description = "Agent Loop 진행 상태를 실시간으로 수신하는 SSE 스트림을 연결합니다.")
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(description = "세션 ID") @PathVariable Long sessionId) {
        return agentLoopService.createEmitter(sessionId);
    }

    @Operation(summary = "Tool 실행 결과 전달", description = "FE가 서브도메인 API를 호출한 결과를 BE에 전달합니다.")
    @PostMapping("/{sessionId}/tool-result")
    public ResponseEntity<Void> toolResult(
            @Parameter(description = "세션 ID") @PathVariable Long sessionId,
            @Valid @RequestBody ToolResultRequest request) {
        agentLoopService.handleToolResult(sessionId, request);
        return ResponseEntity.ok().build();
    }
}
