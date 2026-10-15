import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Bookmark } from 'lucide-react'
import useAsync from '../hooks/useAsync'
import { getArticle } from '../services/newsService'
import { summarize } from '../services/aiService'
import { Loading, Empty, ErrorState } from '../components/States'
import { useApp } from '../hooks/useApp'
export default function Article() {
  const { id } = useParams()
  const { data: a, loading, error, reload } = useAsync(() => getArticle(id), [id])
  const { saved, toggleSave } = useApp()
  const [sum, setSum] = useState(null)
  if (loading) return <Loading />
  if (error) return <ErrorState text={error} retry={reload} />
  if (!a) return <Empty text="Article not found." />
  return (
    <article>
      <Link to="/discover">← Back</Link>
      <h1 className="h1">{a.title}</h1>
      <p className="muted">{a.source} · {a.time} · {a.readMins} min read · demo article</p>
      <div className="row wrap">
        <button className="btn" aria-pressed={saved.includes(a.id)} onClick={() => toggleSave(a.id)}><Bookmark size={16} />{saved.includes(a.id) ? 'Saved' : 'Bookmark'}</button>
        <button className="btn primary" onClick={() => summarize(a).then(setSum)}>Summarize (mock)</button>
      </div>
      {sum && <div className="card"><b>AI Summary</b><ul>{sum.points.map(p => <li key={p}>{p}</li>)}</ul><p className="muted small">{sum.note}</p></div>}
      {a.body.map((p, i) => <p key={i} className="desc reader">{p}</p>)}
    </article>
  )
}
