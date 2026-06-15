const API_BASE = '/api/v1'

interface LoginRequest {
  email: string
  password: string
}

interface LoginResponse {
  token: string
  email: string
  name: string
}

interface RegisterRequest {
  email: string
  password: string
  name: string
}

interface RegisterResponse {
  token: string
  email: string
  name: string
}

export async function login(data: LoginRequest): Promise<LoginResponse> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new Error(error?.message ?? '이메일 또는 비밀번호가 올바르지 않습니다.')
  }

  return res.json()
}

export async function register(data: RegisterRequest): Promise<RegisterResponse> {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new Error(error?.message ?? '회원가입에 실패했습니다.')
  }

  return res.json()
}
