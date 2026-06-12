# QA Test Checklist

수동 테스트 체크리스트.

## 사용법

1. `docs/test/index.html`을 브라우저에서 열기
2. Feature 드롭다운에서 기능 선택
3. 각 케이스의 Pass / Fail / Skip 버튼 클릭
4. 상태는 localStorage에 자동 저장

## 테스트 현황

| 기능 | 파일 | 우선순위 | 케이스 수 | 마지막 업데이트 |
|------|------|---------|----------|--------------|
| (아직 없음) | | | | |

## 우선순위 가이드

- **critical**: 배포 전 반드시 전체 통과 (핵심 플로우)
- **high**: 관련 기능 변경 시 필수 확인
- **medium**: 주기적 확인
- **low**: 대규모 변경 시에만

## 파일 구조

```
docs/test/
├── index.html                 # 통합 뷰어
├── README.md                  # 이 파일
├── shared/
│   ├── test-base.css          # 공통 스타일
│   ├── test-manifest.js       # 파일 목록
│   └── test-renderer.js       # 렌더링 엔진
└── web/                       # 기능별 테스트 데이터
    └── {기능}.js
```
