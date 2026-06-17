package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.enums.RecipeVisibility;
import com.aitestforge.dto.recipe.request.CreateRecipeRequest;
import com.aitestforge.dto.recipe.request.UpdateRecipeRequest;
import com.aitestforge.dto.recipe.response.RecipeResponse;
import com.aitestforge.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;

    @Transactional
    public RecipeResponse create(CreateRecipeRequest request, Long userId) {
        RecipeVisibility visibility = request.visibility() != null
                ? request.visibility()
                : RecipeVisibility.PUBLIC;

        Recipe recipe = Recipe.builder()
                .userId(userId)
                .name(request.name())
                .description(request.description())
                .tags(request.tags() != null ? request.tags() : List.of())
                .stepsJson(request.stepsJson())
                .visibility(visibility)
                .variablesJson(request.variablesJson())
                .build();

        recipeRepository.save(recipe);
        log.info("Recipe created: {} for user {}", recipe.getName(), userId);
        return RecipeResponse.from(recipe);
    }

    public List<RecipeResponse> getAll(Long userId) {
        return recipeRepository.findByUserIdOrVisibilityOrderByUsageCountDesc(userId, RecipeVisibility.PUBLIC).stream()
                .map(RecipeResponse::from)
                .toList();
    }

    public RecipeResponse getById(Long recipeId) {
        Recipe recipe = findOrThrow(recipeId);
        return RecipeResponse.from(recipe);
    }

    @Transactional
    public RecipeResponse update(Long recipeId, UpdateRecipeRequest request, Long userId) {
        Recipe recipe = findOrThrow(recipeId);
        verifyOwner(recipe, userId);

        RecipeVisibility visibility = request.visibility() != null
                ? request.visibility()
                : recipe.getVisibility();

        recipe.update(
                request.name(),
                request.description(),
                request.tags() != null ? request.tags() : List.of(),
                request.stepsJson(),
                visibility,
                request.variablesJson()
        );
        log.info("Recipe updated: {}", recipe.getName());
        return RecipeResponse.from(recipe);
    }

    @Transactional
    public void delete(Long recipeId, Long userId) {
        Recipe recipe = findOrThrow(recipeId);
        verifyOwner(recipe, userId);
        recipeRepository.delete(recipe);
        log.info("Recipe deleted: {}", recipe.getName());
    }

    /**
     * 레시피를 복제하여 새 owner로 저장한다.
     * 원본 레시피의 이름 뒤에 " (사본)"을 붙여 구분한다.
     * PUBLIC 레시피는 누구나, PRIVATE 레시피는 본인만 복제 가능.
     */
    @Transactional
    public RecipeResponse clone(Long recipeId, Long userId) {
        Recipe original = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // PRIVATE 레시피는 본인만 복제 가능
        if (original.getVisibility() == RecipeVisibility.PRIVATE && !original.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Recipe cloned = Recipe.builder()
                .userId(userId)
                .name(original.getName() + " (사본)")
                .description(original.getDescription())
                .tags(original.getTags() != null ? List.copyOf(original.getTags()) : List.of())
                .stepsJson(original.getStepsJson())
                .visibility(RecipeVisibility.PRIVATE)
                .variablesJson(original.getVariablesJson())
                .build();

        recipeRepository.save(cloned);
        log.info("Recipe cloned: {} -> {} for user {}", original.getName(), cloned.getName(), userId);
        return RecipeResponse.from(cloned);
    }

    @Transactional
    public void incrementUsage(Long recipeId) {
        Recipe recipe = findOrThrow(recipeId);
        recipe.incrementUsage();
    }

    private Recipe findOrThrow(Long recipeId) {
        return recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void verifyOwner(Recipe recipe, Long userId) {
        if (!recipe.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 엔티티 직접 반환 (내부 서비스 간 참조용).
     */
    public Recipe getRecipeEntity(Long recipeId) {
        return findOrThrow(recipeId);
    }
}
