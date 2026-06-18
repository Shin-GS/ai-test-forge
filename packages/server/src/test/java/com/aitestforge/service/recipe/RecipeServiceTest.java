package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.enums.RecipeVisibility;
import com.aitestforge.dto.recipe.request.CreateRecipeRequest;
import com.aitestforge.dto.recipe.request.UpdateRecipeRequest;
import com.aitestforge.dto.recipe.response.RecipeResponse;
import com.aitestforge.repository.RecipeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;

    @InjectMocks
    private RecipeService recipeService;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("정상: 레시피 생성 성공 (visibility 지정)")
        void success_create_recipe_with_visibility() {
            // given
            CreateRecipeRequest request = new CreateRecipeRequest(
                    "입사지원 데이터", "입사지원용 테스트 데이터 생성",
                    List.of("채용", "회원"), "[{\"subdomain\":\"user-service\"}]",
                    RecipeVisibility.PUBLIC, null
            );
            given(recipeRepository.save(any(Recipe.class)))
                    .willAnswer(invocation -> {
                        Recipe recipe = invocation.getArgument(0);
                        ReflectionTestUtils.setField(recipe, "id", 1L);
                        return recipe;
                    });

            // when
            RecipeResponse response = recipeService.create(request, 100L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("입사지원 데이터");
            assertThat(response.visibility()).isEqualTo(RecipeVisibility.PUBLIC);
            then(recipeRepository).should().save(any(Recipe.class));
        }

        @Test
        @DisplayName("정상: visibility 미지정 시 기본값 PUBLIC")
        void success_create_recipe_default_visibility_is_public() {
            // given
            CreateRecipeRequest request = new CreateRecipeRequest(
                    "회원 생성", null, null, "[{}]", null, null
            );
            given(recipeRepository.save(any(Recipe.class)))
                    .willAnswer(invocation -> {
                        Recipe recipe = invocation.getArgument(0);
                        ReflectionTestUtils.setField(recipe, "id", 2L);
                        return recipe;
                    });

            // when
            RecipeResponse response = recipeService.create(request, 100L);

            // then
            assertThat(response.visibility()).isEqualTo(RecipeVisibility.PUBLIC);
        }

        @Test
        @DisplayName("정상: tags가 null이면 빈 리스트로 저장")
        void success_create_recipe_with_null_tags() {
            // given
            CreateRecipeRequest request = new CreateRecipeRequest(
                    "태그 없는 레시피", null, null, "[{}]",
                    RecipeVisibility.PRIVATE, null
            );
            given(recipeRepository.save(any(Recipe.class)))
                    .willAnswer(invocation -> {
                        Recipe recipe = invocation.getArgument(0);
                        ReflectionTestUtils.setField(recipe, "id", 3L);
                        return recipe;
                    });

            // when
            RecipeResponse response = recipeService.create(request, 100L);

            // then
            assertThat(response.tags()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("정상: 본인 레시피 + PUBLIC 레시피 목록 반환")
        void success_returns_user_and_public_recipes() {
            // given
            Recipe recipe1 = createRecipe(1L, 100L, "내 레시피", RecipeVisibility.PRIVATE);
            Recipe recipe2 = createRecipe(2L, 200L, "공용 레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findByUserIdOrVisibilityOrderByUsageCountDesc(100L, RecipeVisibility.PUBLIC))
                    .willReturn(List.of(recipe1, recipe2));

            // when
            List<RecipeResponse> responses = recipeService.getAll(100L);

            // then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).name()).isEqualTo("내 레시피");
            assertThat(responses.get(1).name()).isEqualTo("공용 레시피");
        }

        @Test
        @DisplayName("정상: 레시피가 없으면 빈 리스트 반환")
        void success_returns_empty_list_when_no_recipes() {
            // given
            given(recipeRepository.findByUserIdOrVisibilityOrderByUsageCountDesc(100L, RecipeVisibility.PUBLIC))
                    .willReturn(List.of());

            // when
            List<RecipeResponse> responses = recipeService.getAll(100L);

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("정상: ID로 레시피 조회 성공")
        void success_get_recipe_by_id() {
            // given
            Recipe recipe = createRecipe(1L, 100L, "테스트 레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(recipe));

            // when
            RecipeResponse response = recipeService.getById(1L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("테스트 레시피");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 ID로 조회 시 RESOURCE_NOT_FOUND")
        void fail_recipe_not_found_throws_exception() {
            // given
            given(recipeRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> recipeService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("정상: 레시피 수정 성공 (owner 일치)")
        void success_update_recipe_by_owner() {
            // given
            Recipe recipe = createRecipe(1L, 100L, "원래 이름", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(recipe));

            UpdateRecipeRequest request = new UpdateRecipeRequest(
                    "수정된 이름", "수정된 설명", List.of("새태그"),
                    "[{\"new\":\"steps\"}]", RecipeVisibility.PRIVATE, null
            );

            // when
            RecipeResponse response = recipeService.update(1L, request, 100L);

            // then
            assertThat(response.name()).isEqualTo("수정된 이름");
            assertThat(response.visibility()).isEqualTo(RecipeVisibility.PRIVATE);
        }

        @Test
        @DisplayName("실패: 다른 사용자가 수정 시도하면 FORBIDDEN")
        void fail_update_by_non_owner_throws_forbidden() {
            // given
            Recipe recipe = createRecipe(1L, 100L, "타인 레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(recipe));

            UpdateRecipeRequest request = new UpdateRecipeRequest(
                    "수정 시도", null, null, "[{}]", null, null
            );

            // when & then
            assertThatThrownBy(() -> recipeService.update(1L, request, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("정상: visibility가 null이면 기존 값 유지")
        void success_update_with_null_visibility_keeps_original() {
            // given
            Recipe recipe = createRecipe(1L, 100L, "레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(recipe));

            UpdateRecipeRequest request = new UpdateRecipeRequest(
                    "수정된 이름", null, null, "[{}]", null, null
            );

            // when
            RecipeResponse response = recipeService.update(1L, request, 100L);

            // then — visibility는 기존 PUBLIC 유지
            assertThat(response.visibility()).isEqualTo(RecipeVisibility.PUBLIC);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("정상: owner가 레시피 삭제 성공")
        void success_delete_recipe_by_owner() {
            // given
            Recipe recipe = createRecipe(1L, 100L, "삭제할 레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(recipe));

            // when
            recipeService.delete(1L, 100L);

            // then
            then(recipeRepository).should().delete(recipe);
        }

        @Test
        @DisplayName("실패: 다른 사용자가 삭제 시도하면 FORBIDDEN")
        void fail_delete_by_non_owner_throws_forbidden() {
            // given
            Recipe recipe = createRecipe(1L, 100L, "타인 레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(recipe));

            // when & then
            assertThatThrownBy(() -> recipeService.delete(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 레시피 삭제 시 RESOURCE_NOT_FOUND")
        void fail_delete_nonexistent_recipe_throws_not_found() {
            // given
            given(recipeRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> recipeService.delete(999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("clone")
    class Clone {

        @Test
        @DisplayName("정상: PUBLIC 레시피를 다른 사용자가 복제 성공")
        void success_clone_public_recipe_by_another_user() {
            // given
            Recipe original = createRecipe(1L, 100L, "원본 레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(original));
            given(recipeRepository.save(any(Recipe.class)))
                    .willAnswer(invocation -> {
                        Recipe cloned = invocation.getArgument(0);
                        ReflectionTestUtils.setField(cloned, "id", 2L);
                        return cloned;
                    });

            // when
            RecipeResponse response = recipeService.clone(1L, 200L);

            // then
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.name()).isEqualTo("원본 레시피 (사본)");
            assertThat(response.visibility()).isEqualTo(RecipeVisibility.PRIVATE);
            then(recipeRepository).should().save(any(Recipe.class));
        }

        @Test
        @DisplayName("정상: PRIVATE 레시피를 본인이 복제 성공")
        void success_clone_private_recipe_by_owner() {
            // given
            Recipe original = createRecipe(1L, 100L, "비공개 레시피", RecipeVisibility.PRIVATE);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(original));
            given(recipeRepository.save(any(Recipe.class)))
                    .willAnswer(invocation -> {
                        Recipe cloned = invocation.getArgument(0);
                        ReflectionTestUtils.setField(cloned, "id", 2L);
                        return cloned;
                    });

            // when
            RecipeResponse response = recipeService.clone(1L, 100L);

            // then
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.name()).isEqualTo("비공개 레시피 (사본)");
        }

        @Test
        @DisplayName("실패: PRIVATE 레시피를 다른 사용자가 복제 시도하면 RESOURCE_NOT_FOUND")
        void fail_clone_private_recipe_by_other_user_throws_not_found() {
            // given
            Recipe original = createRecipe(1L, 100L, "비공개 레시피", RecipeVisibility.PRIVATE);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(original));

            // when & then
            assertThatThrownBy(() -> recipeService.clone(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 레시피 복제 시 RESOURCE_NOT_FOUND")
        void fail_clone_nonexistent_recipe_throws_not_found() {
            // given
            given(recipeRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> recipeService.clone(999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("incrementUsage")
    class IncrementUsage {

        @Test
        @DisplayName("정상: 사용 횟수 증가")
        void success_increment_usage_count() {
            // given
            Recipe recipe = createRecipe(1L, 100L, "레시피", RecipeVisibility.PUBLIC);
            given(recipeRepository.findById(1L)).willReturn(Optional.of(recipe));

            // when
            recipeService.incrementUsage(1L);

            // then
            assertThat(recipe.getUsageCount()).isEqualTo(1);
            assertThat(recipe.getLastUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 레시피 사용 시 RESOURCE_NOT_FOUND")
        void fail_increment_nonexistent_recipe_throws_not_found() {
            // given
            given(recipeRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> recipeService.incrementUsage(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    // === Helper Methods ===

    private Recipe createRecipe(Long id, Long userId, String name, RecipeVisibility visibility) {
        Recipe recipe = Recipe.builder()
                .userId(userId)
                .name(name)
                .description("테스트 설명")
                .tags(List.of("test"))
                .stepsJson("[{\"subdomain\":\"user-service\"}]")
                .visibility(visibility)
                .build();
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }
}
