import { type AxiosAdapter, AxiosHeaders } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { apiClient, clearAccessToken, setAccessToken } from './client'

describe('api client access token handling', () => {
  const originalAdapter = apiClient.defaults.adapter

  afterEach(() => {
    clearAccessToken()
    apiClient.defaults.adapter = originalAdapter
  })

  it('adds the in-memory bearer token to requests and removes it when cleared', async () => {
    const authorizationHeaders: Array<string | null> = []
    const adapter: AxiosAdapter = async config => {
      const authorization = config.headers.get('Authorization')
      authorizationHeaders.push(typeof authorization === 'string' ? authorization : null)
      return {
        data: {},
        status: 200,
        statusText: 'OK',
        headers: new AxiosHeaders(),
        config,
      }
    }
    apiClient.defaults.adapter = adapter

    setAccessToken('test-access-token')
    await apiClient.get('/users/me')
    clearAccessToken()
    await apiClient.get('/users/me')

    expect(authorizationHeaders).toEqual(['Bearer test-access-token', null])
  })
})
