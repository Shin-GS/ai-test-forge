---
inclusion: fileMatch
fileMatchPattern: ["packages/server/**/*.java", "packages/server/**/*.yml", "packages/server/**/*.kts", "packages/client-spring/**/*.java", "packages/client-spring/**/*.kts"]
---

# Backend 개발 규칙

## 1. 언어
- 에이전트 응답은 한국어로 작성한다.
- 코드(클래스명, 변수명, 메서드명, API 경로, 에러 메시지)는 모두 영어로 작성한다.
- 코드 주석은 한국어 허용 (개발자 간 소통 목적).

## 2. 정확성
- 확실하지 않은 내용은 추측하지 않고 모른다고 명확히 답변한다.

## 3. 변경 범위
- 요청받은 부분만 수정한다.
- 명시적으로 언급되지 않은 코드는 변경하지 않는다.

## 4. Lombok 규칙

**왜**: 불필요한 보일러플레이트를 줄이되, 명시적으로 어떤 기능을 사용하는지 드러내기 위함.

- `@Data` 사용 지양 — equals/hashCode 자동 생성이 JPA Entity에서 문제를 일으킬 수 있음
- `@Setter` 대신 `@Builder` 사용 — 불변성 유지, 필요시 `@Setter` 허용
- 권장: `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, `@Slf4j`

## 5. Entity 패턴

**왜**: JPA 프록시 생성을 위해 protected no-arg 생성자 필요, Builder로 명시적 객체 생성.

```java
@Getter
@Entity
@Table(name = "TABLE_NAME")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EntityName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "COLUMN_NAME", nullable = false)
    private String fieldName;
}
```

## 6. Service 패턴

**왜**: 클래스 레벨 readOnly로 기본 읽기 최적화, 쓰기 메서드만 @Transactional 추가.

```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SomeService {

    private final SomeRepository someRepository;

    @Transactional
    public void create(CreateRequest request) {
        // 비즈니스 로직
    }
}
```

## 7. Controller 패턴

```java
@Tag(name = "Resource", description = "Resource management")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    @Operation(summary = "리소스 목록 조회", description = "페이지네이션된 리소스 목록을 반환합니다.")
    @GetMapping
    public ResponseEntity<PageResponse<ResourceResponse>> getAll(
            @Parameter(description = "페이지 번호 (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(resourceService.getAll(page, size));
    }
}
```

### Swagger 어노테이션 규칙
- 클래스 레벨: `@Tag(name, description)` 필수
- 메서드 레벨: `@Operation(summary, description)` 필수
- 파라미터: `@Parameter(description)` — `@RequestParam`, `@PathVariable`에 추가

## 8. DTO 패턴

**왜**: record는 불변, 자동 생성자/getter/equals/hashCode 제공, Jackson 역직렬화 지원.

```java
public record ChallengeResponse(
    Long id,
    String title,
    String description,
    LocalDateTime createdAt
) {}
```

- 응답: `{Name}Response` 접미사
- 요청: `{Name}Request` 접미사
- Entity를 API request/response에 직접 사용 금지 — 반드시 DTO 분리

## 9. 예외 처리
- 비즈니스 예외는 커스텀 Exception 클래스 사용
- `@RestControllerAdvice`로 글로벌 예외 핸들링
- HTTP 상태 코드를 의미에 맞게 사용 (404, 409, 400 등)

## 10. 피해야 할 패턴
- `@Enumerated(EnumType.ORDINAL)` — enum 순서 변경 시 데이터 깨짐
- Entity에서 DTO 직접 참조 — 레이어 의존성 역전
- Service에서 HttpServletRequest/Response 직접 사용 — 테스트 어려움
- 비즈니스 로직을 Controller에 작성 — 재사용/테스트 불가
