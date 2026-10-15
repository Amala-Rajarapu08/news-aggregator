import { useCallback, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { getArticles } from '../services/articleService'
import { getCoverage, getNewsStatus, getRecommendations } from '../services/intelligenceService'
import { useApiRequest } from '../hooks/useApiRequest'
import { usePulse } from '../hooks/usePulse'
import { ArticleCard, ArticleList, ArticleSkeleton } from '../components/editorial/ArticleComponents'
import { Button, Card, PageHeading } from '../components/ui/Primitives'
import { EmptyState, ErrorState } from '../components/ui/States'
export function ConnectedRecommendations() {
  const request = useCallback((signal: AbortSignal) => getRecommendations(signal), [])
  const result = useApiRequest(request)
  return <div className="page-stack"><PageHeading eyebrow="EXPLICIT PREFERENCES" title="Recommended for you" description="Recent articles from categories you select. Every recommendation discloses its matching preference." />
    {result.status === 'loading' || result.status === 'idle' ? <ArticleSkeleton /> : result.status === 'error' ? <ErrorState description={result.error.message} onRetry={result.retry} /> : result.data.length ? <div className="article-list">{result.data.map(item => <div key={item.article.id}><p className="recommendation-reason">{item.reason}</p><ArticleCard article={item.article} /></div>)}</div> : <EmptyState title="Choose your reading interests" description="Enable recommendations and select categories in Preferences. Matching coverage appears when those articles are in the cache." action={{ label: 'Edit preferences', href: '/preferences' }} />}
    <p className="muted small">This selection uses explicit category matches and publication order. ML ranking and inferred interests are future milestones.</p>
  </div>
}
export function ConnectedBriefing() {
  const request = useCallback((signal: AbortSignal) => getArticles({ pageSize: 10, from: new Date(Date.now() - 86400000).toISOString() }, signal), [])
  const result = useApiRequest(request)
  return <div className="page-stack"><PageHeading eyebrow="THE LAST 24 HOURS" title="Today's briefing" description="A reading digest of the latest cached stories, with publisher excerpts and original sources. No AI synthesis is generated." />{result.status === 'loading' || result.status === 'idle' ? <ArticleSkeleton /> : result.status === 'error' ? <ErrorState description={result.error.message} onRetry={result.retry} /> : <ArticleList articles={result.data.items} emptyTitle="No reporting in this window" />}</div>
}
export function ConnectedTrends() {
  const request = useCallback((signal: AbortSignal) => getCoverage(signal), [])
  const result = useApiRequest(request)
  return <div className="page-stack"><PageHeading eyebrow="OBSERVED COVERAGE" title="Coverage trends" description="Category counts in cached reporting, comparing the latest 12 hours with the preceding 12. These measure publisher coverage, not public interest." />{result.status === 'loading' || result.status === 'idle' ? <ArticleSkeleton /> : result.status === 'error' ? <ErrorState description={result.error.message} onRetry={result.retry} /> : <Card><p className="muted small">{new Date(result.data.from).toLocaleString()} to {new Date(result.data.to).toLocaleString()}. Refresh and cache limits affect available counts.</p>{result.data.categories.length ? <div className="coverage-table"><div><strong>Category</strong><strong>Previous 12h</strong><strong>Latest 12h</strong></div>{result.data.categories.map(row => <div key={row.category}><span>{row.category}</span><span>{row.previousCount}</span><strong>{row.currentCount}</strong></div>)}</div> : <EmptyState title="No observed coverage" description="Category counts will appear after RSS ingestion stores recent articles." compact />}</Card>}</div>
}
export function CopilotWorkspace() {
  const pulse = usePulse()
  useEffect(() => { pulse.setOpen(true); pulse.setExpanded(true) }, [])
  return <div className="page-stack"><PageHeading eyebrow="PULSE COPILOT" title="Your research workspace" description="Search source-attributed coverage, inspect publisher excerpts, and find headline-related candidates." /><Card><h2>Start with a source.</h2><p>Pulse Copilot is open in expanded mode. Your conversation stays available as you move through this workspace.</p><Button variant="primary" onClick={() => { pulse.setOpen(true); pulse.setExpanded(true) }}>Open research mode</Button><p className="muted small">Grounded LLM/RAG answers, verified event timelines, and perspective analysis are pending.</p></Card></div>
}
export function ConnectedSystem() {
  const request = useCallback((signal: AbortSignal) => getNewsStatus(signal), [])
  const result = useApiRequest(request)
  return <div className="page-stack"><PageHeading eyebrow="OBSERVED STATUS" title="Catalog status" description="Provider request success, persisted article counts, and per-feed ingestion diagnostics reported by the backend." />{result.status === 'loading' || result.status === 'idle' ? <ArticleSkeleton /> : result.status === 'error' ? <ErrorState description={result.error.message} onRetry={result.retry} /> : <>
    <Card><h2>{result.data.lastSuccessfulRefreshAt ? 'Last successful provider fetch' : 'No successful provider fetch recorded'}</h2><p>{result.data.lastSuccessfulRefreshAt && new Date(result.data.lastSuccessfulRefreshAt).toLocaleString()}</p>
      <div className="signal-count-row"><span>Cached articles</span><strong>{result.data.cachedArticles}</strong></div>
      <div className="signal-count-row"><span>Articles with publisher images</span><strong>{result.data.articlesWithImages}</strong></div>
      <div className="signal-count-row"><span>Last run: fetched / inserted / duplicates / invalid / failures</span><strong>{result.data.fetched} / {result.data.inserted} / {result.data.duplicatesSkipped} / {result.data.invalidSkipped} / {result.data.failures}</strong></div>
      <div className="signal-count-row"><span>Publisher image links backfilled on duplicates</span><strong>{result.data.imagesBackfilled}</strong></div>
      <p className="muted">Refresh schedule: {result.data.scheduled ? `every ${Math.round(result.data.refreshIntervalMs / 60000)} minutes` : 'manual only'}. Retention: {result.data.retentionHours} hours, at most {result.data.maxCachedArticles} articles.</p>
      <p className="muted small">Configured providers: {result.data.configuredProviders.join(', ') || 'none'}. Publisher freshness and provider-plan delays apply.</p>
      <Link to="/discover" className="text-link">Browse the catalog</Link>
    </Card>
    <Card><h2>Articles by category</h2>{Object.keys(result.data.categoryDistribution).length ? <div className="coverage-table">{Object.entries(result.data.categoryDistribution).map(([category, count]) => <div key={category}><span>{category}</span><span>Cached</span><strong>{count}</strong></div>)}</div> : <p className="muted">No articles are in the cache.</p>}</Card>
    <Card><h2>Publishers in the cache</h2>{Object.keys(result.data.publisherDistribution).length ? <div className="coverage-table">{Object.entries(result.data.publisherDistribution).map(([publisher, count]) => <div key={publisher}><span>{publisher}</span><span>Cached</span><strong>{count}</strong></div>)}</div> : <p className="muted">No publishers have articles in the cache.</p>}</Card>
    <Card><h2>Last feed run</h2>{result.data.feeds.length ? <div className="feed-status-list">{result.data.feeds.map(feed => <article key={feed.feedIdentifier}>
      <div className="feed-status-heading"><strong>{feed.feedIdentifier}</strong><span>HTTP {feed.httpStatus || 'unavailable'}</span></div>
      <p className="muted small">{feed.feedUrl} | parsed {feed.parsedItems} | accepted {feed.acceptedItems} | new articles saved {feed.savedArticles} | duplicate image links backfilled {feed.imagesBackfilled} | duplicates {feed.duplicateItems} | rejected {feed.rejectedItems} | image candidates {feed.imageCandidates} | failures {feed.failures}</p>
      {Object.keys(feed.rejectionReasons).length > 0 && <p className="muted small">Reasons: {Object.entries(feed.rejectionReasons).map(([reason, count]) => `${reason} (${count})`).join(', ')}</p>}
      {feed.lastSuccessfulAt && <p className="muted small">Last successful fetch: {new Date(feed.lastSuccessfulAt).toLocaleString()}</p>}
    </article>)}</div> : <p className="muted">No per-feed run has completed in this process yet.</p>}</Card>
  </>}</div>
}
