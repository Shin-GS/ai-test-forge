package com.aitestforge.controller.settings;

import com.aitestforge.dto.settings.SettingsResponse;
import com.aitestforge.dto.settings.UpdateSettingsRequest;
import com.aitestforge.service.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Settings", description = "AI 및 Agent Loop 설정 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    @Operation(summary = "설정 조회", description = "현재 AI Provider, 모델, Agent Loop 설정을 조회합니다.")
    @GetMapping
    public ResponseEntity<SettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @Operation(summary = "설정 수정", description = "AI Provider, 모델, Agent Loop 설정을 변경합니다. 런타임 반영되며 재시작 시 원복됩니다.")
    @PutMapping
    public ResponseEntity<SettingsResponse> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(settingsService.updateSettings(request));
    }
}
