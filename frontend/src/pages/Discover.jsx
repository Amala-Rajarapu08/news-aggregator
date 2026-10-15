import { useState } from 'react'
import NewsCard from '../components/NewsCard'
import { Loading, Empty, ErrorState } from '../components/States'
import useAsync from '../hooks/useAsync'
import { getArticles } from '../services/newsService'
import { CATEGORIES } from '../data/articles'
export default function Discover() {
  const [cat, setCat] = useState('All')
  const { data, loading, error, reload } = useAsync(() => getArticles({ category: cat }), [cat])
  return (
    <>
      <h1 className="h1">Discover</h1>
      <div className="row wrap">{['All', ...CATEGORIES].map(c => <button key={c} aria-pressed={cat === c} className={`btn small-btn ${cat === c ? 'active' : ''}`} onClick={() => setCat(c)}>{c}</button>)}</div>
      {loading ? <Loading /> : error ? <ErrorState text={error} retry={reload} /> : data.length ? data.map(a => <NewsCard key={a.id} article={a} />) : <Empty />}
    </>
  )
}
