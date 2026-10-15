import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { login, register, refreshSession, logout, type Credentials, type RegistrationDetails } from '../services/authService'
import { clearAccessToken } from '../api/client'
import type { User } from '../types/domain'
type AuthContextValue = { user: User | null; restoring: boolean; error: string; signIn: (input: Credentials) => Promise<void>; signUp: (input: RegistrationDetails) => Promise<void>; signOut: () => Promise<void>; updateUser: (user: User) => void }
const AuthContext = createContext<AuthContextValue | null>(null)
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [restoring, setRestoring] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    refreshSession().then(result => { if (active) setUser(result.user) }).catch(err => { if (active && err.status !== 401) setError(err.message) }).finally(() => { if (active) setRestoring(false) })
    const expired = () => { setUser(null); clearAccessToken() }
    window.addEventListener('pulse:session-expired', expired)
    return () => { active = false; window.removeEventListener('pulse:session-expired', expired) }
  }, [])
  const signIn = useCallback(async (input: Credentials) => { const result = await login(input); setUser(result.user); setError('') }, [])
  const signUp = useCallback(async (input: RegistrationDetails) => { const result = await register(input); setUser(result.user); setError('') }, [])
  const signOut = useCallback(async () => { await logout(); setUser(null); clearAccessToken() }, [])
  return <AuthContext.Provider value={{ user, restoring, error, signIn, signUp, signOut, updateUser: setUser }}>{children}</AuthContext.Provider>
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error('AuthProvider is required'); return value }
