import axios from 'axios'

export class ApiError extends Error {
  readonly status: number | undefined
  readonly code: string | undefined

  constructor(message: string, status?: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error

  if (!axios.isAxiosError(error)) {
    return new ApiError(error instanceof Error ? error.message : 'An unexpected error occurred.')
  }

  const body = error.response?.data as { detail?: unknown; title?: unknown; code?: unknown; message?: unknown; validationErrors?: Record<string, string> } | undefined
  const detail = typeof body?.detail === 'string' ? body.detail : undefined
  const title = typeof body?.title === 'string' ? body.title : undefined
  const fields = body?.validationErrors ? Object.values(body.validationErrors).join(' ') : ''
  const message = fields || (typeof body?.message === 'string' ? body.message : detail ?? title) || (error.response
    ? `The service returned HTTP ${error.response.status}.`
    : 'The service could not be reached. Check your connection and try again.')

  return new ApiError(
    message,
    error.response?.status,
    typeof body?.code === 'string' ? body.code : undefined,
  )
}
