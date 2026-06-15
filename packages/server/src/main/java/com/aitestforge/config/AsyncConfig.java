package com.aitestforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 비동기 처리 및 스케줄링 설정.
 * Virtual Threads가 활성화되어 있으므로 별도의 ThreadPoolTaskExecutor 설정 없이
 * @Async 메서드 실행 시 자동으로 Virtual Threads를 사용한다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
