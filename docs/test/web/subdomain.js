export default {
  feature: "서브도메인",
  screen: "서브도메인 등록/관리",
  priority: "high",
  lastUpdated: "2026-06-18",
  cases: [
    // ─── 정상 플로우 ───
    {
      id: "SUB-N-001",
      title: "클라이언트 라이브러리 Push 등록",
      precondition: "Spring Boot 서버에 클라이언트 라이브러리 추가됨",
      steps: [
        "서브도메인 서버 시작",
        "메인 서버 서브도메인 탭에서 목록 확인"
      ],
      expected: "서버 시작 → 메인 서버에 자동 등록, ACTIVE 상태, API 수 표시",
      status: "pending"
    },
    {
      id: "SUB-N-002",
      title: "Heartbeat 유지 확인",
      precondition: "서브도메인 등록 완료 (ACTIVE)",
      steps: [
        "등록 후 30초 이상 대기",
        "상태 변경 없음 확인"
      ],
      expected: "heartbeat 정상 수신, ACTIVE 상태 유지",
      status: "pending"
    },
    {
      id: "SUB-N-003",
      title: "서브도메인 목록 표시 및 검색",
      precondition: "서브도메인 2개 이상 등록",
      steps: [
        "서브도메인 탭 진입",
        "검색바에 서비스명 일부 입력"
      ],
      expected: "카드 목록 표시 (서비스명 + 상태 배지 + 환경 수 + API 수), 검색 필터 동작",
      status: "pending"
    },
    {
      id: "SUB-N-004",
      title: "서브도메인 상세 (API 목록) 확인",
      precondition: "서브도메인 등록됨",
      steps: [
        "목록에서 서브도메인 카드 클릭 → 펼침",
        "환경 클릭하여 상세 페이지 이동"
      ],
      expected: "상태, API 수, 마지막 갱신 시각, base-url 표시, API 목록 태그별 그룹핑",
      status: "pending"
    },
    {
      id: "SUB-N-005",
      title: "수동 업로드 등록 (OpenAPI JSON)",
      precondition: "유효한 OpenAPI JSON 파일 준비",
      steps: [
        "[📄 수동 등록] 버튼 클릭",
        "서브도메인 이름, 환경, Base URL 입력",
        "JSON 파일 선택 후 [등록] 클릭"
      ],
      expected: "등록 성공 → 폼 닫힘 + 목록에 새 서브도메인 표시 (ACTIVE)",
      status: "pending"
    },
    {
      id: "SUB-N-006",
      title: "비동기 등록 (대형 스펙 5MB+)",
      precondition: "5MB 이상 OpenAPI JSON 파일",
      steps: [
        "수동 업로드로 대형 JSON 등록",
        "즉시 응답(202 Accepted) 확인",
        "REGISTERING → ACTIVE 상태 전이 확인"
      ],
      expected: "202 Accepted 즉시 반환, REGISTERING 표시 → 파싱 완료 후 ACTIVE 전환",
      status: "pending"
    },
    {
      id: "SUB-N-007",
      title: "Environment 분리 (동일 서비스, 다른 환경)",
      precondition: "같은 서비스가 dev, feature-login 2개 환경으로 등록",
      steps: [
        "서브도메인 탭에서 환경 필터 확인",
        "동일 서비스의 2개 환경 별도 표시 확인"
      ],
      expected: "name+environment별로 별도 등록 표시, 환경 필터로 각각 조회 가능",
      status: "pending"
    },
    {
      id: "SUB-N-008",
      title: "환경/상태 필터 드롭다운 동작",
      precondition: "다양한 환경/상태의 서브도메인 존재",
      steps: [
        "환경 필터(전체/dev/feature-*/qa) 변경",
        "상태 필터(전체/ACTIVE/STALE) 변경"
      ],
      expected: "선택한 필터 조건에 맞는 서브도메인만 표시",
      status: "pending"
    },
    {
      id: "SUB-N-009",
      title: "'채팅에서 이 서버 사용하기' 버튼",
      precondition: "서브도메인 상세 페이지",
      steps: [
        "[채팅에서 이 서버 사용하기 →] 버튼 클릭"
      ],
      expected: "채팅 탭으로 이동 + 'user-service의 POST /api/members 호출해줘' 자동 입력",
      status: "pending"
    },
    // ─── 예외 플로우 ───
    {
      id: "SUB-E-001",
      title: "Heartbeat 중단 → STALE 표시",
      precondition: "서브도메인 서버 종료",
      steps: [
        "서브도메인 서버 종료 후 5분 대기",
        "서브도메인 탭에서 상태 확인"
      ],
      expected: "⚠️ STALE 배지 표시 + '10분 전 마지막 heartbeat' + 경고 배너",
      status: "pending"
    },
    {
      id: "SUB-E-002",
      title: "STALE → 자동 삭제 (30분)",
      precondition: "서브도메인 STALE 상태",
      steps: [
        "30분 대기 후 목록 확인"
      ],
      expected: "자동 삭제되어 목록에서 제거",
      status: "pending"
    },
    {
      id: "SUB-E-003",
      title: "수동 등록 시 유효하지 않은 JSON 업로드",
      precondition: "잘못된 형식의 JSON 파일",
      steps: [
        "수동 등록 폼에서 유효하지 않은 JSON 업로드",
        "[등록] 클릭"
      ],
      expected: "에러 메시지 표시 ('유효하지 않은 OpenAPI 형식입니다')",
      status: "pending"
    },
    {
      id: "SUB-E-004",
      title: "수동 등록 필수값 미입력 시 버튼 비활성",
      precondition: "수동 등록 폼 열림",
      steps: [
        "서브도메인 이름만 입력 (환경, Base URL 미입력)"
      ],
      expected: "[등록] 버튼 비활성 상태",
      status: "pending"
    },
    {
      id: "SUB-E-005",
      title: "빈 상태 (등록된 서브도메인 없음)",
      precondition: "아무 서브도메인도 등록되지 않음",
      steps: [
        "서브도메인 탭 진입"
      ],
      expected: "빈 상태 UI: '등록된 서브도메인이 없습니다' + 연동 가이드 버튼",
      status: "pending"
    }
  ]
};
