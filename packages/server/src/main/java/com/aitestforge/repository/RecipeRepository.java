package com.aitestforge.repository;

import com.aitestforge.domain.recipe.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByUserIdOrderByUsageCountDesc(Long userId);

    List<Recipe> findByUserIdAndNameContainingIgnoreCaseOrderByUsageCountDesc(Long userId, String name);
}
