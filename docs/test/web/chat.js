export default {
  feature: "채팅",
  screen: "채팅 (Agent Loop)",
  priority: "critical",
  lastUpdated: "2026-06-18",
  cases: [
    // ─── 정상 플로우 ───
    {
      id: "CHAT-N-001",
      title: "첫 접속 시 온보딩 화면 표시",
      precondition: "로그인 완료, 활성 세션 없음",
      steps: [
        "메인 화면(채팅 탭) 진입",
        "온보딩 영역 확인"
      ],
      expected: "퀵 액션 버튼 + '또는 자유롭게 입력하세요...' 안내 + 하단 입력창 활성",
      status: "pending"
    },
    {
      id: "CHAT-N-002",
      title: "단일 API 호출 (Agent Loop 기본)",
      precondition: "서브도메인 1개 등록 + ACTIVE + 서브도메인 로그인됨",
      steps: [
        "입력창에 '회원 생성해줘' 입력 후 전송",
        "AI 응답 및 Progress Steps 확인",
        "Tool Call 완료 후 결과 확인"
      ],
      expected: "FE가 서브도메인 API 직접 호출 → 성공 결과 표시, 입력창 재활성화",
      status: "pending"
    },
    {
      id: "CHAT-N-003",
      title: "크로스 서비스 호출 (3개 이상 서브도메인)",
      precondition: "서브도메인 3개 등록 + 모두 ACTIVE + 로그인됨",
      steps: [
        "'입사지원 데이터 만들어줘' 입력 전송",
        "Progress Steps에서 순차 실행 확인",
        "각 Step에 서브도메인명 + method + path 표시 확인"
      ],
      expected: "의존성 순서대로 호출 (회원→이력서→지원) → 전체 성공, 결과 요약 표시",
      status: "pending"
    },
    {
      id: "CHAT-N-004",
      title: "대화 진행 중 입력창 비활성화",
      precondition: "Agent Loop 실행 중",
      steps: [
        "메시지 전송 후 AI 처리 중 상태 확인",
        "입력창 상태 확인"
      ],
      expected: "입력창 비활성 + 'AI 응답 대기 중...' placeholder 표시",
      status: "pending"
    },
    {
      id: "CHAT-N-005",
      title: "대화 완료 후 상세보기 펼침/접기",
      precondition: "Agent Loop 완료 상태",
      steps: [
        "AI 완료 메시지 하단의 [상세 보기 ▾] 클릭",
        "Tool Call 상세 정보 확인",
        "다시 클릭하여 접기"
      ],
      expected: "각 API 호출의 요청/응답 상세가 펼쳐지고 다시 접힘",
      status: "pending"
    },
    {
      id: "CHAT-N-006",
      title: "세션 히스토리 표시 및 전환",
      precondition: "이전 세션 2개 이상 존재",
      steps: [
        "좌측 사이드바에서 이전 세션 목록 확인",
        "이전 세션 클릭"
      ],
      expected: "세션 목록에 날짜+첫 메시지 요약 표시, 클릭 시 해당 세션 대화 내용 로드",
      status: "pending"
    },
    {
      id: "CHAT-N-007",
      title: "새 대화 세션 생성",
      precondition: "기존 세션 진행 중",
      steps: [
        "좌측 사이드바의 [+ 새 대화] 버튼 클릭",
        "새 입력창 상태 확인"
      ],
      expected: "빈 채팅 화면(온보딩) 표시 + 새 세션 생성",
      status: "pending"
    },
    {
      id: "CHAT-N-008",
      title: "퀵 액션 버튼으로 레시피 실행",
      precondition: "자주 쓰는 레시피 등록됨, 빈 채팅 화면",
      steps: [
        "온보딩 영역의 퀵 액션 버튼 클릭"
      ],
      expected: "해당 레시피 실행 시작 (메시지 자동 입력 + 전송)",
      status: "pending"
    },
    {
      id: "CHAT-N-009",
      title: "SSE 스트리밍 실시간 수신",
      precondition: "메시지 전송 후 SSE 연결 활성",
      steps: [
        "메시지 전송",
        "AI 텍스트 스트리밍 확인",
        "tool_call_start / step_progress / done 이벤트 순서 확인"
      ],
      expected: "텍스트가 점진적으로 표시, Progress Steps 실시간 업데이트, done 시 입력 활성화",
      status: "pending"
    },
    {
      id: "CHAT-N-010",
      title: "CORS 동작 확인 (FE → 서브도메인)",
      precondition: "서브도메인 CORS 설정 완료",
      steps: [
        "채팅에서 API 호출 유발",
        "브라우저 Network 탭에서 CORS 헤더 확인"
      ],
      expected: "FE가 서브도메인 직접 호출 성공, 쿠키 전달 (credentials: include)",
      status: "pending"
    },
    // ─── 예외 플로우 ───
    {
      id: "CHAT-E-001",
      title: "401 에러 → 로그인 유도",
      precondition: "서브도메인 미로그인 상태",
      steps: [
        "채팅에서 해당 서브도메인 API 호출 유발",
        "401 응답 시 UI 확인"
      ],
      expected: "Agent Loop 일시정지, '⚠️ {서브도메인명}에 로그인이 필요합니다' + 로그인 링크 + [계속 진행] 버튼",
      status: "pending"
    },
    {
      id: "CHAT-E-002",
      title: "401 후 로그인 → Agent Loop 재개",
      precondition: "CHAT-E-001 상태에서 로그인 완료",
      steps: [
        "[웹에서 로그인] 클릭하여 새 탭에서 로그인",
        "[계속 진행] 버튼 클릭"
      ],
      expected: "중단된 Step부터 Agent Loop 재개, 정상 완료",
      status: "pending"
    },
    {
      id: "CHAT-E-003",
      title: "5xx 에러 → 재시도",
      precondition: "서브도메인 서버 일시 장애 상황",
      steps: [
        "채팅에서 API 호출 유발",
        "5xx 에러 발생 확인",
        "AI 자동 재시도 확인"
      ],
      expected: "에러 표시 + '🔄 재시도 중...' 표시, 재시도 후 성공/실패 결과",
      status: "pending"
    },
    {
      id: "CHAT-E-004",
      title: "동일 API 3회 실패 → 중단",
      precondition: "반복 실패 상황 (서버 지속 장애)",
      steps: [
        "API 호출 반복 실패 유발",
        "3회 실패 후 UI 확인"
      ],
      expected: "'반복 실패로 중단합니다. 다시 시도하시겠어요?' 메시지 + [재시도] 버튼, 입력창 활성",
      status: "pending"
    },
    {
      id: "CHAT-E-005",
      title: "Agent Loop 타임아웃 (120초 초과)",
      precondition: "타임아웃 120초 초과 상황",
      steps: [
        "장시간 실행되는 요청 전송",
        "120초 경과 후 UI 확인"
      ],
      expected: "타임아웃 에러 표시 + 입력창 활성화",
      status: "pending"
    },
    {
      id: "CHAT-E-006",
      title: "SSE 연결 끊김 → 재연결",
      precondition: "Agent Loop 진행 중 네트워크 일시 끊김",
      steps: [
        "네트워크 일시 중단 후 복구",
        "SSE 재연결 여부 확인"
      ],
      expected: "자동 재연결 (Last-Event-ID 기반) + 누락 이벤트 수신",
      status: "pending"
    },
    {
      id: "CHAT-E-007",
      title: "동시 실행 한도 초과",
      precondition: "max-concurrent 설정 초과 상황",
      steps: [
        "다수의 Agent Loop 동시 실행 시도"
      ],
      expected: "'동시 실행 한도 초과' 에러 메시지 표시",
      status: "pending"
    }
  ]
};
