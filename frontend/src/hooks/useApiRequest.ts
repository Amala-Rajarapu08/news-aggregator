import { useCallback, useEffect, useState } from 'react'
import { normalizeApiError, type ApiError } from '../services/apiError'

export type ApiRequestState<T> =
  | { status: 'idle'; data: null; error: null }
  | { status: 'loading'; data: null; error: null }
  | { status: 'success'; data: T; error: null }
  | { status: 'error'; data: null; error: ApiError }

export function useApiRequest<T>(request: (signal: AbortSignal) => Promise<T>, enabled = true) {
  const [state, setState] = useState<ApiRequestState<T>>({ status: 'idle', data: null, error: null })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!enabled) {
      setState({ status: 'idle', data: null, error: null })
      return undefined
    }

    const controller = new AbortController()
    setState({ status: 'loading', data: null, error: null })
    request(controller.signal)
      .then(data => {
        if (!controller.signal.aborted) setState({ status: 'success', data, error: null })
      })
      .catch(error => {
        if (!controller.signal.aborted) {
          setState({ status: 'error', data: null, error: normalizeApiError(error) })
        }
      })
    return () => controller.abort()
  }, [enabled, request, attempt])

  const retry = useCallback(() => setAttempt(value => value + 1), [])
  return { ...state, retry }
}
