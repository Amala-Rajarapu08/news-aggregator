export interface User {
  id: string
  name: string
  email: string
  role: string
  createdAt: string
}

export interface Source {
  id: string
  name: string
  homepageUrl: string
  domain?: string
}

export interface Topic {
  id: string
  name: string
  category: string
}

export interface Article {
  archived?: boolean
  id: string
  headline: string
  summary?: string
  source: Source
  publishedAt: string
  author?: string
  category?: string
  readingMinutes?: number
  imageUrl?: string
  url?: string
  language?: string
  topics: Topic[]
  body?: string[]
}

export interface Event {
  id: string
  title: string
  description: string
  category?: string
  status: 'DEVELOPING' | 'QUIET' | 'CLOSED'
  firstReportedAt: string
  latestReportedAt: string
  articleCount: number
  sourceCount: number
  sources: EventPublisher[]
  articles?: EventArticle[]
}

export interface EventPublisher {
  id: string
  name: string
  articleCount: number
}

export interface EventArticle {
  id: string
  title: string
  description?: string
  publisher: string
  publishedAt: string
  url: string
}

export interface Recommendation {
  article: Article
  reasons: RecommendationReason[]
  generatedAt: string
}

export interface RecommendationReason {
  type: 'topic' | 'source' | 'freshness' | 'reading-history' | 'similarity'
  explanation: string
}

export interface NewsDNA {
  topics: Topic[]
  sources: Source[]
  categories: string[]
  readingPatterns: string[]
  emergingInterests: Topic[]
  updatedAt: string
}

export interface Briefing {
  title: string
  generatedAt: string
  date: string
  topDevelopments: Article[]
  majorStories: Article[]
  emergingTopics: Topic[]
  events: Event[]
  sourceReferences: Citation[]
}

export interface SearchResult {
  article: Article
  matchedFields: string[]
}

export interface SemanticSearchResult extends SearchResult {
  relevance?: number
  relatedConcepts: string[]
  entities: string[]
}

export interface AssistantMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
}

export interface Citation {
  articleId: string
  title: string
  sourceName: string
  publishedAt: string
  url: string
  excerpt?: string
}

export interface ReadingActivity {
  date: string
  articlesRead: number
  minutesRead: number
}

export interface AnalyticsSummary {
  readingActivity: ReadingActivity[]
  categoryDistribution: Array<{ category: string; count: number }>
  topicDistribution: Array<{ topic: string; count: number }>
  sourceDistribution: Array<{ source: string; count: number }>
}

export interface Notification {
  id: string
  type: 'event' | 'briefing' | 'topic' | 'recommendation' | 'system'
  title: string
  createdAt: string
  read: boolean
}
