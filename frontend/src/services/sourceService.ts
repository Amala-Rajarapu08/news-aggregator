import { apiClient } from '../api/client'
import type { Source } from '../types/domain'

export interface SourceCatalog {
  items: Source[]
  totalItems: number
  page: number
  totalPages: number
}

export interface SourceComparison {
  eventId: string
  articles: Array<{ source: Source; articleId: string; title: string; publishedAt: string; url: string }>
}

export async function getSources(signal?: AbortSignal, page = 0): Promise<SourceCatalog> {
  const response = await apiClient.get<{
    items: Array<{ id: string; name: string; domain: string; baseUrl: string }>
    totalItems: number
    page: number
    totalPages: number
  }>('/sources', { signal, params: { page, size: 100 } })
  return {
    items: response.data.items.map(source => ({
      id: source.id,
      name: source.name,
      domain: source.domain,
      homepageUrl: source.baseUrl,
    })),
    totalItems: response.data.totalItems,
    page: response.data.page,
    totalPages: response.data.totalPages,
  }
}

export async function getAvailableSources(category?: string, query?: string, signal?: AbortSignal): Promise<Source[]> {
  const response = await apiClient.get<Array<{ id: string; name: string; domain: string; baseUrl: string }>>(
    '/sources/available',
    { signal, params: { category, q: query } },
  )
  return response.data.map(source => ({
    id: source.id,
    name: source.name,
    domain: source.domain,
    homepageUrl: source.baseUrl,
  }))
}

export interface Category {
  id: string
  name: string
  slug: string
}

export async function getCategories(signal?: AbortSignal): Promise<Category[]> {
  const response = await apiClient.get<Category[]>('/categories', { signal })
  return response.data
}

export async function getSource(sourceId: string, signal?: AbortSignal): Promise<Source> {
  const response = await apiClient.get<{ id: string; name: string; domain: string; baseUrl: string }>(
    `/sources/${encodeURIComponent(sourceId)}`,
    { signal },
  )
  return {
    id: response.data.id,
    name: response.data.name,
    domain: response.data.domain,
    homepageUrl: response.data.baseUrl,
  }
}

export async function compareEventSources(eventId: string, signal?: AbortSignal): Promise<SourceComparison> {
  const response = await apiClient.get<SourceComparison>(
    `/events/${encodeURIComponent(eventId)}/source-comparison`,
    { signal },
  )
  return response.data
}
