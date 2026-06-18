export default {
  feature: "로그인",
  screen: "로그인/인증",
  priority: "high",
  lastUpdated: "2026-06-18",
  cases: [
    // ─── 정상 플로우 ───
    {
      id: "LOGIN-N-001",
      title: "기본 로그인 성공 (이메일/비밀번호)",
      precondition: "계정 생성됨, OTP 미설정",
      steps: [
        "/login 페이지 접근",
        "이메일 + 비밀번호 입력",
        "[로그인] 버튼 클릭"
      ],
      expected: "로그인 성공 → 메인 화면(채팅 탭)으로 이동, JWT 저장",
      status: "pending"
    },
    {
      id: "LOGIN-N-002",
      title: "OTP 2단계 인증 성공",
      precondition: "OTP 활성화된 계정",
      steps: [
        "이메일/비밀번호 로그인 성공",
        "OTP 입력 화면 표시 확인",
        "인증 앱에서 6자리 코드 확인 후 입력",
        "[인증] 버튼 클릭"
      ],
      expected: "OTP 인증 성공 → 메인 화면 이동",
      status: "pending"
    },
    {
      id: "LOGIN-N-003",
      title: "비로그인 상태에서 리다이렉트",
      precondition: "로그아웃 상태",
      steps: [
        "/ (메인) 또는 /recipes 등 인증 필요 페이지 직접 접근"
      ],
      expected: "/login 페이지로 자동 리다이렉트",
      status: "pending"
    },
    {
      id: "LOGIN-N-004",
      title: "로그인 폼 UI 요소 확인",
      precondition: "비로그인 상태",
      steps: [
        "/login 페이지 접근",
        "UI 요소 확인"
      ],
      expected: "중앙 정렬 카드: 로고 + 서비스명 + 이메일 입력 + 비밀번호 입력 + [로그인] 버튼",
      status: "pending"
    },
    // ─── 예외 플로우 ───
    {
      id: "LOGIN-E-001",
      title: "잘못된 비밀번호 → 에러 메시지",
      precondition: "계정 존재",
      steps: [
        "올바른 이메일 + 잘못된 비밀번호 입력",
        "[로그인] 클릭"
      ],
      expected: "폼 상단에 '이메일 또는 비밀번호가 올바르지 않습니다' 에러 표시, 비밀번호 필드 초기화",
      status: "pending"
    },
    {
      id: "LOGIN-E-002",
      title: "존재하지 않는 이메일 → 에러 메시지",
      precondition: "없음",
      steps: [
        "존재하지 않는 이메일 + 아무 비밀번호 입력",
        "[로그인] 클릭"
      ],
      expected: "'이메일 또는 비밀번호가 올바르지 않습니다' (동일 메시지로 계정 존재 여부 노출 방지)",
      status: "pending"
    },
    {
      id: "LOGIN-E-003",
      title: "OTP 인증 실패 (잘못된 코드)",
      precondition: "이메일/비밀번호 인증 성공 후 OTP 입력 화면",
      steps: [
        "잘못된 6자리 코드 입력",
        "[인증] 클릭"
      ],
      expected: "'인증 코드가 올바르지 않습니다' 에러 메시지 표시",
      status: "pending"
    },
    {
      id: "LOGIN-E-004",
      title: "OTP 화면에서 [← 이전으로] 복귀",
      precondition: "OTP 입력 화면 표시 중",
      steps: [
        "[← 이전으로] 버튼 클릭"
      ],
      expected: "기본 로그인 폼으로 복귀 (이메일 유지)",
      status: "pending"
    },
    {
      id: "LOGIN-E-005",
      title: "JWT 만료 → 재로그인 리다이렉트",
      precondition: "로그인 세션 만료",
      steps: [
        "세션 만료 후 API 호출 시도 (아무 페이지 조작)"
      ],
      expected: "/login 페이지로 리다이렉트 + 세션 만료 안내",
      status: "pending"
    },
    {
      id: "LOGIN-E-006",
      title: "Rate Limit (로그인 5회 실패)",
      precondition: "5분 내 5회 로그인 실패",
      steps: [
        "6번째 로그인 시도"
      ],
      expected: "'잠시 후 다시 시도해주세요' 에러 메시지 (Rate Limiter 동작)",
      status: "pending"
    }
  ]
};
