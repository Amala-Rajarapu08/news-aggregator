import { Link } from 'react-router-dom'
import { Bookmark, Share2 } from 'lucide-react'
import { useApp } from '../hooks/useApp'
export default function NewsCard({ article: a, showWhy }) {
  const { saved, toggleSave, toast, setWhy } = useApp()
  const on = saved.includes(a.id)
  return (
    <article className="card news">
      <div className="thumb" aria-hidden="true" />
      <div className="grow">
        <div className="meta">{a.source} · {a.category} · {a.time} · {a.readMins} min read · <b>demo</b></div>
        <h3 className="h3"><Link to={`/article/${a.id}`}>{a.title}</Link></h3>
        <p className="desc">{a.description}</p>
        <div className="row wrap">
          {showWhy && <button className="btn outline" onClick={() => setWhy(a)}>Why am I seeing this?</button>}
          <button className="btn" aria-pressed={on} onClick={() => toggleSave(a.id)}><Bookmark size={16} fill={on ? 'currentColor' : 'none'} />{on ? 'Saved' : 'Bookmark'}</button>
          <button className="btn" onClick={() => toast('Share link copied (demo)')}><Share2 size={16} />Share</button>
        </div>
      </div>
    </article>
  )
}
