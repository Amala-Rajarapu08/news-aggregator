import { CATEGORIES } from '../data/articles'
import { useApp } from '../hooks/useApp'
export default function PersonalizationPanel() {
  const { unfollowed, toggleFollow } = useApp()
  return (
    <section className="card"><h2 className="h2">Personalization</h2>
      <p className="muted small">Toggle topics; the feed responds immediately.</p>
      <div className="row wrap">{CATEGORIES.slice(0, 6).map(c => <button key={c} className={`btn small-btn ${unfollowed.includes(c) ? '' : 'active'}`} aria-pressed={!unfollowed.includes(c)} onClick={() => toggleFollow(c)}>{c}</button>)}</div>
    </section>
  )
}
