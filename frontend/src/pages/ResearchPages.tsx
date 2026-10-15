import { ConnectedAnalytics } from './AccountPages'
import { ConnectedRecommendations, CopilotWorkspace } from './IntelligencePages'
import StoryPage from './StoryPage'
import { useCallback, useEffect, useState } from 'react'
import { ArrowRight, Bookmark, BrainCircuit, Clock3, Command, ExternalLink, FileSearch, MessageSquareText, Search as SearchIcon, Send, Share2, ShieldAlert, Sparkles } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Badge, Button, Card, Input, PageHeading } from '../components/ui/Primitives'
import { EmptyState, ErrorState, LoadingState, PlannedState } from '../components/ui/States'
import { ArticleList } from '../components/editorial/ArticleComponents'
import { useApiRequest } from '../hooks/useApiRequest'
import { getArticle, getArticles } from '../services/articleService'
import { getEvent, getEvents } from '../services/eventService'
import { semanticSearch, type SemanticSearchPage } from '../services/searchService'
import type { Event as EventData } from '../types/domain'

export function SemanticSearchPage({ keyword = false }: { keyword?: boolean }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialQuery = searchParams.get('q') ?? ''
  const [query, setQuery] = useState(initialQuery)
  const [submitted, setSubmitted] = useState(Boolean(initialQuery))
  const semantic = !keyword
  type SearchPage = SemanticSearchPage & { items: import('../types/domain').Article[] }
  const searchRequest = useCallback(
    (signal: AbortSignal): Promise<SearchPage> => semantic
      ? semanticSearch(query.trim(), signal)
      : getArticles({ q: query.trim(), page: 0, pageSize: 20 }, signal)
          .then(result => ({ ...result, mode: 'keyword-fallback' as const, indexedArticles: 0 })),
    [query, semantic],
  )
  const results = useApiRequest(searchRequest, submitted && Boolean(query.trim()))
  const vectorSearchActive = results.status === 'success' && results.data.mode === 'pgvector'
  useEffect(() => {
    const routeQuery = searchParams.get('q') ?? ''
    setQuery(routeQuery)
    setSubmitted(Boolean(routeQuery))
  }, [searchParams])
  return (
    <div className="page-stack">
      <PageHeading
        eyebrow={semantic ? (vectorSearchActive ? 'PGVECTOR SEMANTIC SEARCH' : 'MEANING-BASED SEARCH') : 'ARTICLE SEARCH'}
        title={semantic ? 'Semantic search' : 'Search'}
        description={semantic
          ? 'Search articles by meaning. Each result identifies whether it came from pgvector similarity or the keyword fallback.'
          : 'Search stored article titles and descriptions by keyword.'}
      />
      <Card className={`search-workspace${semantic ? ' search-workspace-semantic' : ''}`}>
        <div className="search-mode"><span className="search-mode-icon">{semantic ? <BrainCircuit size={18} /> : <SearchIcon size={18} />}</span><div><b>{semantic ? (vectorSearchActive ? 'Embedding similarity' : 'Semantic search') : 'Search coverage'}</b><p>{semantic ? 'The server reports the retrieval mode and model used for each submitted query.' : 'Find words in stored article titles, descriptions, publishers, and categories.'}</p></div></div>
        <form className="search-form" onSubmit={event => { event.preventDefault(); const trimmed = query.trim(); setSearchParams(trimmed ? { q: trimmed } : {}); setSubmitted(Boolean(trimmed)) }}>
          <label htmlFor="article-search" className="sr">Search articles</label>
          <Input id="article-search" value={query} onChange={event => { setQuery(event.target.value); setSubmitted(false) }} placeholder={semantic ? 'Search a headline, publisher, or category…' : 'Search headlines, publishers, or topics…'} />
          <Button type="submit" variant="primary" disabled={!query.trim()}><SearchIcon size={16} />{semantic ? 'Search catalog' : 'Search'}</Button>
        </form>
        {semantic && results.status === 'success' && (vectorSearchActive
          ? <p className="search-caveat"><BrainCircuit size={14} />pgvector similarity using {results.data.model}; {results.data.indexedArticles} articles are indexed.</p>
          : <p className="search-caveat"><ShieldAlert size={14} />Keyword fallback: {fallbackExplanation(results.data.reason)} Results match article and publisher text, not meaning.</p>)}
        <div className="suggestion-row"><span>Try a search:</span>{(semantic ? ['NASA', 'Space exploration', 'Artificial intelligence', 'Climate change'] : ['AI policy', 'climate research', 'central bank']).map(item => <button key={item} type="button" className="query-chip" onClick={() => { setQuery(item); setSubmitted(false) }}>{item}</button>)}</div>
      </Card>
      <div className="search-results-heading"><h2>{semantic ? (submitted ? (vectorSearchActive ? 'Semantic results' : 'Keyword fallback results') : 'Semantic search') : (submitted ? 'Results' : 'Search results')}</h2><span className="muted small">{submitted && query ? `For “${query}”` : 'Waiting for a query'}</span></div>
      {!submitted
        ? <EmptyState title="Enter a search query" description={semantic ? 'The result will report whether it used pgvector similarity or keyword matching.' : 'Search stored article text and publisher metadata.'} />
        : results.status === 'loading' || results.status === 'idle'
          ? <LoadingState label={semantic ? 'Searching indexed coverage' : 'Searching articles'} />
          : results.status === 'error'
            ? <ErrorState title="Search unavailable" description={results.error.message} onRetry={results.retry} />
            : <ArticleList articles={results.data.items} emptyTitle={semantic ? 'No keyword matches in the catalog' : 'No matching articles'} />}
    </div>
  )
}

function fallbackExplanation(reason?: string | null) {
  if (reason === 'pgvector-unavailable') return 'pgvector storage is not available.'
  if (reason === 'embedding-index-empty') return 'article embeddings are not indexed yet.'
  if (reason === 'embedding-service-unavailable') return 'the embedding service is unavailable.'
  return 'semantic retrieval was not available.'
}

export function EventsPage() {
  const { eventId } = useParams()
  const eventRequest = useCallback((signal: AbortSignal): Promise<EventData | { items: EventData[]; totalItems: number }> =>
    eventId ? getEvent(eventId, signal) : getEvents(signal), [eventId])
  const result = useApiRequest(eventRequest)
  return (
    <div className="page-stack">
      <PageHeading eyebrow={eventId ? 'EVENT INTELLIGENCE' : 'DEVELOPING STORIES'} title={eventId ? 'Event detail' : 'Events'} description="Follow how coverage develops over time, with each update tied to its source." />
      {eventId && <Link className="breadcrumb-link" to="/events"><span>←</span> All events</Link>}
      {result.status === 'loading' || result.status === 'idle'
        ? <LoadingState label={eventId ? 'Loading event coverage' : 'Loading developing events'} />
        : result.status === 'error'
          ? <ErrorState title={eventId ? 'Event unavailable' : 'Events unavailable'} description={result.error.message} onRetry={result.retry} />
          : eventId
            ? ('items' in result.data
                ? <LoadingState label="Loading event coverage" />
                : <EventDetail event={result.data} />)
            : ('items' in result.data
                ? <EventFeed items={result.data.items} />
                : <LoadingState label="Loading developing events" />)}
    </div>
  )
}

function EventFeed({ items }: { items: EventData[] }) {
  if (!items.length) return <EmptyState title="No multi-article events yet" description="Events appear when multiple stored articles share a category, headline entities, and close publication dates. Individual articles stay in the news catalog." />
  return <div className="event-list" aria-label="Developing events">{items.map(event => (
    <Link key={event.id} className="ui-card event-card" to={`/events/${event.id}`} aria-label={`Open event: ${event.title}`}>
      <div className="event-card-top"><span className="eyebrow">{event.category || 'NEWS COVERAGE'}</span><Badge tone={event.status === 'DEVELOPING' ? 'blue' : 'neutral'}>{event.status === 'DEVELOPING' ? 'DEVELOPING' : 'QUIET'}</Badge></div>
      <h2>{event.title}</h2>
      {event.description && <p className="event-card-summary">{event.description.split('\n\n')[0]}</p>}
      <div className="event-card-meta"><span><b>{event.articleCount}</b> related {event.articleCount === 1 ? 'article' : 'articles'}</span><span><b>{event.sourceCount}</b> {event.sourceCount === 1 ? 'publisher' : 'publishers'}</span></div>
      <div className="event-card-dates"><span>First: {formatEventDate(event.firstReportedAt)}</span><span>Latest: {formatEventDate(event.latestReportedAt)}</span></div>
      <div className="event-source-list">{event.sources.map(source => <span key={source.id}>{source.name}</span>)}</div>
      <span className="event-card-open">View timeline <ArrowRight size={14} /></span>
    </Link>
  ))}</div>
}

function EventDetail({ event }: { event: EventData }) {
  const articles = event.articles ?? []
  return <div className="event-detail-layout">
    <main className="event-detail-main">
      <Card className="event-overview-card">
        <div className="event-card-top"><span className="eyebrow">{event.category || 'NEWS COVERAGE'}</span><Badge tone={event.status === 'DEVELOPING' ? 'blue' : 'neutral'}>{event.status === 'DEVELOPING' ? 'DEVELOPING' : 'QUIET'}</Badge></div>
        <h2>{event.title}</h2>
        <p className="event-overview-label">Publisher supplied coverage summaries</p>
        <p className="event-overview">{event.description || 'No article summaries were supplied by the publishers.'}</p>
        <div className="event-card-meta"><span><b>{event.articleCount}</b> related {event.articleCount === 1 ? 'article' : 'articles'}</span><span><b>{event.sourceCount}</b> {event.sourceCount === 1 ? 'publisher' : 'publishers'}</span></div>
        <div className="event-card-dates"><span>First reported {formatEventDate(event.firstReportedAt)}</span><span>Latest report {formatEventDate(event.latestReportedAt)}</span></div>
      </Card>
      <Card className="event-reports-card">
        <div className="section-heading-inline"><div><p className="eyebrow">CHRONOLOGICAL COVERAGE</p><h2>Reporting timeline</h2></div><span className="muted small">{articles.length} articles</span></div>
        <ol className="event-report-list">{articles.map(article => <li key={article.id} className="event-report">
          <time dateTime={article.publishedAt}>{formatEventDate(article.publishedAt)}</time>
          <p className="event-report-publisher">{article.publisher}</p>
          <h3><Link to={`/article/${article.id}`}>{article.title}</Link></h3>
          {article.description && <p className="event-report-description">{article.description}</p>}
          <a className="event-original-link" href={article.url} target="_blank" rel="noopener noreferrer">Read at {article.publisher}<ExternalLink size={13} /></a>
        </li>)}</ol>
      </Card>
    </main>
    <aside className="event-detail-aside">
      <Card><p className="eyebrow">PUBLISHERS</p><h2>Coverage sources</h2><ul className="event-publisher-list">{(event.sources ?? []).map(source => <li key={source.id}><span>{source.name}</span><b>{source.articleCount}</b></li>)}</ul></Card>
      <Card><p className="eyebrow">COVERAGE WINDOW</p><h2>Reported dates</h2><p className="event-date-value">First report<time dateTime={event.firstReportedAt}>{formatEventDate(event.firstReportedAt)}</time></p><p className="event-date-value">Latest report<time dateTime={event.latestReportedAt}>{formatEventDate(event.latestReportedAt)}</time></p></Card>
    </aside>
  </div>
}

function formatEventDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function PlannedPage({ title, description }: { title: string; description: string }) {
  return <div className="page-stack"><PageHeading eyebrow="PULSEAI INTELLIGENCE" title={title} description={description} /><EmptyState title={`${title} is not connected yet`} description="This area will be available when its real data and service are implemented. No sample metrics or invented content are shown." /></div>
}

export function ResearchPage({ page }: { page: 'recommendations' | 'search' | 'semantic-search' | 'assistant' | 'news-dna' | 'events' | 'article' }) {
  if (page === 'recommendations') return <ConnectedRecommendations />
  if (page === 'search') return <SemanticSearchPage keyword />
  if (page === 'semantic-search') return <SemanticSearchPage />
  if (page === 'assistant') return <CopilotWorkspace />
  if (page === 'news-dna') return <ConnectedAnalytics dna />
  if (page === 'events') return <EventsPage />
  return <StoryPage />
}
