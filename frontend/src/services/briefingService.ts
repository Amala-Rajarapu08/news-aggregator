import { apiClient } from '../api/client'
import type { Briefing } from '../types/domain'

export async function getDailyBriefing(signal?: AbortSignal): Promise<Briefing> {
  const response = await apiClient.get<Briefing>('/briefings/daily', { signal })
  return response.data
}
