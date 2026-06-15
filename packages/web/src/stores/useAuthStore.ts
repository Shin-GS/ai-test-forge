import { create } from 'zustand'
import { login as loginApi } from '@/services/authApi'

interface User {
  email: string
  name: string
}

interface AuthState {
  isAuthenticated: boolean
  user: User | null
  token: string | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
  hydrate: () => void
}

const TOKEN_KEY = 'auth_token'
const USER_KEY = 'auth_user'

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  user: null,
  token: null,

  login: async (email, password) => {
    const response = await loginApi({ email, password })
    const user: User = { email: response.email, name: response.name }

    localStorage.setItem(TOKEN_KEY, response.token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))

    set({ isAuthenticated: true, user, token: response.token })
  },

  logout: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    set({ isAuthenticated: false, user: null, token: null })
  },

  hydrate: () => {
    const token = localStorage.getItem(TOKEN_KEY)
    const userJson = localStorage.getItem(USER_KEY)

    if (token && userJson) {
      const user = JSON.parse(userJson) as User
      set({ isAuthenticated: true, user, token })
    }
  },
}))
