import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useApiRequest } from './useApiRequest'

describe('useApiRequest', () => {
  it('normalizes a failed request and retries only after explicit user action', async () => {
    const request = vi.fn((_signal: AbortSignal) => Promise.reject(new Error('upstream unavailable')))
    const { result } = renderHook(() => useApiRequest(request))

    await waitFor(() => expect(result.current.status).toBe('error'))
    expect(result.current.error?.message).toBe('upstream unavailable')
    expect(request).toHaveBeenCalledOnce()

    act(() => result.current.retry())
    await waitFor(() => expect(request).toHaveBeenCalledTimes(2))
    expect(result.current.status).toBe('error')
  })
})
