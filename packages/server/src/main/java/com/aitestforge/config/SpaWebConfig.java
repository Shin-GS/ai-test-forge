package com.aitestforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA(React) 라우팅 지원 설정.
 * /api, /health 등 서버 경로 외의 모든 요청은 index.html로 포워딩하여
 * React Router가 클라이언트 라우팅을 처리하도록 함.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // API, actuator, swagger 경로는 SPA fallback 제외
                        if (resourcePath.startsWith("api/") ||
                                resourcePath.startsWith("v3/") ||
                                resourcePath.startsWith("swagger-ui/") ||
                                resourcePath.equals("health")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        // 실제 파일이 있으면 그 파일 반환, 없으면 index.html (SPA fallback)
                        return requested.exists() && requested.isReadable()
                                ? requested
                                : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
