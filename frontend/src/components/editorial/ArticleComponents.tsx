import { useEffect, useState } from 'react'
import { Bookmark, ExternalLink, ScanLine } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Article } from '../../types/domain'
import { useLibrary } from '../../hooks/useLibrary'
import { usePulse } from '../../hooks/usePulse'
import { safeUrl } from '../../utils/urls'
import { EmptyState } from '../ui/States'
import { Button } from '../ui/Primitives'
import businessIllustration from '../../assets/category-illustrations/business.svg'
import entertainmentIllustration from '../../assets/category-illustrations/entertainment.svg'
import healthIllustration from '../../assets/category-illustrations/health.svg'
import politicsIllustration from '../../assets/category-illustrations/politics.svg'
import scienceIllustration from '../../assets/category-illustrations/science.svg'
import sportsIllustration from '../../assets/category-illustrations/sports.svg'
import technologyIllustration from '../../assets/category-illustrations/technology.svg'
import worldIllustration from '../../assets/category-illustrations/world.svg'
import generalIllustration from '../../assets/category-illustrations/general.svg'

const CATEGORY_ILLUSTRATIONS: Record<string, { label: string; image: string }> = {
  business: { label: 'Business', image: businessIllustration },
  entertainment: { label: 'Entertainment', image: entertainmentIllustration },
  health: { label: 'Health', image: healthIllustration },
  politics: { label: 'Politics', image: politicsIllustration },
  science: { label: 'Science', image: scienceIllustration },
  sports: { label: 'Sports', image: sportsIllustration },
  technology: { label: 'Technology', image: technologyIllustration },
  world: { label: 'World', image: worldIllustration },
  general: { label: 'General', image: generalIllustration },
}

export function ArticleMeta({ article }: { article: Article }) {
  const date = new Date(article.publishedAt)
  return <div className="article-meta"><span className="publisher-name">{article.source.name}</span><span aria-hidden="true"> / </span><time dateTime={article.publishedAt}>{Number.isNaN(date.getTime()) ? 'Time unavailable' : date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</time></div>
}
export function ArticleImage({ article, eager = false }: { article: Article; eager?: boolean }) {
  const [failed, setFailed] = useState(false), [loaded, setLoaded] = useState(false)
  const src = safeUrl(article.imageUrl)
  useEffect(() => { setFailed(false); setLoaded(false) }, [src])
  const category = article.category?.trim() ?? ''
  const fallbackCategory = categoryKey(category)
  const fallback = CATEGORY_ILLUSTRATIONS[fallbackCategory]
  return src && !failed
    ? <img className={`article-image${loaded ? ' is-loaded' : ''}`} src={src} alt={`${article.headline} — image from ${article.source.name}`} loading={eager ? 'eager' : 'lazy'} decoding="async" referrerPolicy="strict-origin-when-cross-origin" onLoad={() => setLoaded(true)} onError={() => setFailed(true)} />
    : <div className={`article-image-fallback category-${fallbackCategory} fallback-variant-${fallbackVariant(article)}`} role="img" aria-label={`Generic ${fallback.label} illustration; not a photograph of ${article.headline}`}>
        <img className="article-image-fallback-art" src={fallback.image} alt="" aria-hidden="true" loading={eager ? 'eager' : 'lazy'} decoding="async" />
      </div>
}

function fallbackVariant(article: Article) {
  const key = article.id || article.headline
  return Array.from(key).reduce((total, character) => total + character.charCodeAt(0), 0) % 3
}

function categoryKey(category: string) {
  const value = category.toLowerCase()
  if (value.includes('business') || value.includes('finance') || value.includes('economy')) return 'business'
  if (value.includes('entertainment') || value.includes('culture')) return 'entertainment'
  if (value.includes('technology') || value.includes('tech')) return 'technology'
  if (value.includes('science')) return 'science'
  if (value.includes('health') || value.includes('medicine')) return 'health'
  if (value.includes('sport')) return 'sports'
  if (value.includes('politic') || value.includes('government')) return 'politics'
  if (value.includes('world') || value.includes('international')) return 'world'
  return 'general'
}
export function ArticleActions({ article }: { article: Article }) {
  const library = useLibrary(), pulse = usePulse()
  const [error, setError] = useState('')
  const saved = library.isSaved(article.id)
  return <><div className="article-card-actions"><Button type="button" variant="quiet" aria-label={saved ? 'Unsave article' : 'Save article'} aria-pressed={saved} disabled={library.loading || library.pending.has(article.id)} onClick={async () => { setError(''); try { await library.toggle(article.id) } catch (err) { setError(err instanceof Error ? err.message : 'Save failed.') } }}><Bookmark size={15} fill={saved ? 'currentColor' : 'none'} />{saved ? 'Saved' : 'Save'}</Button>
    {!article.archived && <Button type="button" variant="quiet" onClick={() => pulse.setLens(article)}><ScanLine size={15} />Pulse Lens</Button>}
    {safeUrl(article.url) && <a className="source-link" href={safeUrl(article.url)} target="_blank" rel="noopener noreferrer">Read original<ExternalLink size={13} /></a>}
  </div>{error && <p className="inline-notice" role="alert">{error}</p>}</>
}
export function ArticleCard({ article }: { article: Article }) {
  return <article className="article-card"><Link className="article-visual" to={article.archived ? safeUrl(article.url) || '/saved' : `/article/${article.id}`} target={article.archived ? '_blank' : undefined} rel={article.archived ? 'noopener noreferrer' : undefined} aria-label={`Read ${article.headline}`}><ArticleImage article={article} /></Link><div className="article-card-copy"><div className="article-category">{article.archived ? 'SAVED CITATION' : article.category || 'LATEST COVERAGE'}</div><h3>{article.archived ? <a href={safeUrl(article.url)} target="_blank" rel="noopener noreferrer">{article.headline}</a> : <Link to={`/article/${article.id}`}>{article.headline}</Link>}</h3><ArticleMeta article={article} />{article.summary && <p>{article.summary}</p>}<ArticleActions article={article} /></div></article>
}
export function CompactArticleCard({ article }: { article: Article }) {
  return <article className="compact-article"><ArticleMeta article={article} /><h3><Link to={`/article/${article.id}`}>{article.headline}</Link></h3></article>
}
export function FeaturedArticleCard({ article }: { article: Article }) {
  return <article className="featured-article"><Link to={`/article/${article.id}`} className="featured-image"><ArticleImage article={article} eager /></Link><div className="featured-copy"><p className="eyebrow">LEAD STORY / {article.category || 'LATEST'}</p><h2><Link to={`/article/${article.id}`}>{article.headline}</Link></h2>{article.summary && <p className="featured-summary">{article.summary}</p>}<ArticleMeta article={article} /><ArticleActions article={article} /></div></article>
}
export function ArticleList({ articles, emptyTitle = 'No articles available' }: { articles: Article[]; emptyTitle?: string }) {
  if (!articles.length) return <EmptyState title={emptyTitle} description="No matching articles are available in the current catalog. Browse another category or check the refresh status." compact />
  return <div className="article-list">{articles.map(article => <ArticleCard key={article.id} article={article} />)}</div>
}
export function ArticleSkeleton() { return <div className="article-list" role="status" aria-label="Loading articles">{[0,1,2].map(id => <div className="article-skeleton" key={id}><div /><span /><span /><span /></div>)}</div> }
