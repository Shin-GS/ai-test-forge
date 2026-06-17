package com.aitestforge.controller.spec;

import com.aitestforge.domain.spec.enums.SpecStatus;
import com.aitestforge.dto.spec.request.SpecRegisterRequest;
import com.aitestforge.dto.spec.response.SpecDetailResponse;
import com.aitestforge.dto.spec.response.SpecRegisterResponse;
import com.aitestforge.dto.spec.response.SpecResponse;
import com.aitestforge.service.spec.SpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Spec", description = "서브도메인 API 스펙 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/specs")
public class SpecController {

    private final SpecService specService;

    @Operation(summary = "API 스펙 등록/갱신", description = "서브도메인 서버의 OpenAPI 스펙을 등록하거나 갱신합니다. heartbeat 겸용. 대형 스펙(5MB 이상)은 비동기 처리되며 202를 반환합니다.")
    @PostMapping("/register")
    public ResponseEntity<SpecRegisterResponse> register(@Valid @RequestBody SpecRegisterRequest request) {
        SpecRegisterResponse response = specService.register(request);

        if (response.status() == SpecStatus.REGISTERING) {
            return ResponseEntity.accepted().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "등록된 스펙 목록 조회", description = "모든 등록된 서브도메인 API 스펙 목록을 반환합니다.")
    @GetMapping
    public ResponseEntity<List<SpecResponse>> getAll() {
        return ResponseEntity.ok(specService.getAll());
    }

    @Operation(summary = "특정 스펙 조회", description = "name + environment로 특정 서브도메인 스펙을 조회합니다.")
    @GetMapping("/{name}")
    public ResponseEntity<SpecResponse> getByName(
            @Parameter(description = "서브도메인 이름") @PathVariable String name,
            @Parameter(description = "환경 (default: default)") @RequestParam(defaultValue = "default") String environment) {
        return specService.getByNameAndEnvironment(name, environment)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "서브도메인 상세 (API 목록)", description = "특정 서브도메인의 파싱된 API 엔드포인트 목록을 조회합니다.")
    @GetMapping("/{name}/detail")
    public ResponseEntity<SpecDetailResponse> getDetail(
            @Parameter(description = "서브도메인 이름") @PathVariable String name,
            @Parameter(description = "환경 (default: default)") @RequestParam(defaultValue = "default") String environment) {
        return ResponseEntity.ok(specService.getDetail(name, environment));
    }
}
