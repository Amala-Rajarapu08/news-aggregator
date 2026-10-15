import { apiClient } from '../api/client'
import type { NewsDNA, User } from '../types/domain'

export interface UserPreferences {
  briefingFrequency: 'DAILY' | 'WEEKLY' | 'OFF'
  recommendationsEnabled: boolean
  notificationsEnabled: boolean
  topicIds: string[]
  historyEnabled: boolean
  categorySlugs: string[]
  updatedAt: string
}

export type UserPreferencesUpdate = Omit<UserPreferences, 'updatedAt'>

export async function getCurrentUser(signal?: AbortSignal): Promise<User> {
  const response = await apiClient.get<User>('/users/me', { signal })
  return response.data
}

export async function getUserPreferences(signal?: AbortSignal): Promise<UserPreferences> {
  const response = await apiClient.get<UserPreferences>('/users/me/preferences', { signal })
  return response.data
}

export async function updateUserPreferences(
  preferences: UserPreferencesUpdate,
): Promise<UserPreferences> {
  const response = await apiClient.put<UserPreferences>('/users/me/preferences', preferences)
  return response.data
}

export async function getNewsDNA(signal?: AbortSignal): Promise<NewsDNA> {
  const response = await apiClient.get<NewsDNA>('/users/me/news-dna', { signal })
  return response.data
}

export async function updateProfile(name: string): Promise<User> { return (await apiClient.put<User>('/users/me', { name })).data }
export async function recordReading(articleId: string): Promise<void> { await apiClient.post('/users/me/history', { articleId, durationSeconds: 0, completed: false }) }
export async function clearReadingHistory(): Promise<void> { await apiClient.delete('/users/me/history') }

export interface ReadingHistoryPage {
  items: Array<{ id: string; openedAt: string; article: { id: string; title: string; source: { name: string } } }>
  totalPages: number
  totalItems: number
}
export async function getReadingHistory(page: number, signal?: AbortSignal): Promise<ReadingHistoryPage> {
  return (await apiClient.get<ReadingHistoryPage>('/users/me/history', { params: { page, size: 20 }, signal })).data
}
