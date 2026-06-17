export const MESSAGES = {
  common: {
    loading: '로딩 중...',
    error: '오류가 발생했습니다',
    retry: '다시 시도',
    confirm: '확인',
    cancel: '취소',
    delete: '삭제',
    save: '저장',
    create: '생성',
    close: '닫기',
  },
  auth: {
    loginTitle: 'AI Test Forge',
    loginSubtitle: '마이크로서비스 테스트 데이터, 채팅 한 줄로 끝.',
    emailLabel: '이메일',
    emailPlaceholder: 'admin@company.com',
    passwordLabel: '비밀번호',
    passwordPlaceholder: '비밀번호를 입력하세요',
    loginButton: '로그인',
    loginLoading: '로그인 중...',
    loginFailed: '로그인에 실패했습니다. 이메일과 비밀번호를 확인하세요.',
    logout: '로그아웃',
  },
  chat: {
    newChat: '+ 새 대화',
    inputPlaceholder: '메시지를 입력하세요...',
    inputLoading: 'AI 응답 대기 중...',
    onboardingTitle: '🚀 빠른 시작',
    onboardingHint: '또는 자유롭게 입력하세요...',
    sessionEmpty: '대화를 시작해보세요',
  },
  subdomain: {
    title: '서브도메인',
    searchPlaceholder: '서브도메인 검색...',
    emptyTitle: '등록된 서브도메인이 없습니다',
    emptyDescription:
      '클라이언트 라이브러리를 서브도메인 서버에 추가하면 자동으로 등록됩니다.',
    goToChat: '채팅에서 등록하기',
    staleWarning: (count: number) =>
      `⚠️ ${count}개 서버의 연결이 불안정합니다`,
    chatButton: '💬 채팅에서 사용',
  },
  recipe: {
    title: '레시피',
    searchPlaceholder: '레시피 검색...',
    emptyTitle: '저장된 레시피가 없습니다',
    emptyDescription:
      '채팅에서 작업 후 "레시피로 저장해줘"라고 말해보세요.',
    goToChat: '채팅으로 이동',
    run: '▶ 실행',
    detail: '상세 보기',
    frequentTitle: '⭐ 자주 사용',
    allTitle: '📋 전체',
  },
  settings: {
    title: '설정',
    workspace: {
      title: '워크스페이스',
      createButton: '+ 새 워크스페이스',
      creating: '생성 중...',
      namePlaceholder: '워크스페이스 이름',
      default: '기본',
    },
    account: {
      title: '계정',
    },
  },
} as const
