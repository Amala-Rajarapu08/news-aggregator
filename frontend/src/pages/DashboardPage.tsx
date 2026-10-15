import { useCallback } from 'react'
import { ArrowRight, Activity, ScanLine } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Card, PageHeading } from '../components/ui/Primitives'
import { ArticleList, FeaturedArticleCard, CompactArticleCard, ArticleSkeleton } from '../components/editorial/ArticleComponents'
import { ErrorState } from '../components/ui/States'
import { useApiRequest } from '../hooks/useApiRequest'
import { useAuth } from '../hooks/useAuth'
import { usePulse } from '../hooks/usePulse'
import { safeUrl } from '../utils/urls'
import { getArticles, type ArticlePage } from '../services/articleService'
import { getCategories, getSources } from '../services/sourceService'
import { getNewsStatus, getCoverage } from '../services/intelligenceService'
import { selectBalancedEditorialArticles } from './dashboardEditorialSelection'
export default function DashboardPage() {
  const { user } = useAuth(), pulse = usePulse()
  const articleRequest = useCallback(async (signal: AbortSignal) => {
    const pages = [await getArticles({ page: 0, pageSize: 100 }, signal)]
    const categories = new Set(pages[0].items.map(article => article.category?.trim().toLowerCase()).filter(Boolean))
    const pageLimit = Math.min(4, pages[0].totalPages)
    for (let page = 1; page < pageLimit && categories.size < 9; page++) {
      let nextPage: ArticlePage
      try {
        nextPage = await getArticles({ page, pageSize: 100 }, signal)
      } catch (error) {
        if (signal.aborted) throw error
        break
      }
      pages.push(nextPage)
      nextPage.items.forEach(article => {
        const category = article.category?.trim().toLowerCase()
        if (category) categories.add(category)
      })
    }
    return { ...pages[0], items: pages.flatMap(page => page.items) }
  }, [])
  const catalogRequest = useCallback(async (signal: AbortSignal) => { const [categories, sources, status, coverage] = await Promise.all([getCategories(signal), getSources(signal), getNewsStatus(signal), getCoverage(signal)]); return { categories, sources, status, coverage } }, [])
  const articles = useApiRequest(articleRequest), catalog = useApiRequest(catalogRequest)
  const items = articles.status === 'success' ? selectBalancedEditorialArticles(articles.data.items, 12) : []
  const lead = items.find(article => safeUrl(article.imageUrl)) || items[0]
  const remaining = items.filter(article => article.id !== lead?.id)
  return <div className="dashboard-page page-stack"><div className="editorial-masthead"><PageHeading eyebrow="THE PULSE / DAILY EDITION" title="The world, in perspective." description={`Welcome, ${user?.name.split(' ')[0] || 'reader'}. Understand the news, not just the headlines.`} /><time>{new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}</time></div>
    <div className="editorial-status"><span className="status-dot" />{catalog.status === 'success' ? catalog.data.status.lastSuccessfulRefreshAt ? `Last successful provider fetch: ${new Date(catalog.data.status.lastSuccessfulRefreshAt).toLocaleString()} | latest run: ${catalog.data.status.fetched} fetched, ${catalog.data.status.inserted} new articles saved, ${catalog.data.status.failures} failures` : 'No successful provider fetch recorded yet' : 'Checking catalog freshness'}<Link to="/system">Refresh details <ArrowRight size={12} /></Link></div>
    {catalog.status === 'error' && <ErrorState title="Catalog details unavailable" description={catalog.error.message} onRetry={catalog.retry} />}
    {catalog.status === 'success' && <nav className="editorial-categories" aria-label="Explore news categories"><Link to="/discover" className="selected">All coverage</Link>{catalog.data.categories.map(category => <Link key={category.id} to={`/discover?category=${category.slug}`}>{category.name}</Link>)}</nav>}
    {articles.status === 'loading' || articles.status === 'idle' ? <ArticleSkeleton /> : articles.status === 'error' ? <ErrorState title="News unavailable" description={articles.error.message} onRetry={articles.retry} /> : articles.data.items.length ? <><div className="lead-story-grid"><FeaturedArticleCard article={lead} /><aside className="headline-rail"><p className="eyebrow">THE LATEST</p>{remaining.slice(0,4).map(article => <CompactArticleCard key={article.id} article={article} />)}<Link className="text-link" to="/discover">All headlines <ArrowRight size={14} /></Link></aside></div><div className="section-heading-inline"><div><p className="eyebrow">KEEP READING</p><h2>Across the newsroom.</h2></div><Link className="text-link" to="/discover">Discover more <ArrowRight size={14} /></Link></div><ArticleList articles={remaining.slice(4).length ? remaining.slice(4) : remaining} /></> : <Card className="catalog-first-run"><p className="eyebrow">YOUR NEWS DESK IS READY</p><h2>Connect reporting to begin.</h2><p>No articles are cached yet. PulseAI shows source-provided headlines once the configured RSS feeds refresh.</p><Link className="ui-button ui-button-primary" to="/system">View refresh status <ArrowRight size={15} /></Link></Card>}
    <div className="dashboard-intelligence"><Card className="copilot-entry"><Activity size={27} strokeWidth={1.4} /><p className="eyebrow">FOLLOW YOUR CURIOSITY</p><h2>A question is a starting point.</h2><p>Find coverage and inspect publisher excerpts with Pulse Copilot.</p><button className="ui-button ui-button-primary" type="button" onClick={() => pulse.setOpen(true)}>Open Pulse Copilot <ArrowRight size={15} /></button></Card><Card><ScanLine size={25} strokeWidth={1.4} /><p className="eyebrow">LOOK A LITTLE CLOSER</p><h2>See a story through Pulse Lens.</h2><p>Open Pulse Lens on any story for its source, publication time, excerpt, and related headline candidates.</p><Link className="text-link" to="/discover">Explore reporting <ArrowRight size={15} /></Link></Card></div>
    {catalog.status === 'success' && <div className="dashboard-intelligence"><Card><p className="eyebrow">COVERAGE SNAPSHOT / 24 HOURS</p><h2>Across the categories.</h2>{catalog.data.coverage.categories.length ? catalog.data.coverage.categories.map(row => <div className="signal-count-row" key={row.category}><span>{row.category}</span><strong>{row.total} articles</strong></div>) : <p className="muted small">Counts appear after articles are ingested.</p>}<Link to="/trends" className="text-link">Explore the observation window <ArrowRight size={14} /></Link></Card><Card><p className="eyebrow">ORIGINAL VOICES</p><h2>Explore publishers.</h2><div className="publisher-chips">{catalog.data.sources.items.slice(0,8).map(source => <Link to={`/discover?sourceId=${source.id}`} key={source.id}><span>{source.name.charAt(0)}</span>{source.name}<ArrowRight size={13} /></Link>)}</div>{!catalog.data.sources.items.length && <p className="muted small">Publisher entries appear with ingested reporting.</p>}</Card></div>}
  </div>
}
