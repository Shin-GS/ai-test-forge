package com.aitestforge.service.settings;

import com.aitestforge.dto.settings.response.SettingsResponse;
import com.aitestforge.dto.settings.request.UpdateSettingsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설정 조회/수정 서비스.
 * AI 모델 설정은 읽기 전용 (환경변수/application.yml에서만 변경 가능).
 * Agent Loop 파라미터와 힌트 토글은 런타임 메모리에서 관리하며,
 * 서버 재시작 시 application.yml 기본값으로 원복된다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class SettingsService {

    // AI 모델 설정 — 읽기 전용 (런타임 변경 불가)
    private final String reasoningProvider;
    private final String reasoningModel;
    private final String fastProvider;
    private final String fastModel;

    // Agent Loop 설정 — 런타임 변경 가능
    private volatile int maxIterations;
    private volatile int maxToolCallsPerTurn;
    private volatile int timeoutSeconds;
    private volatile boolean nextActionHintEnabled;

    public SettingsService(
            @Value("${ai.reasoning.provider:openai}") String reasoningProvider,
            @Value("${ai.reasoning.model:gpt-4o}") String reasoningModel,
            @Value("${ai.fast.provider:openai}") String fastProvider,
            @Value("${ai.fast.model:gpt-4o-mini}") String fastModel,
            @Value("${agent-loop.max-iterations:20}") int maxIterations,
            @Value("${agent-loop.max-tool-calls-per-turn:5}") int maxToolCallsPerTurn,
            @Value("${agent-loop.timeout-seconds:120}") int timeoutSeconds) {
        this.reasoningProvider = reasoningProvider;
        this.reasoningModel = reasoningModel;
        this.fastProvider = fastProvider;
        this.fastModel = fastModel;
        this.maxIterations = maxIterations;
        this.maxToolCallsPerTurn = maxToolCallsPerTurn;
        this.timeoutSeconds = timeoutSeconds;
        this.nextActionHintEnabled = false;
    }

    public SettingsResponse getSettings() {
        return new SettingsResponse(
                reasoningProvider,
                reasoningModel,
                fastProvider,
                fastModel,
                maxIterations,
                maxToolCallsPerTurn,
                timeoutSeconds,
                nextActionHintEnabled
        );
    }

    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        this.maxIterations = request.maxIterations();
        this.maxToolCallsPerTurn = request.maxToolCallsPerTurn();
        this.timeoutSeconds = request.timeoutSeconds();
        this.nextActionHintEnabled = request.nextActionHintEnabled();

        log.info("Settings updated: maxIterations={}, maxToolCallsPerTurn={}, timeoutSeconds={}, nextActionHint={}",
                maxIterations, maxToolCallsPerTurn, timeoutSeconds, nextActionHintEnabled);

        return new SettingsResponse(
                reasoningProvider,
                reasoningModel,
                fastProvider,
                fastModel,
                maxIterations,
                maxToolCallsPerTurn,
                timeoutSeconds,
                nextActionHintEnabled
        );
    }
}
