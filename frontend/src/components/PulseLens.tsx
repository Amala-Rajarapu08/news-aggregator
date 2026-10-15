import { useCallback, useEffect, useRef } from 'react'
import { ArrowUpRight, MessageSquare, ScanLine, X } from 'lucide-react'
import { usePulse } from '../hooks/usePulse'
import { useApiRequest } from '../hooks/useApiRequest'
import { getRelated } from '../services/intelligenceService'
import { safeUrl } from '../utils/urls'
import { ArticleImage, ArticleMeta, CompactArticleCard } from './editorial/ArticleComponents'
import { Button } from './ui/Primitives'
import { ErrorState, LoadingState } from './ui/States'
export default function PulseLens() {
  const pulse = usePulse(), article = pulse.lens
  const request = useCallback((signal: AbortSignal) => getRelated(article!.id, signal), [article?.id])
  const related = useApiRequest(request, Boolean(article))
  const panel = useRef<HTMLElement>(null), returnFocus = useRef<HTMLElement | null>(null)
  useEffect(() => {
    if (!article) return
    returnFocus.current = document.activeElement as HTMLElement
    panel.current?.focus()
    const previous = document.body.style.overflow; document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previous; returnFocus.current?.focus() }
  }, [article?.id])
  if (!article) return null
  return <div className="lens-overlay" onClick={() => pulse.setLens(null)}><section className="lens-panel" ref={panel} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="lens-title" onClick={event => event.stopPropagation()} onKeyDown={event => {
    if (event.key === 'Escape') pulse.setLens(null)
    if (event.key === 'Tab') {
      const elements = panel.current?.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input, [tabindex="0"]')
      if (!elements?.length) return
      const first = elements[0], last = elements[elements.length - 1]
      if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.current)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
  }}><header className="lens-header"><span><ScanLine size={20} />PULSE LENS</span><button type="button" aria-label="Close Pulse Lens" onClick={() => pulse.setLens(null)}><X size={21} /></button></header>
    <ArticleImage article={article} eager /><div className="lens-body"><p className="eyebrow">{article.category || 'SOURCE-ATTRIBUTED REPORTING'}</p><h1 id="lens-title">{article.headline}</h1><ArticleMeta article={article} /><p className="lens-excerpt">{article.summary || 'The publisher supplied no excerpt. Read the original for the full story.'}</p><div className="row wrap">{safeUrl(article.url) && <a className="ui-button ui-button-primary" href={safeUrl(article.url)} target="_blank" rel="noopener noreferrer">Read at {article.source.name}<ArrowUpRight size={15} /></a>}<Button type="button" onClick={() => { pulse.setArticle(article); pulse.setLens(null); pulse.setOpen(true) }}><MessageSquare size={15} />Ask Pulse Copilot</Button></div>
    <section className="lens-section"><p className="eyebrow">KEEP EXPLORING</p><h2>Related coverage candidates</h2><p className="muted small">Shared headline keywords across the latest 100 cached stories. These have not been verified as the same event.</p>{related.status === 'loading' || related.status === 'idle' ? <LoadingState label="Finding coverage" /> : related.status === 'error' ? <ErrorState description={related.error.message} onRetry={related.retry} /> : related.data.items.length ? related.data.items.map(item => <div key={item.id} onClick={() => pulse.setLens(null)}><CompactArticleCard article={item} /></div>) : <p className="muted small">No candidates meet the keyword threshold in the current cache.</p>}</section>
    <section className="lens-section lens-future"><h2>Intelligence, with evidence.</h2><p>Why it matters / Timeline / Perspectives / Related entities</p><small>Grounded analysis and verified event grouping are pending. The excerpt above is supplied by the publisher.</small></section></div>
  </section></div>
}
