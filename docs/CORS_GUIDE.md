# 서브도메인 CORS 설정 가이드

AI Test Forge는 **브라우저(FE)가 서브도메인 API를 직접 호출**하는 구조입니다.
이를 위해 서브도메인 서버에서 CORS를 허용해야 합니다.

## 왜 CORS가 필요한가?

```
[브라우저]
  ├── AI Test Forge UI (https://test-forge.company.com)
  │     ↓ fetch (cross-origin)
  └── 서브도메인 API (https://user.dev.company.com)
```

브라우저의 동일 출처 정책(Same-Origin Policy)에 의해,
AI Test Forge 도메인에서 서브도메인 API로의 요청은 CORS 허용 없이 차단됩니다.

## 설정 방법

### Spring Boot (Java)

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://test-forge.company.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)  // 쿠키/세션 전달 시 필수
                .maxAge(3600);
    }
}
```

### Express (Node.js)

```javascript
const cors = require('cors');

app.use(cors({
  origin: 'https://test-forge.company.com',
  credentials: true,  // 쿠키 전달 시 필수
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
}));
```

### Django (Python)

```python
# settings.py
INSTALLED_APPS = [
    ...
    'corsheaders',
]

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    ...
]

CORS_ALLOWED_ORIGINS = [
    'https://test-forge.company.com',
]
CORS_ALLOW_CREDENTIALS = True
```

### Laravel (PHP)

```php
// config/cors.php
return [
    'paths' => ['api/*'],
    'allowed_origins' => ['https://test-forge.company.com'],
    'allowed_methods' => ['*'],
    'allowed_headers' => ['*'],
    'supports_credentials' => true,
];
```

## 로컬 개발 환경

로컬 개발 시 AI Test Forge UI는 `http://localhost:5173`에서 실행됩니다.

```java
// 로컬 개발용 — 프로덕션에서는 도메인 제한 필요
.allowedOrigins("http://localhost:5173")
```

## 주의사항

### `credentials: true` 필수 조건

쿠키/세션 기반 인증을 사용하는 서브도메인은 반드시:
1. 서버: `Access-Control-Allow-Credentials: true` 설정
2. 서버: `Access-Control-Allow-Origin`에 와일드카드(`*`) 사용 불가 — 정확한 도메인 명시
3. 클라이언트(FE): `fetch(..., { credentials: 'include' })` 사용

### SSO 환경

같은 도메인 쿠키를 공유하는 SSO 환경에서는:
- 한 번 로그인하면 모든 서브도메인에서 쿠키가 자동 부착됨
- 각 서브도메인은 AI Test Forge 도메인에서의 요청만 허용하면 됨

### 보안 권장사항

- `allowedOrigins`에 AI Test Forge 도메인만 명시 (와일드카드 금지)
- dev/qa 환경에서만 CORS 설정 활성화 (프로필 기반 제어 권장)
- 민감한 엔드포인트는 추가 인증 검증 유지

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `403 Forbidden` (preflight) | CORS 설정 누락 | `Access-Control-Allow-Origin` 헤더 확인 |
| 쿠키가 전달되지 않음 | `credentials: true` 미설정 | 서버 + 클라이언트 양쪽 설정 확인 |
| `Origin 'null'` 에러 | 파일 프로토콜에서 접근 | HTTP 서버로 접근 필요 |
| 401 발생 | 서브도메인 세션 만료 | 로그인 페이지 링크 따라 재로그인 |
