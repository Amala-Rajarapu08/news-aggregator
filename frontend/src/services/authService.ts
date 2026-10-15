import { apiClient, clearAccessToken, setAccessToken, restoreSession } from '../api/client'
import type { User } from '../types/domain'

export interface Credentials {
  email: string
  password: string
}

export interface RegistrationDetails extends Credentials {
  name: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  user: User
}

export async function register(details: RegistrationDetails): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/auth/register', details)
  setAccessToken(response.data.accessToken)
  return response.data
}

export async function login(credentials: Credentials): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/auth/login', credentials)
  setAccessToken(response.data.accessToken)
  return response.data
}

export function clearSession(): void {
  clearAccessToken()
}

export function refreshSession(): Promise<AuthResponse> { return restoreSession() }
export async function logout(): Promise<void> { await apiClient.post('/auth/logout'); clearAccessToken() }
