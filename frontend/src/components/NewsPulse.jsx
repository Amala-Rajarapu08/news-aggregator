import { trends } from '../data/trends'
export default function NewsPulse() {
  return (
    <section className="card"><h2 className="h2">News Pulse</h2>
      {trends.map(t => <div key={t.topic} className="row between small"><span>{t.topic}</span><b>{t.delta > 0 ? '▲' : '▼'} {Math.abs(t.delta)}%</b></div>)}
      <p className="muted small">Demo values. Arrows and text carry meaning, not color alone.</p>
    </section>
  )
}
