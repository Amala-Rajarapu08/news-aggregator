import { apiClient } from '../api/client'
import type { Notification } from '../types/domain'

export async function getNotifications(signal?: AbortSignal): Promise<Notification[]> {
  const response = await apiClient.get<Notification[]>('/me/notifications', { signal })
  return response.data
}
