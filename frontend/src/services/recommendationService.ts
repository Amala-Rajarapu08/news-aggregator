import { apiClient } from '../api/client'
import type { Recommendation } from '../types/domain'

export interface RecommendationFeed {
  forYou: Recommendation[]
  becauseYouRead: Recommendation[]
  similarInterests: Recommendation[]
  emergingInterests: Recommendation[]
}

export async function getRecommendations(signal?: AbortSignal): Promise<RecommendationFeed> {
  const response = await apiClient.get<RecommendationFeed>('/recommendations', { signal })
  return response.data
}
