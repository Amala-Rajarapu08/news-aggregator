import { useEffect, useState } from 'react'
import { generateBriefing } from '../services/aiService'
import { Loading, ErrorState } from './States'
import { useApp } from '../hooks/useApp'
import { briefing as initial } from '../data/briefing'
export default function BriefingCard({ autoRun }) {
  const [b, setB] = useState(initial)
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState(null)
  const [open, setOpen] = useState(false)
  const { toast } = useApp()
  const run = () => { setLoading(true); setErr(null); generateBriefing().then(setB).catch(e => setErr(e.message)).finally(() => setLoading(false)) }
  useEffect(() => { if (autoRun) run() }, [])
  if (loading) return <Loading text="Analyzing 24 relevant articles…" />
  if (err) return <ErrorState text={err} retry={run} />
  return (
    <section className="card" aria-label="Your 60-second brief">
      <div className="row between wrap"><h2 className="h2">Your 60-Second Brief</h2><span className="chip">{b.label} · {b.sources} sources</span></div>
      <div className="cols3">
        <div><div className="eyebrow">WHAT HAPPENED</div><p className="desc">{b.happened}</p></div>
        <div><div className="eyebrow">WHY IT MATTERS</div><p className="desc">{b.matters}</p></div>
        <div><div className="eyebrow">WHAT'S NEXT (POSSIBLE, NOT CONFIRMED)</div><p className="desc">{b.next}</p></div>
      </div>
      {open && <p className="desc">{b.more}</p>}
      <p className="muted small">AI-generated summary. Read the original source for full context. (Demo content.)</p>
      <div className="row wrap">
        <button className="btn" onClick={run}>Regenerate</button>
        <button className="btn" aria-expanded={open} onClick={() => setOpen(o => !o)}>{open ? 'Collapse' : 'Expand'}</button>
        <button className="btn" onClick={() => toast('Sources: demo list')}>View Sources</button>
        <button className="btn" onClick={() => toast('Briefing saved (demo)')}>Save Briefing</button>
        <button className="btn" onClick={() => toast('Share link copied (demo)')}>Share</button>
      </div>
    </section>
  )
}
