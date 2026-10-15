import { apiClient } from '../api/client'
import type { Topic } from '../types/domain'

export interface TopicCatalog {
  categories: string[]
  topics: Topic[]
}

export async function getTopics(signal?: AbortSignal): Promise<TopicCatalog> {
  const response = await apiClient.get<TopicCatalog>('/topics', { signal })
  return response.data
}
