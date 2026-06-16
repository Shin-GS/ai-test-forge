package com.aitestforge.infra.ai.dto;

public record ToolDefinition(String name, String description, String parametersJson, ToolControl control) {

    /**
     * 기존 코드 호환을 위한 편의 생성자 — control 없이 생성 시 ToolControl.none() 적용.
     */
    public ToolDefinition(String name, String description, String parametersJson) {
        this(name, description, parametersJson, ToolControl.none());
    }
}
