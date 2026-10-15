const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const appConfig = {
  apiBaseUrl: configuredApiBaseUrl || '/api/v1',
}
