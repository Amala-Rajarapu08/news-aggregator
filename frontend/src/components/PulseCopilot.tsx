import { useEffect, useRef, useState } from 'react'
import { Activity, ArrowUpRight, Maximize2, Minimize2, Minus, Send, X } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { usePulse } from '../hooks/usePulse'
import { assistantService } from '../services/assistantService'
import { safeUrl } from '../utils/urls'
import { Button, Input } from './ui/Primitives'
export default function PulseCopilot() {
  const pulse = usePulse(), location = useLocation()
  const [question, setQuestion] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const launcher = useRef<HTMLButtonElement>(null), log = useRef<HTMLDivElement>(null)
  const request = useRef<AbortController | null>(null), inFlight = useRef(false)
  const context = pulse.lens ?? pulse.article
  useEffect(() => { if (pulse.open) document.getElementById('copilot-question')?.focus() }, [pulse.open])
  useEffect(() => { log.current?.scrollTo?.({ top: log.current.scrollHeight }) }, [pulse.messages, busy])
  useEffect(() => () => request.current?.abort(), [])
  function close() { pulse.setOpen(false); launcher.current?.focus() }
  async function submit(text: string) {
    if (!text.trim() || inFlight.current) return
    inFlight.current = true; setQuestion(''); setError(''); setBusy(true)
    pulse.setMessages(current => [...current, { id: Date.now(), role: 'user', text }])
    const controller = new AbortController(); request.current = controller
    try {
      const result = await assistantService.search({ question: text.trim(), articleId: context?.id, pageContext: location.pathname }, controller.signal)
      pulse.setMessages(current => [...current, { id: Date.now(), role: 'assistant', text: result.message, result }])
    } catch (err) { if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Search unavailable.') }
    finally { if (!controller.signal.aborted) setBusy(false); inFlight.current = false }
  }
  const suggestions = context ? ['Show the publisher excerpt', 'Find related coverage', 'Why does this matter?'] : location.pathname === '/discover' ? ['Major technology headlines', 'News about artificial intelligence', 'Latest world headlines'] : ['Latest headlines', 'Major science headlines', 'News about artificial intelligence']
  return <>
    <button ref={launcher} type="button" className="copilot-launcher" aria-label="Open Pulse Copilot" aria-expanded={pulse.open} aria-controls="pulse-copilot" onClick={() => pulse.setOpen(!pulse.open)}><Activity size={22} /><span>Pulse Copilot</span><span className="copilot-dot" /></button>
    {pulse.open && <section id="pulse-copilot" className={`copilot-panel${pulse.expanded ? ' copilot-expanded' : ''}`} role="dialog" aria-label="Pulse Copilot" onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); close() } }}>
      <header className="copilot-header"><Activity size={22} /><div><strong>Pulse Copilot</strong><small>Search & source discovery</small></div><button type="button" aria-label={pulse.expanded ? 'Reduce Copilot' : 'Expand Copilot'} onClick={() => pulse.setExpanded(!pulse.expanded)}>{pulse.expanded ? <Minimize2 size={16} /> : <Maximize2 size={16} />}</button><button type="button" aria-label="Minimize Copilot" onClick={close}><Minus size={17} /></button><button type="button" aria-label="Close Copilot" onClick={close}><X size={18} /></button></header>
      <div className="copilot-context">{context ? <>Story context: <strong>{context.headline}</strong></> : <>Workspace: <strong>{location.pathname.split('/')[1] || 'dashboard'}</strong></>}{context && <button className="text-button" type="button" onClick={() => { pulse.setArticle(null); pulse.setLens(null) }}>Clear story context</button>}</div>
      <div className="copilot-log" ref={log} role="log" aria-live="polite">
        {!pulse.messages.length && <div className="copilot-intro"><p className="eyebrow">FOLLOW THE SIGNAL</p><h2>A clearer starting point.</h2><p>Search current coverage, open original sources, or inspect a publisher excerpt.</p><small>Grounded AI analysis is a future feature. No generated news answers are shown.</small></div>}
        {pulse.messages.map(message => <div key={`${message.role}-${message.id}`} className={`copilot-message copilot-message-${message.role}`}><span className="eyebrow">{message.role === 'user' ? 'YOU' : 'PULSE'}</span><p>{message.text}</p>{message.result?.articles.map(article => <article className="copilot-citation" key={article.id}><Link to={`/article/${article.id}`} onClick={() => { pulse.setLens(null) }}>{article.headline}</Link><small>{article.source.name} / {new Date(article.publishedAt).toLocaleString()}</small>{message.result?.mode === 'publisher-excerpt' && <p>{article.summary ?? 'The publisher supplied no excerpt.'}</p>}{safeUrl(article.url) && <a href={safeUrl(article.url)} target="_blank" rel="noopener noreferrer">Original reporting <ArrowUpRight size={13} /></a>}</article>)}{message.result && message.result.mode !== 'unavailable' && !message.result.articles.length && <small>No matching articles in the current cache.</small>}</div>)}
        {busy && <p role="status" className="copilot-loading">Searching stored coverage...</p>}{error && <p role="alert" className="inline-notice">{error} Please try again.</p>}
      </div>
      <div className="copilot-suggestions">{suggestions.map(text => <button type="button" key={text} disabled={busy} onClick={() => void submit(text)}>{text}</button>)}</div>
      <form className="copilot-composer" onSubmit={event => { event.preventDefault(); void submit(question) }}><label className="sr" htmlFor="copilot-question">Search with Pulse Copilot</label><Input id="copilot-question" value={question} onChange={event => setQuestion(event.target.value)} placeholder="Find a topic or ask about this story..." maxLength={500} /><Button type="submit" variant="primary" disabled={busy || !question.trim()} aria-label="Send to Copilot"><Send size={16} /></Button></form>
      <p className="copilot-privacy">Session-only conversation. No private profile is sent to an AI provider.</p>
    </section>}
  </>
}
