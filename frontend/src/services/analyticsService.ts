import { apiClient } from '../api/client'
import type { AnalyticsSummary } from '../types/domain'

export interface TrendSummary {
  risingTopics: string[]
  decliningTopics: string[]
  developingEvents: string[]
  articleVolume: Array<{ date: string; count: number }>
}

export async function getAnalytics(signal?: AbortSignal): Promise<AnalyticsSummary> {
  const response = await apiClient.get<AnalyticsSummary>('/analytics', { signal })
  return response.data
}

export async function getTrends(signal?: AbortSignal): Promise<TrendSummary> {
  const response = await apiClient.get<TrendSummary>('/analytics/trends', { signal })
  return response.data
}
