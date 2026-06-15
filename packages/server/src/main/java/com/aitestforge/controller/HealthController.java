package com.aitestforge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @Operation(summary = "헬스 체크", description = "서버 및 DB 연결 상태를 확인합니다.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        String timestamp = Instant.now().toString();
        try (var connection = dataSource.getConnection()) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "timestamp", timestamp
            ));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DOWN",
                    "timestamp", timestamp
            ));
        }
    }
}
