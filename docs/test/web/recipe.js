export default {
  feature: "레시피",
  screen: "레시피 실행",
  priority: "high",
  lastUpdated: "2026-06-18",
  cases: [
    // ─── 정상 플로우 ───
    {
      id: "RCP-N-001",
      title: "레시피 목록 표시 (정상)",
      precondition: "저장된 레시피 3개 이상 존재",
      steps: [
        "레시피 탭 진입",
        "'자주 사용' 섹션 + '전체' 섹션 확인"
      ],
      expected: "검색바 + 태그 필터 + 레시피 카드(이름, 설명, 태그, 단계 수, 변수 수, [실행][상세]) 표시",
      status: "pending"
    },
    {
      id: "RCP-N-002",
      title: "레시피 검색 및 태그 필터",
      precondition: "다양한 태그의 레시피 존재",
      steps: [
        "검색바에 레시피명 입력",
        "태그 필터 드롭다운에서 특정 태그 선택"
      ],
      expected: "입력/선택에 맞는 레시피만 필터링되어 표시",
      status: "pending"
    },
    {
      id: "RCP-N-003",
      title: "레시피 상세 보기",
      precondition: "레시피 목록에서 [상세 보기] 클릭",
      steps: [
        "단계별 시각화 확인",
        "변수 목록 (auto / input) 확인",
        "태그, 생성일, 마지막 사용일, 사용 횟수 확인"
      ],
      expected: "Step 1~N 시각화 (서브도메인 + method + path), 변수 표시, 메타 정보 표시",
      status: "pending"
    },
    {
      id: "RCP-N-004",
      title: "레시피 실행 (변수 없음, 전체 auto/fixed)",
      precondition: "모든 step이 fixed/gen 전략인 레시피",
      steps: [
        "레시피 목록에서 [▶ 실행] 클릭"
      ],
      expected: "채팅 탭으로 이동 → 즉시 실행 (AI 비호출) → 결과 표시",
      status: "pending"
    },
    {
      id: "RCP-N-005",
      title: "레시피 실행 (input 변수 있음)",
      precondition: "{{input:포지션ID}} 변수 포함 레시피",
      steps: [
        "[▶ 실행] 클릭",
        "AI가 변수 입력 질문 표시",
        "값 입력 후 전송"
      ],
      expected: "변수 질문 → 입력값 반영 → 실행 시작 → 결과 표시",
      status: "pending"
    },
    {
      id: "RCP-N-006",
      title: "레시피 저장 (대화 이력 기반)",
      precondition: "채팅에서 멀티 API 호출 완료",
      steps: [
        "'레시피로 저장해줘' 입력 전송",
        "AI가 저장 범위 확인 요청",
        "사용자 승인"
      ],
      expected: "대화 이력에서 레시피 자동 생성 → 저장 완료 메시지",
      status: "pending"
    },
    {
      id: "RCP-N-007",
      title: "레시피 수정",
      precondition: "본인 소유 레시피 상세 화면",
      steps: [
        "[수정] 버튼 클릭",
        "단계 추가/삭제 또는 변수 수정",
        "저장"
      ],
      expected: "편집 모드 진입 → 변경 사항 반영 → 저장 완료",
      status: "pending"
    },
    {
      id: "RCP-N-008",
      title: "레시피 삭제",
      precondition: "본인 소유 레시피",
      steps: [
        "[삭제] 버튼 클릭",
        "확인 모달에서 확인"
      ],
      expected: "레시피 삭제 → 목록에서 제거",
      status: "pending"
    },
    {
      id: "RCP-N-009",
      title: "레시피 자동 제안 (유사 레시피 발견)",
      precondition: "'입사지원 데이터 생성' 레시피 저장됨",
      steps: [
        "채팅에서 '입사지원 데이터 만들어줘' 입력 전송"
      ],
      expected: "'📋 유사한 레시피가 있습니다' 카드 + [레시피 실행] + [새로 대화로 진행] 버튼",
      status: "pending"
    },
    {
      id: "RCP-N-010",
      title: "레시피 실행 시 Progress Steps 표시",
      precondition: "레시피 실행 시작됨",
      steps: [
        "실행 중 Progress Steps 확인"
      ],
      expected: "📋 아이콘으로 일반 Agent Loop과 구분, Step 1~N 순서대로 진행 표시",
      status: "pending"
    },
    // ─── 예외 플로우 ───
    {
      id: "RCP-E-001",
      title: "레시피 실행 중 401 (서브도메인 미로그인)",
      precondition: "레시피 참조 서브도메인 미로그인",
      steps: [
        "레시피 실행 시작",
        "401 발생 시 UI 확인"
      ],
      expected: "로그인 유도 카드 표시 + [계속 진행] 버튼 (채팅 Case 4와 동일 처리)",
      status: "pending"
    },
    {
      id: "RCP-E-002",
      title: "레시피 스펙 검증 실패 (참조 API 삭제됨)",
      precondition: "레시피 참조 API가 스펙에서 제거됨",
      steps: [
        "레시피 실행 시도"
      ],
      expected: "실행 전 경고 표시: 'API가 현재 스펙과 호환되지 않습니다' + AI 자동 수정 옵션",
      status: "pending"
    },
    {
      id: "RCP-E-003",
      title: "빈 상태 (레시피 없음)",
      precondition: "저장된 레시피 없음",
      steps: [
        "레시피 탭 진입"
      ],
      expected: "'저장된 레시피가 없습니다' + 안내 문구 + [채팅으로 이동] 버튼",
      status: "pending"
    },
    {
      id: "RCP-E-004",
      title: "레시피 실행 중 서버 에러",
      precondition: "레시피 실행 중 서브도메인 5xx",
      steps: [
        "레시피 실행 중 서버 에러 발생 확인"
      ],
      expected: "에러 표시 + 재시도 버튼 (채팅 Case 5와 동일 처리)",
      status: "pending"
    }
  ]
};
