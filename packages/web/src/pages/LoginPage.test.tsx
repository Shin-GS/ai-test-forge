import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import LoginPage from './LoginPage'

// Mock modules
vi.mock('@/services/authApi', () => ({
  login: vi.fn(),
}))

vi.mock('@/stores/useAuthStore', () => ({
  useAuthStore: Object.assign(
    (selector: (s: Record<string, unknown>) => unknown) =>
      selector({ login: vi.fn(), isAuthenticated: false }),
    { getState: () => ({ setAuth: vi.fn() }), setState: vi.fn() },
  ),
}))

import { login as loginApi } from '@/services/authApi'

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('이메일과 비밀번호 입력 필드를 렌더링한다', () => {
    renderLoginPage()
    expect(screen.getByLabelText(/이메일/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/비밀번호/i)).toBeInTheDocument()
  })

  it('로그인 버튼을 렌더링한다', () => {
    renderLoginPage()
    expect(screen.getByRole('button', { name: /로그인/i })).toBeInTheDocument()
  })

  it('로그인 실패 시 에러 메시지를 표시한다', async () => {
    const user = userEvent.setup()
    const mockLogin = loginApi as ReturnType<typeof vi.fn>
    mockLogin.mockRejectedValueOnce(new Error('이메일 또는 비밀번호가 올바르지 않습니다.'))

    renderLoginPage()

    await user.type(screen.getByLabelText(/이메일/i), 'test@test.com')
    await user.type(screen.getByLabelText(/비밀번호/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /로그인/i }))

    expect(await screen.findByText(/이메일 또는 비밀번호/i)).toBeInTheDocument()
  })

  it('OTP 필요 시 코드 입력 화면을 표시한다', async () => {
    const user = userEvent.setup()
    const mockLogin = loginApi as ReturnType<typeof vi.fn>
    mockLogin.mockResolvedValueOnce({ token: null, email: null, name: null, otpRequired: true })

    renderLoginPage()

    await user.type(screen.getByLabelText(/이메일/i), 'otp@test.com')
    await user.type(screen.getByLabelText(/비밀번호/i), 'pass123')
    await user.click(screen.getByRole('button', { name: /로그인/i }))

    expect(await screen.findByText(/2단계 인증/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/6자리/i)).toBeInTheDocument()
  })
})
