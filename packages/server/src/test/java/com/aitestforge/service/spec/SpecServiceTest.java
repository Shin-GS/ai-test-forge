package com.aitestforge.service.spec;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.spec.enums.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.spec.request.SpecRegisterRequest;
import com.aitestforge.dto.spec.response.SpecRegisterResponse;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.aitestforge.service.recipe.RecipeSpecValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SpecServiceTest {

    @Mock
    private SubdomainSpecRepository specRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SpecAsyncProcessor specAsyncProcessor;

    @Mock
    private RecipeSpecValidator recipeSpecValidator;

    @InjectMocks
    private SpecService specService;

    @BeforeEach
    void setUp() {
        // asyncThreshold: 5MB (기본값)
        ReflectionTestUtils.setField(specService, "asyncThreshold", 5_242_880L);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("정상: 신규 등록 성공 (specJson 있음, 동기 처리)")
        void success_new_registration_with_spec_json() {
            // given
            SpecRegisterRequest request = new SpecRegisterRequest(
                    "user-service", "dev", "http://user:8080",
                    "{\"openapi\":\"3.0.0\"}", null, null
            );
            given(specRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.empty());
            given(specRepository.save(any(SubdomainSpec.class)))
                    .willAnswer(invocation -> {
                        SubdomainSpec spec = invocation.getArgument(0);
                        ReflectionTestUtils.setField(spec, "id", 1L);
                        return spec;
                    });

            // when
            SpecRegisterResponse response = specService.register(request);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("user-service");
            assertThat(response.environment()).isEqualTo("dev");
            assertThat(response.status()).isEqualTo(SpecStatus.ACTIVE);
            assertThat(response.message()).isEqualTo("spec registered");
            then(specRepository).should().save(any(SubdomainSpec.class));
        }

        @Test
        @DisplayName("정상: 기존 등록이 있으면 갱신 (specJson 변경됨)")
        void success_update_existing_spec() {
            // given
            SubdomainSpec existingSpec = SubdomainSpec.builder()
                    .name("user-service")
                    .environment("dev")
                    .baseUrl("http://user:8080")
                    .specJson("{\"old\":\"spec\"}")
                    .specHash("old-hash")
                    .status(SpecStatus.ACTIVE)
                    .build();
            ReflectionTestUtils.setField(existingSpec, "id", 1L);

            SpecRegisterRequest request = new SpecRegisterRequest(
                    "user-service", "dev", "http://user:8080",
                    "{\"openapi\":\"3.1.0\"}", null, null
            );
            given(specRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(existingSpec));

            // when
            SpecRegisterResponse response = specService.register(request);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.status()).isEqualTo(SpecStatus.ACTIVE);
            assertThat(response.message()).isEqualTo("spec updated");
            assertThat(existingSpec.getSpecJson()).isEqualTo("{\"openapi\":\"3.1.0\"}");
            then(recipeSpecValidator).should().validateAllForSubdomain("user-service", "dev");
        }

        @Test
        @DisplayName("정상: heartbeat만 (specJson 없음, specHash 일치 시 상태 유지)")
        void success_heartbeat_hash_match() {
            // given
            SubdomainSpec existingSpec = SubdomainSpec.builder()
                    .name("user-service")
                    .environment("dev")
                    .baseUrl("http://user:8080")
                    .specJson("{\"openapi\":\"3.0.0\"}")
                    .specHash("abc123")
                    .status(SpecStatus.ACTIVE)
                    .build();
            ReflectionTestUtils.setField(existingSpec, "id", 1L);

            SpecRegisterRequest request = new SpecRegisterRequest(
                    "user-service", "dev", "http://user:8080",
                    null, "abc123", null
            );
            given(specRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(existingSpec));

            // when
            SpecRegisterResponse response = specService.register(request);

            // then
            assertThat(response.status()).isEqualTo(SpecStatus.ACTIVE);
            assertThat(response.message()).isEqualTo("heartbeat accepted");
            then(specRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("정상: heartbeat 해시 불일치 시 resend 메시지 반환")
        void success_heartbeat_hash_mismatch_returns_resend() {
            // given
            SubdomainSpec existingSpec = SubdomainSpec.builder()
                    .name("user-service")
                    .environment("dev")
                    .baseUrl("http://user:8080")
                    .specJson("{\"openapi\":\"3.0.0\"}")
                    .specHash("server-hash")
                    .status(SpecStatus.ACTIVE)
                    .build();
            ReflectionTestUtils.setField(existingSpec, "id", 1L);

            SpecRegisterRequest request = new SpecRegisterRequest(
                    "user-service", "dev", "http://user:8080",
                    null, "client-different-hash", null
            );
            given(specRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(existingSpec));

            // when
            SpecRegisterResponse response = specService.register(request);

            // then
            assertThat(response.status()).isEqualTo(SpecStatus.ACTIVE);
            assertThat(response.message()).isEqualTo("hash mismatch, resend spec");
        }

        @Test
        @DisplayName("정상: 비동기 처리 대상 (5MB 이상이면 REGISTERING 상태)")
        void success_async_processing_for_large_spec() {
            // given
            // asyncThreshold를 100바이트로 낮춰서 테스트 용이하게
            ReflectionTestUtils.setField(specService, "asyncThreshold", 100L);

            String largeSpec = "x".repeat(200); // 100바이트 이상

            SpecRegisterRequest request = new SpecRegisterRequest(
                    "payment-service", "dev", "http://payment:8080",
                    largeSpec, null, null
            );
            given(specRepository.findByNameAndEnvironment("payment-service", "dev"))
                    .willReturn(Optional.empty());
            given(specRepository.save(any(SubdomainSpec.class)))
                    .willAnswer(invocation -> {
                        SubdomainSpec spec = invocation.getArgument(0);
                        ReflectionTestUtils.setField(spec, "id", 2L);
                        return spec;
                    });

            // when
            SpecRegisterResponse response = specService.register(request);

            // then
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.status()).isEqualTo(SpecStatus.REGISTERING);
            assertThat(response.message()).isEqualTo("async processing");
            then(specRepository).should().save(any(SubdomainSpec.class));
        }
    }

    @Nested
    @DisplayName("getDetail")
    class GetDetail {

        @Test
        @DisplayName("실패: 존재하지 않는 서브도메인이면 BusinessException")
        void fail_subdomain_not_found_throws_exception() {
            // given
            given(specRepository.findByNameAndEnvironment("unknown-service", "dev"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> specService.getDetail("unknown-service", "dev"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    // === Helper Methods ===

    private SubdomainSpec createSpec(Long id, String name, String environment, SpecStatus status) {
        SubdomainSpec spec = SubdomainSpec.builder()
                .name(name)
                .environment(environment)
                .baseUrl("http://" + name + ":8080")
                .specJson("{\"openapi\":\"3.0.0\"}")
                .specHash("hash")
                .status(status)
                .build();
        ReflectionTestUtils.setField(spec, "id", id);
        return spec;
    }
}
