import { apiClient } from '../api/client'
import { toArticle, type CatalogArticle } from './articleService'
import type { Article } from '../types/domain'
export interface CopilotQuery { question: string; articleId?: string; pageContext: string }
export interface CopilotResult { mode: 'catalog-search' | 'publisher-excerpt' | 'related-coverage' | 'unavailable'; message: string; articles: Article[] }
export interface AssistantService { search: (query: CopilotQuery, signal?: AbortSignal) => Promise<CopilotResult> }
export const assistantService: AssistantService = { async search(query, signal) { const result = await apiClient.post<Omit<CopilotResult, 'articles'> & { articles: CatalogArticle[] }>('/assistant/search', query, { signal }); return { ...result.data, articles: result.data.articles.map(toArticle) } } }
