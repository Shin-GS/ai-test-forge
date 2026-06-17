package com.aitestforge.service.recipe;

import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.enums.RecipeVisibility;
import com.aitestforge.dto.recipe.response.RecipeResponse;
import com.aitestforge.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 사용자의 요청 텍스트와 유사한 레시피를 검색하는 서비스.
 * name, description, tags 기반으로 키워드 매칭 점수를 계산하여 상위 결과를 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeSuggestionService {

    private static final int NAME_MATCH_SCORE = 3;
    private static final int DESCRIPTION_MATCH_SCORE = 2;
    private static final int TAG_MATCH_SCORE = 2;

    private final RecipeRepository recipeRepository;

    /**
     * 사용자의 요청 텍스트와 유사한 레시피를 검색한다.
     * name, description, tags 기반으로 매칭.
     *
     * @param query      사용자의 요청 텍스트
     * @param maxResults 최대 반환 건수 (기본 3)
     * @return 유사한 레시피 목록 (매칭 점수 → 사용 빈도 순)
     */
    public List<RecipeResponse> suggest(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> keywords = tokenize(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<Recipe> publicRecipes = recipeRepository.findByVisibilityOrderByUsageCountDesc(RecipeVisibility.PUBLIC);

        return publicRecipes.stream()
                .map(recipe -> new ScoredRecipe(recipe, calculateScore(recipe, keywords)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredRecipe::score).reversed()
                        .thenComparing(scored -> scored.recipe().getUsageCount(), Comparator.reverseOrder()))
                .limit(maxResults)
                .map(scored -> RecipeResponse.from(scored.recipe()))
                .collect(Collectors.toList());
    }

    /**
     * query를 공백 기준으로 토큰화한다.
     * 빈 문자열 토큰은 제거한다.
     */
    private List<String> tokenize(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    /**
     * 레시피와 키워드 목록 간의 매칭 점수를 계산한다.
     * - name 매칭: +3점/키워드
     * - description 매칭: +2점/키워드
     * - tag 매칭: +2점/키워드
     */
    private int calculateScore(Recipe recipe, List<String> keywords) {
        int score = 0;
        String nameLower = recipe.getName() != null ? recipe.getName().toLowerCase() : "";
        String descLower = recipe.getDescription() != null ? recipe.getDescription().toLowerCase() : "";
        List<String> tagsLower = recipe.getTags() != null
                ? recipe.getTags().stream().map(String::toLowerCase).toList()
                : List.of();

        for (String keyword : keywords) {
            if (nameLower.contains(keyword)) {
                score += NAME_MATCH_SCORE;
            }
            if (descLower.contains(keyword)) {
                score += DESCRIPTION_MATCH_SCORE;
            }
            if (tagsLower.stream().anyMatch(tag -> tag.contains(keyword))) {
                score += TAG_MATCH_SCORE;
            }
        }

        return score;
    }

    /**
     * 점수가 매겨진 레시피를 표현하는 내부 record.
     */
    private record ScoredRecipe(Recipe recipe, int score) {
    }
}
