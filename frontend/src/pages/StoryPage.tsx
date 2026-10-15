import { useCallback, useEffect, useRef } from 'react'
import { Link, useParams } from 'react-router-dom'
import { MessageSquare, ScanLine } from 'lucide-react'
import { getArticle } from '../services/articleService'
import { getRelated } from '../services/intelligenceService'
import { getUserPreferences, recordReading } from '../services/userService.ts'
import { useApiRequest } from '../hooks/useApiRequest'
import { usePulse } from '../hooks/usePulse'
import { useApp } from '../hooks/useApp'
import { ArticleActions, ArticleImage, ArticleMeta, CompactArticleCard } from '../components/editorial/ArticleComponents'
import { Button, Card, PageHeading } from '../components/ui/Primitives'
import { ErrorState, LoadingState } from '../components/ui/States'
export default function StoryPage() {
  const { articleId } = useParams(), pulse = usePulse(), { toast } = useApp()
  const articleRequest = useCallback((signal: AbortSignal) => getArticle(articleId!, signal), [articleId])
  const relatedRequest = useCallback((signal: AbortSignal) => getRelated(articleId!, signal), [articleId])
  const result = useApiRequest(articleRequest, Boolean(articleId)), related = useApiRequest(relatedRequest, Boolean(articleId))
  const recorded = useRef<string | null>(null)
  useEffect(() => {
    if (result.status !== 'success') return
    pulse.setArticle(result.data)
    if (recorded.current === result.data.id) return
    recorded.current = result.data.id
    void getUserPreferences().then(preferences => { if (preferences.historyEnabled) return recordReading(result.data.id) }).catch(error => toast(`Reading activity: ${error.message}`))
  }, [result.status, result.status === 'success' ? result.data.id : null])
  useEffect(() => () => pulse.setArticle(null), [])
  if (result.status === 'idle' || result.status === 'loading') return <LoadingState label="Loading the story" />
  if (result.status === 'error') return <ErrorState title="Story unavailable" description={result.error.message} onRetry={result.retry} />
  const article = result.data
  return <div className="page-stack reader-page"><Link className="breadcrumb-link" to="/discover">Discover / Story</Link><PageHeading eyebrow={article.category || 'ORIGINAL REPORTING'} title={article.headline} /><ArticleMeta article={article} /><div className="reader-grid"><Card className="reader-main"><ArticleImage article={article} eager /><p className="reader-article-description">{article.summary || 'No excerpt supplied. Open the original reporting below.'}</p><ArticleActions article={article} /><p className="muted small">Publisher-provided excerpt. The full article remains at {article.source.name}. {article.author && `By ${article.author}.`}</p><div className="row wrap"><Button onClick={() => pulse.setLens(article)}><ScanLine size={16} />Open Pulse Lens</Button><Button onClick={() => pulse.setOpen(true)}><MessageSquare size={16} />Ask Pulse Copilot</Button></div></Card><aside className="reader-aside"><Card><p className="eyebrow">EXPLORE FURTHER</p><h2>Related candidates</h2><p className="muted small">Shared headline keywords. Common events and perspectives are not verified.</p>{related.status === 'loading' || related.status === 'idle' ? <LoadingState label="Finding candidates" /> : related.status === 'error' ? <ErrorState description={related.error.message} onRetry={related.retry} /> : related.data.items.length ? related.data.items.map(item => <CompactArticleCard key={item.id} article={item} />) : <p className="muted small">No candidates meet the threshold.</p>}</Card></aside></div></div>
}
