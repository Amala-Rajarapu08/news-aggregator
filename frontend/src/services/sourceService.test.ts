import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../api/client'
import { getAvailableSources } from './sourceService'

vi.mock('../api/client', () => ({ apiClient: { get: vi.fn() } }))

describe('available publisher API request', () => {
  beforeEach(() => vi.clearAllMocks())

  it('requests the article-backed publisher catalog using only supported filters', async () => {
    const signal = new AbortController().signal
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{ id: 'source-1', name: 'SEC', domain: 'sec.gov', baseUrl: 'https://www.sec.gov' }],
    } as never)

    await expect(getAvailableSources('business', 'markets', signal)).resolves.toEqual([
      { id: 'source-1', name: 'SEC', domain: 'sec.gov', homepageUrl: 'https://www.sec.gov' },
    ])
    expect(apiClient.get).toHaveBeenCalledWith('/sources/available', {
      signal,
      params: { category: 'business', q: 'markets' },
    })
  })

  it('leaves the category and query absent for All coverage', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] } as never)

    await expect(getAvailableSources()).resolves.toEqual([])
    expect(apiClient.get).toHaveBeenCalledWith('/sources/available', {
      signal: undefined,
      params: { category: undefined, q: undefined },
    })
  })
})
