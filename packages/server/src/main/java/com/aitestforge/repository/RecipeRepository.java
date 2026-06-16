package com.aitestforge.repository;

import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.RecipeVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByUserIdOrderByUsageCountDesc(Long userId);

    List<Recipe> findByUserIdAndNameContainingIgnoreCaseOrderByUsageCountDesc(Long userId, String name);

    List<Recipe> findByVisibilityOrderByUsageCountDesc(RecipeVisibility visibility);

    List<Recipe> findByUserIdOrVisibilityOrderByUsageCountDesc(Long userId, RecipeVisibility visibility);

    @Query("SELECT r FROM Recipe r WHERE r.stepsJson LIKE %:subdomain%")
    List<Recipe> findByStepsJsonContaining(@Param("subdomain") String subdomain);
}
