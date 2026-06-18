/**
 * 테스트 파일 매니페스트
 * 모든 기능별 테스트 데이터 파일 목록을 관리합니다.
 */
const TEST_MANIFEST = [
  {
    id: "chat",
    name: "채팅 (Agent Loop)",
    file: "../web/chat.js",
    priority: "critical",
    description: "Agent Loop 실행, FE 직접 호출, SSE 통신, 에러 처리"
  },
  {
    id: "subdomain",
    name: "서브도메인 등록/관리",
    file: "../web/subdomain.js",
    priority: "high",
    description: "클라이언트 라이브러리 등록, heartbeat, 수동 업로드, STALE 처리"
  },
  {
    id: "recipe",
    name: "레시피 실행",
    file: "../web/recipe.js",
    priority: "high",
    description: "레시피 목록, 실행, 변수 입력, 스펙 검증, 대화 기반 생성"
  },
  {
    id: "login",
    name: "로그인/인증",
    file: "../web/login.js",
    priority: "high",
    description: "이메일/비밀번호 로그인, OTP 2단계 인증, JWT 세션"
  },
  {
    id: "settings",
    name: "설정 (워크스페이스+AI+AgentLoop)",
    file: "../web/settings.js",
    priority: "medium",
    description: "워크스페이스 관리, AI 설정 조회, Agent Loop 파라미터, 계정 정보"
  }
];

export default TEST_MANIFEST;
