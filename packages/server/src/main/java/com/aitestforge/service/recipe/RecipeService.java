package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.dto.recipe.CreateRecipeRequest;
import com.aitestforge.dto.recipe.RecipeResponse;
import com.aitestforge.dto.recipe.UpdateRecipeRequest;
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
        Recipe recipe = Recipe.builder()
                .userId(userId)
                .name(request.name())
                .description(request.description())
                .tags(request.tags() != null ? request.tags() : List.of())
                .stepsJson(request.stepsJson())
                .build();

        recipeRepository.save(recipe);
        log.info("Recipe created: {} for user {}", recipe.getName(), userId);
        return RecipeResponse.from(recipe);
    }

    public List<RecipeResponse> getAll(Long userId) {
        return recipeRepository.findByUserIdOrderByUsageCountDesc(userId).stream()
                .map(RecipeResponse::from)
                .toList();
    }

    public RecipeResponse getById(Long recipeId) {
        Recipe recipe = findOrThrow(recipeId);
        return RecipeResponse.from(recipe);
    }

    @Transactional
    public RecipeResponse update(Long recipeId, UpdateRecipeRequest request) {
        Recipe recipe = findOrThrow(recipeId);
        recipe.update(
                request.name(),
                request.description(),
                request.tags() != null ? request.tags() : List.of(),
                request.stepsJson()
        );
        log.info("Recipe updated: {}", recipe.getName());
        return RecipeResponse.from(recipe);
    }

    @Transactional
    public void delete(Long recipeId) {
        Recipe recipe = findOrThrow(recipeId);
        recipeRepository.delete(recipe);
        log.info("Recipe deleted: {}", recipe.getName());
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
}
