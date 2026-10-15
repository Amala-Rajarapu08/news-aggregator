import { apiClient } from '../api/client'
import { toArticle, type CatalogArticle, type NewsIngestionStatus } from './articleService'
export type NewsStatus = NewsIngestionStatus
export interface Coverage { from: string; midpoint: string; to: string; categories: Array<{ category: string; total: number; currentCount: number; previousCount: number }> }
export interface Activity { from: string; to: string; articleOpenings: number; categories: Array<{ name: string; count: number }>; sources: Array<{ name: string; count: number }> }
export async function getNewsStatus(signal?: AbortSignal): Promise<NewsStatus> { return (await apiClient.get('/news/status', { signal })).data }
export async function getCoverage(signal?: AbortSignal): Promise<Coverage> { return (await apiClient.get('/news/coverage', { signal })).data }
export async function getActivity(signal?: AbortSignal): Promise<Activity> { return (await apiClient.get('/users/me/analytics', { signal })).data }
export async function getRecommendations(signal?: AbortSignal) { const response = await apiClient.get<Array<{ article: CatalogArticle; reason: string }>>('/users/me/recommendations', { signal }); return response.data.map(item => ({ ...item, article: toArticle(item.article) })) }
export async function getRelated(id: string, signal?: AbortSignal) { const response = await apiClient.get<{ method: string; items: CatalogArticle[] }>(`/articles/${encodeURIComponent(id)}/related`, { signal }); return { ...response.data, items: response.data.items.map(toArticle) } }
