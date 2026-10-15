import axios from 'axios'
import { appConfig } from '../config/env'
import { normalizeApiError } from '../services/apiError'
let accessToken: string | null = null
let refresh: Promise<any> | null = null
export const apiClient = axios.create({ baseURL: appConfig.apiBaseUrl, timeout: 15_000, withCredentials: true, headers: { Accept: 'application/json' } })
const sessionClient = axios.create({ baseURL: appConfig.apiBaseUrl, timeout: 15_000, withCredentials: true })
export function setAccessToken(token: string) { accessToken = token }
export function clearAccessToken() { accessToken = null }
export function restoreSession() {
  if (!refresh) refresh = sessionClient.post('/auth/refresh').then(response => { setAccessToken(response.data.accessToken); return response.data }).catch(error => { clearAccessToken(); throw normalizeApiError(error) }).finally(() => { refresh = null })
  return refresh
}
apiClient.interceptors.request.use(config => { if (accessToken && !config.url?.startsWith('/auth/')) config.headers.set('Authorization', `Bearer ${accessToken}`); return config })
apiClient.interceptors.response.use(response => response, async error => {
  const config = error.config
  if (error.response?.status === 401 && accessToken && config && !config.url?.startsWith('/auth/') && !config._retried) {
    config._retried = true
    try { await restoreSession(); return apiClient(config) }
    catch (refreshError) { window.dispatchEvent(new Event('pulse:session-expired')); throw refreshError }
  }
  throw axios.isCancel(error) ? error : normalizeApiError(error)
})
