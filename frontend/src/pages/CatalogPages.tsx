import { getTopics } from '../services/topicService'
import { getUserPreferences, updateUserPreferences } from '../services/userService.ts'
import { Bookmark, Compass, Layers3 } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArticleList, ArticleSkeleton } from '../components/editorial/ArticleComponents'
import { EmptyState, ErrorState, LoadingState } from '../components/ui/States'
import { Button, Card, Input, PageHeading } from '../components/ui/Primitives'
import { useApiRequest, type ApiRequestState } from '../hooks/useApiRequest'
import { useLibrary } from '../hooks/useLibrary'
import { getArticles, getNewsIngestionStatus, type NewsIngestionStatus } from '../services/articleService'
import { getAvailableSources, getCategories } from '../services/sourceService'

export function DiscoverPage() {
  const [query, setQuery] = useState('')
  const [searchParams, setSearchParams] = useSearchParams()
  const [category, setCategory] = useState(searchParams.get('category') ?? 'All')
  const [sourceId, setSourceId] = useState(searchParams.get('sourceId') ?? '')
  const [sourceSearch, setSourceSearch] = useState('')
  const [sourcePickerOpen, setSourcePickerOpen] = useState(false)
  const [activeSourceIndex, setActiveSourceIndex] = useState(-1)
  const [page, setPage] = useState(0)
  const activeQuery = searchParams.get('q') ?? ''
  const articleRequest = useCallback(
    (signal: AbortSignal) => getArticles({
      q: activeQuery || undefined,
      category: category === 'All' ? undefined : category,
      sourceId: sourceId || undefined,
      page,
      pageSize: 12,
    }, signal),
    [activeQuery, category, page, sourceId],
  )
  const categoriesRequest = useCallback((signal: AbortSignal) => getCategories(signal), [])
  const sourcesRequest = useCallback(
    (signal: AbortSignal) => getAvailableSources(category === 'All' ? undefined : category, activeQuery || undefined, signal),
    [activeQuery, category],
  )
  const statusRequest = useCallback((signal: AbortSignal) => getNewsIngestionStatus(signal), [])
  const articles = useApiRequest(articleRequest)
  const categories = useApiRequest(categoriesRequest)
  const sources = useApiRequest(sourcesRequest)
  const ingestionStatus = useApiRequest(statusRequest)

  useEffect(() => {
    setCategory(searchParams.get('category') ?? 'All')
    setSourceId(searchParams.get('sourceId') ?? '')
    setQuery(searchParams.get('q') ?? '')
    setSourceSearch('')
    setSourcePickerOpen(false)
    setPage(0)
  }, [searchParams])

  useEffect(() => {
    if (sources.status !== 'success' || !sourceId) return
    if (!sources.data.some(source => source.id === sourceId)) selectSource('')
  }, [sourceId, sources.status, sources.status === 'success' ? sources.data : null])

  const selectedSource = sources.status === 'success'
    ? sources.data.find(source => source.id === sourceId)
    : undefined
  const filteredSources = sources.status === 'success'
    ? sources.data
        .filter(source => source.name.toLocaleLowerCase().includes(sourceSearch.trim().toLocaleLowerCase()))
        .sort((left, right) => left.name.localeCompare(right.name, undefined, { sensitivity: 'base' }))
    : []
  const hasActiveFilters = category !== 'All' || Boolean(sourceId) || Boolean(activeQuery)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = new URLSearchParams(searchParams)
    const trimmed = query.trim()
    if (trimmed) next.set('q', trimmed)
    else next.delete('q')
    setPage(0)
    setSearchParams(next)
  }

  function selectCategory(slug: string) {
    setCategory(slug)
    setPage(0)
    const next = new URLSearchParams(searchParams)
    if (slug === 'All') next.delete('category')
    else next.set('category', slug)
    setSearchParams(next)
  }

  function selectSource(id: string) {
    setSourceId(id)
    setSourceSearch('')
    setSourcePickerOpen(false)
    setActiveSourceIndex(-1)
    setPage(0)
    const next = new URLSearchParams(searchParams)
    if (id) next.set('sourceId', id)
    else next.delete('sourceId')
    setSearchParams(next)
  }

  function clearFilters() {
    setCategory('All')
    setSourceId('')
    setQuery('')
    setSourceSearch('')
    setSourcePickerOpen(false)
    setActiveSourceIndex(-1)
    setPage(0)
    const next = new URLSearchParams(searchParams)
    next.delete('category')
    next.delete('sourceId')
    next.delete('q')
    setSearchParams(next)
  }

  function handleSourceKeyDown(event: ReactKeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      setSourcePickerOpen(false)
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setSourcePickerOpen(true)
      setActiveSourceIndex(index => Math.min(index + 1, Math.max(filteredSources.length - 1, 0)))
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveSourceIndex(index => Math.max(index - 1, 0))
      return
    }
    if (event.key === 'Enter' && sourcePickerOpen && filteredSources.length) {
      event.preventDefault()
      selectSource(filteredSources[activeSourceIndex]?.id ?? filteredSources[0].id)
    }
  }

  return (
    <div className="page-stack">
      <PageHeading eyebrow="LATEST COVERAGE" title="Discover" description="Browse recent articles stored from configured news providers, with source attribution." />
      <Card className="discover-controls">
        <form className="discover-search" onSubmit={submit}>
          <label className="sr" htmlFor="discover-query">Search coverage</label>
          <Input id="discover-query" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search a topic or headline…" />
          <Button type="submit" variant="primary" disabled={!query.trim() && !activeQuery}>Search</Button>
        </form>
        <div className="discover-shortcuts"><Link to="/categories">Browse categories <Compass size={14} /></Link></div>
      </Card>
      {categories.status === 'error' && <ErrorState title="Categories unavailable" description={categories.error.message} onRetry={categories.retry} />}
      {categories.status === 'success' && (
        <div className="row wrap category-filter-row" aria-label="Filter articles by category">
          {['All', ...categories.data.map(item => item.slug)].map(slug => (
            <Button key={slug} type="button" variant={category === slug ? 'primary' : 'secondary'} aria-pressed={category === slug} onClick={() => selectCategory(slug)}>
              {slug === 'All' ? 'All' : categories.data.find(item => item.slug === slug)?.name ?? slug}
            </Button>
          ))}
        </div>
      )}
      {sources.status === 'error' && <div className="source-catalog-notice" role="alert"><span>Publishers unavailable — {sources.error.message}</span><Button type="button" variant="quiet" onClick={sources.retry}>Retry publishers</Button></div>}
      {sources.status === 'success' && (
        <div className="source-filter-row" aria-label="Filter articles by source">
          <Button type="button" variant={sourceId ? 'secondary' : 'primary'} aria-pressed={!sourceId} onClick={() => selectSource('')}>All sources</Button>
          {sources.data.length > 0 && (
            <div className="source-picker">
              <label htmlFor="discover-source-query">Select source</label>
              <Input
                id="discover-source-query"
                role="combobox"
                aria-autocomplete="list"
                aria-expanded={sourcePickerOpen}
                aria-controls="discover-source-options"
                aria-activedescendant={sourcePickerOpen && activeSourceIndex >= 0 && filteredSources[activeSourceIndex] ? `discover-source-${filteredSources[activeSourceIndex].id}` : undefined}
                autoComplete="off"
                value={sourceSearch}
                placeholder="Search publishers…"
                onFocus={() => setSourcePickerOpen(true)}
                onChange={event => { setSourceSearch(event.target.value); setSourcePickerOpen(true); setActiveSourceIndex(-1) }}
                onKeyDown={handleSourceKeyDown}
                onBlur={() => window.setTimeout(() => setSourcePickerOpen(false), 120)}
              />
              {selectedSource && <span className="selected-source">Selected: {selectedSource.name}</span>}
              <div id="discover-source-options" className="source-picker-options" role="listbox" aria-label="Available publishers" hidden={!sourcePickerOpen}>
                {filteredSources.length
                  ? filteredSources.map(source => (
                    <div
                      id={`discover-source-${source.id}`}
                      className="source-picker-option"
                      key={source.id}
                      role="option"
                      aria-selected={source.id === sourceId}
                      tabIndex={-1}
                      onMouseDown={event => event.preventDefault()}
                      onClick={() => selectSource(source.id)}
                    >{source.name}</div>
                  ))
                  : <p className="source-picker-empty">{sourceSearch.trim() ? 'No sources match this search.' : `No publishers have articles in ${category === 'All' ? 'this catalog' : 'this category'}.`}</p>}
              </div>
            </div>
          )}
          {!sources.data.length && <span className="muted small">No publishers have articles in {category === 'All' ? 'this catalog' : 'this category'}.</span>}
          {hasActiveFilters && <Button className="clear-filter-button" type="button" variant="quiet" onClick={clearFilters}>Clear filters</Button>}
        </div>
      )}
      {articles.status === 'loading' || articles.status === 'idle'
        ? <ArticleSkeleton />
        : articles.status === 'error'
          ? <ErrorState title="Articles unavailable" description={articles.error.message} onRetry={articles.retry} />
          : <>
            {articles.data.items.length
              ? <ArticleList articles={articles.data.items} />
              : <EmptyState
                  title={emptyCoverageTitle()}
                  description={emptyCoverageDescription(ingestionStatus, category, activeQuery, sourceId)}
                  compact
                />}
            {articles.data.totalPages > 1 && (
              <div className="row wrap" aria-label="Article pages">
                <Button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</Button>
                <span className="muted small">Page {page + 1} of {articles.data.totalPages}</span>
                <Button type="button" disabled={page + 1 >= articles.data.totalPages} onClick={() => setPage(value => value + 1)}>Next</Button>
              </div>
            )}
          </>}
    </div>
  )
}

function emptyCoverageTitle() {
  return 'No matching articles'
}

function emptyCoverageDescription(
  status: ApiRequestState<NewsIngestionStatus>,
  category: string,
  query: string,
  sourceId: string,
) {
  if (status.status === 'error') return 'The article request succeeded but found no matches. PulseAI could not read feed health, so check again shortly.'
  if (status.status !== 'success') return 'No articles match these filters. Feed health is still loading.'
  const filters = [category !== 'All' ? category : '', query ? `“${query}”` : '', sourceId ? 'this publisher' : '']
    .filter(Boolean).join(', ')
  if (status.data.failures > 0 && status.data.lastAttemptAt) {
    return `No articles match ${filters || 'the current view'}. The most recent overall ingestion attempt (${new Date(status.data.lastAttemptAt).toLocaleString()}) reported ${status.data.failures} provider or feed failure${status.data.failures === 1 ? '' : 's'}; this service-wide count does not show that these filters caused the gap.`
  }
  if (!status.data.lastSuccessfulRefreshAt) {
    return `No articles match ${filters || 'the current view'}. No successful ingestion has completed; check the configured feeds and API keys.`
  }
  return `No articles match ${filters || 'the current view'}. The last successful provider fetch was ${new Date(status.data.lastSuccessfulRefreshAt).toLocaleString()}; its latest run fetched ${status.data.fetched} entries and saved ${status.data.inserted} new articles.`
}

export function SavedPage() {
  const library = useLibrary()
  return <div className="page-stack"><PageHeading eyebrow="YOUR READING LIST" title="Saved stories" description="Account-backed citations survive the temporary news cache. Older stories link directly to their publisher." />
    {library.loading ? <LoadingState label="Loading your reading list" /> : library.error ? <ErrorState description={library.error} onRetry={() => void library.reload()} /> : <ArticleList articles={library.items.map(item => item.article)} emptyTitle="Your reading list is empty" />}
  </div>
}

export function TopicsPage() {
  const request = useCallback(async (signal: AbortSignal) => { const [topics, categories, preferences] = await Promise.all([getTopics(signal), getCategories(signal), getUserPreferences(signal)]); return { topics, categories, preferences } }, [])
  const result = useApiRequest(request)
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  return <div className="page-stack"><PageHeading eyebrow="EXPLORE THE COVERAGE" title="Topics" description="Browse supported categories and follow editorial topics when they are available." />{result.status === 'loading' || result.status === 'idle' ? <LoadingState label="Loading topics" /> : result.status === 'error' ? <ErrorState description={result.error.message} onRetry={result.retry} /> : <><div className="taxonomy-grid">{result.data.categories.map(category => <Link className="taxonomy-link" to={`/discover?category=${category.slug}`} key={category.id}>{category.name}<Layers3 size={15} /></Link>)}</div>{result.data.topics.topics.length ? <div className="taxonomy-grid">{result.data.topics.topics.map(topic => <Card key={topic.id}><h2>{topic.name}</h2><Button disabled={busy} aria-pressed={result.data.preferences.topicIds.includes(topic.id)} onClick={async () => { setBusy(true); setError(''); try { const prefs=result.data.preferences; await updateUserPreferences({ ...prefs, topicIds: prefs.topicIds.includes(topic.id) ? prefs.topicIds.filter(id => id !== topic.id) : [...prefs.topicIds, topic.id] }); result.retry() } catch (err) { setError(err instanceof Error ? err.message : 'Follow failed.') } finally { setBusy(false) } }}>{result.data.preferences.topicIds.includes(topic.id) ? 'Unfollow topic' : 'Follow topic'}</Button></Card>)}</div> : <p className="muted small">Editorial topic taxonomy is empty. Automated topic extraction is a future NLP milestone; categories above are available now.</p>}</>}{error && <p role="alert" className="inline-notice">{error}</p>}</div>
}

export function CatalogPage({ page }: { page: 'discover' | 'saved' | 'topics' }) {
  if (page === 'discover') return <DiscoverPage />
  if (page === 'saved') return <SavedPage />
  return <TopicsPage />
}
