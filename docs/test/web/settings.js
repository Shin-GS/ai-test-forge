export default {
  feature: "설정",
  screen: "설정 (워크스페이스+AI+AgentLoop)",
  priority: "medium",
  lastUpdated: "2026-06-18",
  cases: [
    // ─── 워크스페이스 관리 ───
    {
      id: "SET-N-001",
      title: "워크스페이스 생성",
      precondition: "설정 탭 진입",
      steps: [
        "[+ 새 워크스페이스] 버튼 클릭",
        "이름 입력 모달에서 이름 입력",
        "생성 확인"
      ],
      expected: "새 워크스페이스 생성 (모든 서브도메인 환경 기본값 dev), 목록에 추가",
      status: "pending"
    },
    {
      id: "SET-N-002",
      title: "워크스페이스 환경 매핑 변경",
      precondition: "워크스페이스 존재, 서브도메인 등록됨",
      steps: [
        "매핑 테이블에서 서브도메인의 환경 입력 필드 변경 (dev → feature-login)"
      ],
      expected: "즉시 저장 (별도 저장 버튼 없음), 변경된 환경 반영",
      status: "pending"
    },
    {
      id: "SET-N-003",
      title: "워크스페이스 전환",
      precondition: "워크스페이스 2개 이상 존재",
      steps: [
        "상단 워크스페이스 드롭다운에서 다른 워크스페이스 선택"
      ],
      expected: "전환 완료, 서브도메인 환경 매핑이 선택한 워크스페이스 기준으로 변경",
      status: "pending"
    },
    {
      id: "SET-N-004",
      title: "워크스페이스 삭제",
      precondition: "기본이 아닌 워크스페이스 존재",
      steps: [
        "[워크스페이스 삭제] 버튼 클릭",
        "확인 후 삭제"
      ],
      expected: "삭제 완료 → 기본 워크스페이스로 자동 전환",
      status: "pending"
    },
    {
      id: "SET-E-001",
      title: "기본 워크스페이스 삭제 불가",
      precondition: "기본 워크스페이스 선택 중",
      steps: [
        "[워크스페이스 삭제] 버튼 상태 확인"
      ],
      expected: "삭제 버튼 비활성 또는 에러 메시지 표시",
      status: "pending"
    },
    // ─── AI 설정 ───
    {
      id: "SET-N-005",
      title: "AI 설정 조회 (읽기 전용)",
      precondition: "설정 탭 진입",
      steps: [
        "AI 설정 섹션 확인"
      ],
      expected: "Reasoning/Fast 모델 Provider+Model 표시 (회색 배경, 읽기 전용) + 안내 문구 '서버 환경변수(.env)에서만 변경 가능'",
      status: "pending"
    },
    {
      id: "SET-N-006",
      title: "'다음 액션' 힌트 토글 변경",
      precondition: "설정 탭 AI 섹션",
      steps: [
        "'다음 액션' 힌트 토글 ON/OFF 변경"
      ],
      expected: "즉시 저장, 채팅 결과 후 '💡 더 할 수 있는 것' 동작 여부 반영",
      status: "pending"
    },
    // ─── Agent Loop 설정 ───
    {
      id: "SET-N-007",
      title: "Agent Loop 파라미터 변경",
      precondition: "설정 탭 Agent Loop 섹션",
      steps: [
        "최대 반복 횟수 변경 (20 → 30)",
        "턴당 Tool Call 변경 (5 → 8)",
        "타임아웃 변경 (120 → 180)",
        "[저장] 버튼 클릭"
      ],
      expected: "설정 저장 성공, 이후 Agent Loop에 변경된 값 적용",
      status: "pending"
    },
    // ─── 계정 정보 ───
    {
      id: "SET-N-008",
      title: "계정 정보 표시 (읽기 전용)",
      precondition: "설정 탭 계정 섹션",
      steps: [
        "이메일, 이름 필드 확인"
      ],
      expected: "이메일, 이름 읽기 전용 표시",
      status: "pending"
    },
    {
      id: "SET-N-009",
      title: "비밀번호 변경",
      precondition: "설정 탭 계정 섹션",
      steps: [
        "[비밀번호 변경] 버튼 클릭",
        "현재 비밀번호 + 새 비밀번호 입력",
        "변경 확인"
      ],
      expected: "비밀번호 변경 성공 메시지",
      status: "pending"
    },
    {
      id: "SET-N-010",
      title: "OTP 활성화",
      precondition: "OTP 미설정 상태",
      steps: [
        "OTP 토글 ON",
        "otpauth:// URI (QR코드) 표시 확인",
        "인증 앱에서 6자리 코드 입력",
        "인증 성공"
      ],
      expected: "OTP 활성화 완료, 이후 로그인 시 OTP 입력 필요",
      status: "pending"
    },
    {
      id: "SET-N-011",
      title: "OTP 비활성화",
      precondition: "OTP 활성화 상태",
      steps: [
        "OTP 토글 OFF"
      ],
      expected: "OTP 비활성화 완료, 이후 로그인 시 OTP 불필요",
      status: "pending"
    },
    {
      id: "SET-N-012",
      title: "로그아웃",
      precondition: "로그인 상태",
      steps: [
        "[로그아웃] 버튼 클릭"
      ],
      expected: "세션 종료 + 로그인 페이지로 이동",
      status: "pending"
    },
    // ─── 예외 ───
    {
      id: "SET-E-002",
      title: "비밀번호 변경 실패 (현재 비밀번호 틀림)",
      precondition: "비밀번호 변경 폼 진입",
      steps: [
        "잘못된 현재 비밀번호 입력",
        "변경 시도"
      ],
      expected: "'현재 비밀번호가 올바르지 않습니다' 에러 메시지",
      status: "pending"
    },
    {
      id: "SET-E-003",
      title: "OTP 활성화 실패 (잘못된 코드)",
      precondition: "OTP 설정 중 코드 입력 단계",
      steps: [
        "잘못된 6자리 코드 입력"
      ],
      expected: "'인증 코드가 올바르지 않습니다' 에러, OTP 미활성화 유지",
      status: "pending"
    }
  ]
};
