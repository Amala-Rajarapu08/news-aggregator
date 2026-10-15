import { apiClient } from '../api/client'
import type { Article } from '../types/domain'

export interface ArticleQuery {
  q?: string
  query?: string
  category?: string
  sourceId?: string
  from?: string
  to?: string
  page?: number
  pageSize?: number
}

export interface ArticlePage {
  items: Article[]
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export interface NewsFeedStatus {
  feedIdentifier: string
  feedUrl: string
  category?: string
  httpStatus: number
  parsedItems: number
  acceptedItems: number
  duplicateItems: number
  rejectedItems: number
  savedArticles: number
  imagesBackfilled: number
  imageCandidates: number
  failures: number
  rejectionReasons: Record<string, number>
  categoryDistribution: Record<string, number>
  lastSuccessfulAt?: string
}

export interface NewsIngestionStatus {
  lastAttemptAt?: string | null
  lastSuccessfulRefreshAt?: string | null
  fetched: number
  inserted: number
  duplicatesSkipped: number
  invalidSkipped: number
  failures: number
  imagesBackfilled: number
  scheduled: boolean
  refreshIntervalMs: number
  retentionHours: number
  maxCachedArticles: number
  cachedArticles: number
  configuredProviders: string[]
  feeds: NewsFeedStatus[]
  categoryDistribution: Record<string, number>
  publisherDistribution: Record<string, number>
  articlesWithImages: number
}

export async function getNewsIngestionStatus(signal?: AbortSignal): Promise<NewsIngestionStatus> {
  const response = await apiClient.get<NewsIngestionStatus>('/news/status', { signal })
  return response.data
}

export interface SavedArticle {
  id: string
  savedAt: string
  article: Article
}

export interface SavedArticlePage {
  items: SavedArticle[]
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export async function getArticles(params: ArticleQuery = {}, signal?: AbortSignal): Promise<ArticlePage> {
  const { query, pageSize, ...filters } = params
  const response = await apiClient.get<{
    items: CatalogArticle[]
    page: number
    pageSize: number
    totalItems: number
    totalPages: number
  }>('/articles', {
    params: { ...filters, q: filters.q ?? query, size: pageSize },
    signal,
  })
  return { ...response.data, items: response.data.items.map(toArticle) }
}

export async function getArticle(articleId: string, signal?: AbortSignal): Promise<Article> {
  const response = await apiClient.get<CatalogArticle>(`/articles/${encodeURIComponent(articleId)}`, { signal })
  return toArticle(response.data)
}

export async function getSavedArticles(
  params: { page?: number; size?: number } = {},
  signal?: AbortSignal,
): Promise<SavedArticlePage> {
  const response = await apiClient.get<{
    items: Array<{ id: string; savedAt: string; archived: boolean; article: CatalogArticle }>
    page: number
    pageSize: number
    totalItems: number
    totalPages: number
  }>('/users/me/saved', { params, signal })
  return {
    ...response.data,
    items: response.data.items.map(saved => ({
      ...saved,
      article: { ...toArticle(saved.article), archived: saved.archived },
    })),
  }
}

export async function saveArticle(articleId: string): Promise<SavedArticle> {
  const response = await apiClient.post<{ id: string; savedAt: string; archived: boolean; article: CatalogArticle }>(
    `/users/me/saved/${encodeURIComponent(articleId)}`,
  )
  return { ...response.data, article: toArticle(response.data.article) }
}

export async function removeSavedArticle(articleId: string): Promise<void> {
  await apiClient.delete(`/users/me/saved/${encodeURIComponent(articleId)}`)
}

export interface CatalogArticle {
  id: string
  title: string
  description?: string
  url: string
  imageUrl?: string
  author?: string
  publishedAt: string
  language: string
  source: { id: string; name: string; domain: string; baseUrl: string }
  category?: string
}

export function toArticle(article: CatalogArticle): Article {
  return {
    id: article.id,
    headline: article.title,
    summary: article.description,
    url: article.url,
    imageUrl: article.imageUrl,
    author: article.author,
    publishedAt: article.publishedAt,
    language: article.language,
    category: article.category,
    source: {
      id: article.source.id,
      name: article.source.name,
      domain: article.source.domain,
      homepageUrl: article.source.baseUrl,
    },
    topics: [],
  }
}
