import { apiClient } from '../api/client'
import type { Event } from '../types/domain'

export interface EventFeed {
  items: Event[]
  totalItems: number
}

export async function getEvents(signal?: AbortSignal): Promise<EventFeed> {
  const response = await apiClient.get<EventFeed>('/events', { signal })
  return response.data
}

export async function getEvent(eventId: string, signal?: AbortSignal): Promise<Event> {
  const response = await apiClient.get<Event>(`/events/${encodeURIComponent(eventId)}`, { signal })
  return response.data
}
