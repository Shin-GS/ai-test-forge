---
inclusion: fileMatch
fileMatchPattern: ["packages/server/src/test/**/*.java", "packages/client-spring/src/test/**/*.java"]
---

# BE 테스트 규칙

## 테스트 방식

- **순수 단위 테스트** (Spring 컨텍스트 로딩 없음)
- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- Repository, Infra 서비스는 전부 Mock
- 비즈니스 로직 검증에 집중

## 의존성

- JUnit 5 (`spring-boot-starter-test` 포함)
- Mockito (BDDMockito 스타일)
- AssertJ

## 파일 구조

```
src/test/java/com/aitestforge/
├── service/
│   ├── chat/            # ChatService 등
│   ├── agent/           # AgentLoopService 등
│   ├── spec/            # SpecRegistryService 등
│   └── tool/            # ToolExecutionService 등
└── common/
    └── util/            # 유틸리티 테스트
```

## 코드 컨벤션

### 클래스 구조

```java
@ExtendWith(MockitoExtension.class)
class XxxServiceTest {

    @Mock
    private SomeRepository someRepository;

    @InjectMocks
    private XxxService xxxService;

    @Nested
    @DisplayName("메서드명")
    class MethodName {

        @Test
        @DisplayName("정상: 설명")
        void success_description() {
            // given

            // when

            // then
        }

        @Test
        @DisplayName("실패: 설명")
        void fail_description() {
            // given

            // when & then
        }
    }

    // === Helper Methods ===
    private Xxx createXxx(...) { ... }
}
```

### 네이밍

| 항목 | 규칙 |
|------|------|
| 테스트 클래스 | `{서비스명}Test` |
| @Nested 클래스 | 메서드명 (PascalCase) |
| @DisplayName | 한국어 (정상/실패 접두사) |
| 메서드명 | 영문 snake_case (`success_xxx`, `fail_xxx`) |

### 검증 스타일

- AssertJ: `assertThat(actual).isEqualTo(expected)`
- 예외: `assertThatThrownBy(() -> ...).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.XXX)`
- Mock 호출 검증: `then(repository).should().save(any())` (BDDMockito)
- Mock 호출 안 됨 검증: `then(repository).should(never()).save(any())`

### 주의사항

- `@SpringBootTest` 사용 금지 (단위 테스트만)
- 실제 DB 접근 금지 (Repository는 항상 Mock)
- 테스트 간 상태 공유 금지 (각 테스트 독립 실행)

## 실행

```bash
cd packages/server && ./gradlew test
cd packages/client-spring && ./gradlew test
```
