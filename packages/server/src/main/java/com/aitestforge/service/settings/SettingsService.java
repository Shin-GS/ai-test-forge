package com.aitestforge.service.settings;

import com.aitestforge.dto.settings.SettingsResponse;
import com.aitestforge.dto.settings.UpdateSettingsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설정 조회/수정 서비스.
 * 런타임 메모리에서 관리하며, 서버 재시작 시 application.yml 기본값으로 원복된다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class SettingsService {

    private volatile String aiProvider;
    private volatile String aiModel;
    private volatile int maxIterations;
    private volatile int maxToolCallsPerTurn;
    private volatile int timeoutSeconds;

    public SettingsService(
            @Value("${ai.provider:mock}") String aiProvider,
            @Value("${ai.openai.model:gpt-4o}") String aiModel,
            @Value("${agent-loop.max-iterations:20}") int maxIterations,
            @Value("${agent-loop.max-tool-calls-per-turn:5}") int maxToolCallsPerTurn,
            @Value("${agent-loop.timeout-seconds:120}") int timeoutSeconds) {
        this.aiProvider = aiProvider;
        this.aiModel = aiModel;
        this.maxIterations = maxIterations;
        this.maxToolCallsPerTurn = maxToolCallsPerTurn;
        this.timeoutSeconds = timeoutSeconds;
    }

    public SettingsResponse getSettings() {
        return new SettingsResponse(
                aiProvider,
                aiModel,
                maxIterations,
                maxToolCallsPerTurn,
                timeoutSeconds
        );
    }

    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        this.aiProvider = request.aiProvider();
        this.aiModel = request.aiModel();
        this.maxIterations = request.maxIterations();
        this.maxToolCallsPerTurn = request.maxToolCallsPerTurn();
        this.timeoutSeconds = request.timeoutSeconds();

        log.info("Settings updated: provider={}, model={}, maxIterations={}, maxToolCallsPerTurn={}, timeoutSeconds={}",
                aiProvider, aiModel, maxIterations, maxToolCallsPerTurn, timeoutSeconds);

        return new SettingsResponse(
                aiProvider,
                aiModel,
                maxIterations,
                maxToolCallsPerTurn,
                timeoutSeconds
        );
    }
}
