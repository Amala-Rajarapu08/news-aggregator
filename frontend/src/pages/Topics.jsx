import { CATEGORIES, articles } from '../data/articles'
import { useApp } from '../hooks/useApp'
export default function Topics() {
  const { unfollowed, toggleFollow } = useApp()
  return (
    <>
      <h1 className="h1">Category Explorer</h1>
      <div className="grid2">{CATEGORIES.map(c => {
        const on = !unfollowed.includes(c)
        return <div key={c} className="card"><h3 className="h3">{c}</h3><p className="muted small">{articles.filter(a => a.category === c).length} demo articles</p>
          <div><button className={`btn ${on ? 'active' : ''}`} aria-pressed={on} onClick={() => toggleFollow(c)}>{on ? 'Following' : 'Follow'}</button></div></div>
      })}</div>
    </>
  )
}
