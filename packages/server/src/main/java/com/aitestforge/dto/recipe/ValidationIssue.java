package com.aitestforge.dto.recipe;

public record ValidationIssue(
        int stepIndex,
        String stepName,
        ValidationIssueType issueType,
        String description
) {}
