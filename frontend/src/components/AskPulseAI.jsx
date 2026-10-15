import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ask } from '../services/aiService'
import { Loading, ErrorState } from './States'
const SUGGEST = ['What are the biggest AI developments today?', "Summarize today's technology news.", 'Explain this news in simple terms.']
export default function AskPulseAI({ dark }) {
  const [q, setQ] = useState('')
  const [res, setRes] = useState(null)
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState(null)
  const submit = text => { if (!text.trim()) return; setQ(text); setLoading(true); setErr(null); ask(text).then(setRes).catch(e => setErr(e.message)).finally(() => setLoading(false)) }
  return (
    <section className={`card ${dark ? 'dark-card' : ''}`} aria-label="Ask PulseAI">
      <h2 className="h2">Ask PulseAI</h2>
      <form className="row" onSubmit={e => { e.preventDefault(); submit(q) }}>
        <label htmlFor="ask" className="sr">Ask</label>
        <input id="ask" className="input grow" value={q} onChange={e => setQ(e.target.value)} placeholder="Ask anything about today's news…" />
        <button className="btn primary">Ask</button>
      </form>
      <div className="row wrap">{SUGGEST.map(s => <button key={s} className="btn small-btn" onClick={() => submit(s)}>{s}</button>)}</div>
      {loading && <Loading text="Analyzing 24 relevant articles…" />}
      {err && <ErrorState text={err} retry={() => submit(q)} />}
      {res && !loading && (
        <div className="answer">
          <p>{res.answer}</p>
          <p className="muted small">Based on {res.count} demo sources · mock response</p>
          <ul>{res.sources.map(s => <li key={s.id}><Link to={`/article/${s.id}`} style={dark ? { color: '#b9ceff' } : undefined}>{s.title}</Link></li>)}</ul>
        </div>
      )}
    </section>
  )
}
