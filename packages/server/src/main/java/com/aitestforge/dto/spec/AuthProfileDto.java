package com.aitestforge.dto.spec;

/**
 * 서브도메인 인증 프로필 메타 정보.
 * 서브도메인이 push 시 전달하는 로그인 방식 정보를 담는다.
 */
public record AuthProfileDto(
        String name,
        String loginPageUrl
) {}
