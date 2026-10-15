import { describe, expect, it } from 'vitest'
import { normalizeApiError } from './apiError'

describe('normalizeApiError', () => {
  it('preserves server problem detail, status, and code', () => {
    const error = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: { status: 422, data: { detail: 'Invalid search query', code: 'INVALID_QUERY' } },
    })

    expect(normalizeApiError(error)).toMatchObject({
      name: 'ApiError',
      message: 'Invalid search query',
      status: 422,
      code: 'INVALID_QUERY',
    })
  })

  it('reports network failures without returning a success-shaped value', () => {
    const error = Object.assign(new Error('Network Error'), { isAxiosError: true })
    expect(normalizeApiError(error)).toMatchObject({
      name: 'ApiError',
      message: 'The service could not be reached. Check your connection and try again.',
    })
  })

  it('preserves non-Axios errors', () => {
    expect(normalizeApiError(new Error('Unexpected failure')).message).toBe('Unexpected failure')
  })
})
