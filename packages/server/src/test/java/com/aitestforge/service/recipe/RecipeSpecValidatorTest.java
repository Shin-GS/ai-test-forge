package com.aitestforge.service.recipe;

import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.enums.RecipeValidationStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.recipe.ValidationIssue;
import com.aitestforge.dto.recipe.ValidationIssueType;
import com.aitestforge.dto.recipe.response.RecipeValidationResult;
import com.aitestforge.repository.RecipeRepository;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecipeSpecValidatorTest {

    @Mock
    private SubdomainSpecRepository subdomainSpecRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RecipeSpecValidator recipeSpecValidator;

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("정상: 모든 step이 스펙과 호환되면 VALID")
        void success_all_steps_compatible_returns_valid() {
            // given
            String stepsJson = """
                    [
                      {
                        "subdomain": "user-service",
                        "environment": "dev",
                        "method": "POST",
                        "path": "/api/members",
                        "bodyStrategy": "fixed",
                        "body": { "email": "test@test.com", "name": "홍길동" }
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);

            String specJson = """
                    {
                      "paths": {
                        "/api/members": {
                          "post": {
                            "requestBody": {
                              "content": {
                                "application/json": {
                                  "schema": {
                                    "required": ["email", "name"],
                                    "properties": {
                                      "email": { "type": "string" },
                                      "name": { "type": "string" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """;
            SubdomainSpec spec = createSpec("user-service", "dev", specJson);
            given(subdomainSpecRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(spec));

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.VALID);
            assertThat(result.issues()).isEmpty();
        }

        @Test
        @DisplayName("정상: 스키마에 없는 필드를 body에 포함하면 WARN (UNKNOWN_FIELD)")
        void success_unknown_field_returns_warn() {
            // given
            String stepsJson = """
                    [
                      {
                        "subdomain": "user-service",
                        "environment": "dev",
                        "method": "POST",
                        "path": "/api/members",
                        "bodyStrategy": "fixed",
                        "body": { "email": "test@test.com", "name": "홍길동", "unknownField": "value" }
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);

            String specJson = """
                    {
                      "paths": {
                        "/api/members": {
                          "post": {
                            "requestBody": {
                              "content": {
                                "application/json": {
                                  "schema": {
                                    "required": ["email", "name"],
                                    "properties": {
                                      "email": { "type": "string" },
                                      "name": { "type": "string" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """;
            SubdomainSpec spec = createSpec("user-service", "dev", specJson);
            given(subdomainSpecRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(spec));

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.WARN);
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().getFirst().issueType()).isEqualTo(ValidationIssueType.UNKNOWN_FIELD);
        }

        @Test
        @DisplayName("실패: 서브도메인이 존재하지 않으면 BROKEN (SUBDOMAIN_NOT_FOUND)")
        void fail_subdomain_not_found_returns_broken() {
            // given
            String stepsJson = """
                    [
                      {
                        "subdomain": "nonexistent-service",
                        "environment": "dev",
                        "method": "POST",
                        "path": "/api/something"
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);
            given(subdomainSpecRepository.findByNameAndEnvironment("nonexistent-service", "dev"))
                    .willReturn(Optional.empty());

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.BROKEN);
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().getFirst().issueType()).isEqualTo(ValidationIssueType.SUBDOMAIN_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: API가 스펙에 존재하지 않으면 BROKEN (API_NOT_FOUND)")
        void fail_api_not_found_returns_broken() {
            // given
            String stepsJson = """
                    [
                      {
                        "subdomain": "user-service",
                        "environment": "dev",
                        "method": "DELETE",
                        "path": "/api/nonexistent",
                        "bodyStrategy": "fixed"
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);

            String specJson = """
                    {
                      "paths": {
                        "/api/members": {
                          "post": {}
                        }
                      }
                    }
                    """;
            SubdomainSpec spec = createSpec("user-service", "dev", specJson);
            given(subdomainSpecRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(spec));

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.BROKEN);
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().getFirst().issueType()).isEqualTo(ValidationIssueType.API_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: required 필드가 body에 없으면 BROKEN (REQUIRED_FIELD_MISSING)")
        void fail_required_field_missing_returns_broken() {
            // given
            String stepsJson = """
                    [
                      {
                        "subdomain": "user-service",
                        "environment": "dev",
                        "method": "POST",
                        "path": "/api/members",
                        "bodyStrategy": "fixed",
                        "body": { "name": "홍길동" }
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);

            // email이 required인데 body에 없음
            String specJson = """
                    {
                      "paths": {
                        "/api/members": {
                          "post": {
                            "requestBody": {
                              "content": {
                                "application/json": {
                                  "schema": {
                                    "required": ["email", "name"],
                                    "properties": {
                                      "email": { "type": "string" },
                                      "name": { "type": "string" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """;
            SubdomainSpec spec = createSpec("user-service", "dev", specJson);
            given(subdomainSpecRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(spec));

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.BROKEN);
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().getFirst().issueType()).isEqualTo(ValidationIssueType.REQUIRED_FIELD_MISSING);
            assertThat(result.issues().getFirst().description()).contains("email");
        }

        @Test
        @DisplayName("정상: bodyStrategy가 ai-generate이면 body 검증을 스킵")
        void success_ai_generate_strategy_skips_body_validation() {
            // given
            String stepsJson = """
                    [
                      {
                        "subdomain": "user-service",
                        "environment": "dev",
                        "method": "POST",
                        "path": "/api/members",
                        "bodyStrategy": "ai-generate",
                        "body": {}
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);

            String specJson = """
                    {
                      "paths": {
                        "/api/members": {
                          "post": {
                            "requestBody": {
                              "content": {
                                "application/json": {
                                  "schema": {
                                    "required": ["email"],
                                    "properties": {
                                      "email": { "type": "string" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """;
            SubdomainSpec spec = createSpec("user-service", "dev", specJson);
            given(subdomainSpecRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(spec));

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then — ai-generate는 body 검증 스킵이므로 VALID
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.VALID);
            assertThat(result.issues()).isEmpty();
        }

        @Test
        @DisplayName("정상: stepsJson이 null이면 VALID (파싱 실패 시)")
        void success_null_steps_json_returns_valid() {
            // given
            Recipe recipe = createRecipe(1L, null);

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.VALID);
            assertThat(result.issues()).isEmpty();
        }

        @Test
        @DisplayName("정상: subdomain 정보 없는 step은 스킵")
        void success_step_without_subdomain_is_skipped() {
            // given
            String stepsJson = """
                    [
                      {
                        "method": "POST",
                        "path": "/api/something"
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);

            // when
            RecipeValidationResult result = recipeSpecValidator.validate(recipe);

            // then
            assertThat(result.status()).isEqualTo(RecipeValidationStatus.VALID);
            assertThat(result.issues()).isEmpty();
        }
    }

    @Nested
    @DisplayName("validateAllForSubdomain")
    class ValidateAllForSubdomain {

        @Test
        @DisplayName("정상: 해당 서브도메인을 참조하는 레시피들의 검증 상태가 갱신됨")
        void success_updates_validation_for_referencing_recipes() {
            // given
            String stepsJson = """
                    [
                      {
                        "subdomain": "user-service",
                        "environment": "dev",
                        "method": "POST",
                        "path": "/api/members",
                        "bodyStrategy": "fixed",
                        "body": { "email": "test@test.com" }
                      }
                    ]
                    """;
            Recipe recipe = createRecipe(1L, stepsJson);
            given(recipeRepository.findByStepsJsonContaining("user-service"))
                    .willReturn(List.of(recipe));

            String specJson = """
                    {
                      "paths": {
                        "/api/members": {
                          "post": {
                            "requestBody": {
                              "content": {
                                "application/json": {
                                  "schema": {
                                    "required": ["email"],
                                    "properties": {
                                      "email": { "type": "string" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """;
            SubdomainSpec spec = createSpec("user-service", "dev", specJson);
            given(subdomainSpecRepository.findByNameAndEnvironment("user-service", "dev"))
                    .willReturn(Optional.of(spec));

            // when
            recipeSpecValidator.validateAllForSubdomain("user-service", "dev");

            // then
            assertThat(recipe.getValidationStatus()).isEqualTo(RecipeValidationStatus.VALID);
        }

        @Test
        @DisplayName("정상: 해당 서브도메인을 참조하는 레시피가 없으면 아무것도 하지 않음")
        void success_no_recipes_referencing_subdomain() {
            // given
            given(recipeRepository.findByStepsJsonContaining("empty-service"))
                    .willReturn(List.of());

            // when
            recipeSpecValidator.validateAllForSubdomain("empty-service", "dev");

            // then
            then(subdomainSpecRepository).shouldHaveNoInteractions();
        }
    }

    // === Helper Methods ===

    private Recipe createRecipe(Long id, String stepsJson) {
        Recipe recipe = Recipe.builder()
                .userId(1L)
                .name("테스트 레시피")
                .stepsJson(stepsJson)
                .build();
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }

    private SubdomainSpec createSpec(String name, String environment, String specJson) {
        SubdomainSpec spec = SubdomainSpec.builder()
                .name(name)
                .environment(environment)
                .baseUrl("http://" + name + ":8080")
                .specJson(specJson)
                .build();
        ReflectionTestUtils.setField(spec, "id", 1L);
        return spec;
    }
}
