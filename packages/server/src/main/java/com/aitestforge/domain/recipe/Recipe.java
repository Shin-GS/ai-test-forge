package com.aitestforge.domain.recipe;

import com.aitestforge.domain.recipe.enums.RecipeValidationStatus;
import com.aitestforge.domain.recipe.enums.RecipeVisibility;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "RECIPE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @ElementCollection
    @CollectionTable(name = "RECIPE_TAG", joinColumns = @JoinColumn(name = "RECIPE_ID"))
    @Column(name = "TAG")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Lob
    @Column(name = "STEPS_JSON", columnDefinition = "LONGTEXT", nullable = false)
    private String stepsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "VISIBILITY", nullable = false)
    @Builder.Default
    private RecipeVisibility visibility = RecipeVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "VALIDATION_STATUS")
    private RecipeValidationStatus validationStatus;

    @Column(name = "VALIDATION_MESSAGE")
    private String validationMessage;

    @Lob
    @Column(name = "VARIABLES_JSON", columnDefinition = "TEXT")
    private String variablesJson;

    @Column(name = "USAGE_COUNT", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "LAST_USED_AT")
    private LocalDateTime lastUsedAt;

    public void incrementUsage() {
        this.usageCount++;
        this.lastUsedAt = LocalDateTime.now();
    }

    public void update(String name, String description, List<String> tags, String stepsJson,
                       RecipeVisibility visibility, String variablesJson) {
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.stepsJson = stepsJson;
        this.visibility = visibility;
        this.variablesJson = variablesJson;
    }

    public void updateValidation(RecipeValidationStatus validationStatus, String validationMessage) {
        this.validationStatus = validationStatus;
        this.validationMessage = validationMessage;
    }
}
