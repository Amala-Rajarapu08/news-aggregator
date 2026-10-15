import { apiClient } from '../api/client'
import type { Article, SemanticSearchResult, SearchResult } from '../types/domain'
import { toArticle, type ArticlePage, type ArticleQuery, type CatalogArticle } from './articleService'

export interface SearchResponse<T> {
  items: T[]
  totalItems: number
}

export interface SemanticSearchPage extends ArticlePage {
  mode: 'pgvector' | 'keyword-fallback'
  model?: string | null
  reason?: string | null
  indexedArticles: number
}

interface SearchApiItem {
  article: CatalogArticle
  relevance?: number | null
  matchedFields?: string[]
}

interface SearchApiPage {
  mode: 'pgvector' | 'keyword-fallback' | 'keyword-search-requested'
  model?: string | null
  reason?: string | null
  indexedArticles: number
  items: SearchApiItem[]
  totalItems: number
  page: number
  pageSize: number
  totalPages: number
}

interface SemanticSearchApiPage extends Omit<SearchApiPage, 'mode'> {
  mode: 'pgvector' | 'keyword-fallback'
}

export async function searchArticles(params: ArticleQuery, signal?: AbortSignal): Promise<SearchResponse<SearchResult>> {
  const { query, pageSize, ...filters } = params
  const response = await apiClient.get<SearchApiPage>('/search', {
    params: { ...filters, q: filters.q ?? query, size: pageSize },
    signal,
  })
  return {
    totalItems: response.data.totalItems,
    items: response.data.items.map(item => ({
      article: toArticle(item.article),
      matchedFields: item.matchedFields ?? [],
    })),
  }
}

export async function semanticSearch(
  query: string,
  signal?: AbortSignal,
): Promise<SemanticSearchPage> {
  const response = await apiClient.post<SemanticSearchApiPage>(
    '/search/semantic',
    { query },
    { signal },
  )
  return {
    ...response.data,
    items: response.data.items.map(item => toArticle(item.article)) as Article[],
  }
}

export async function discoverArticles(params: ArticleQuery, signal?: AbortSignal): Promise<ArticlePage> {
  const { query, pageSize, ...filters } = params
  const response = await apiClient.get<Omit<ArticlePage, 'items'> & { items: CatalogArticle[] }>('/articles', {
    params: { ...filters, q: filters.q ?? query, size: pageSize },
    signal,
  })
  return { ...response.data, items: response.data.items.map(toArticle) }
}
