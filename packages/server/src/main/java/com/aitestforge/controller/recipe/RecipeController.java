package com.aitestforge.controller.recipe;

import com.aitestforge.domain.auth.User;
import com.aitestforge.dto.recipe.CreateRecipeRequest;
import com.aitestforge.dto.recipe.RecipeResponse;
import com.aitestforge.dto.recipe.UpdateRecipeRequest;
import com.aitestforge.service.recipe.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Recipe", description = "레시피 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    @Operation(summary = "레시피 생성", description = "새 레시피를 생성합니다.")
    @PostMapping
    public ResponseEntity<RecipeResponse> create(
            @Valid @RequestBody CreateRecipeRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recipeService.create(request, user.getId()));
    }

    @Operation(summary = "레시피 목록 조회", description = "현재 사용자의 레시피를 사용 횟수 순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getAll(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recipeService.getAll(user.getId()));
    }

    @Operation(summary = "레시피 상세 조회", description = "특정 레시피의 상세 정보를 조회합니다.")
    @GetMapping("/{recipeId}")
    public ResponseEntity<RecipeResponse> getById(
            @Parameter(description = "레시피 ID") @PathVariable Long recipeId) {
        return ResponseEntity.ok(recipeService.getById(recipeId));
    }

    @Operation(summary = "레시피 수정", description = "레시피의 이름, 설명, 태그, 단계를 수정합니다.")
    @PutMapping("/{recipeId}")
    public ResponseEntity<RecipeResponse> update(
            @Parameter(description = "레시피 ID") @PathVariable Long recipeId,
            @Valid @RequestBody UpdateRecipeRequest request) {
        return ResponseEntity.ok(recipeService.update(recipeId, request));
    }

    @Operation(summary = "레시피 삭제", description = "레시피를 삭제합니다.")
    @DeleteMapping("/{recipeId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "레시피 ID") @PathVariable Long recipeId) {
        recipeService.delete(recipeId);
        return ResponseEntity.noContent().build();
    }
}
