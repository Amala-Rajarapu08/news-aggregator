import { articles } from '../data/articles'
import { Factor } from './WhyModal'
import NewsPulse from './NewsPulse'
import EventCluster from './EventCluster'
import PersonalizationPanel from './PersonalizationPanel'
export default function IntelligencePanel() {
  const a = articles[0]
  return (
    <aside className="rail" aria-label="Intelligence panel">
      <section className="card"><h2 className="h2">Why am I seeing this?</h2><p className="muted small">Explainable recommendation · demo values</p>{a.factors.map(f => <Factor key={f.label} {...f} />)}</section>
      <NewsPulse /><EventCluster /><PersonalizationPanel />
    </aside>
  )
}
