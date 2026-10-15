import { useSearchParams } from 'react-router-dom'
import NewsCard from '../components/NewsCard'
import { Loading, Empty, ErrorState } from '../components/States'
import useAsync from '../hooks/useAsync'
import { getArticles } from '../services/newsService'
export default function Search() {
  const [p] = useSearchParams()
  const q = p.get('q') || ''
  const { data, loading, error, reload } = useAsync(() => getArticles({ q }), [q])
  return (
    <>
      <h1 className="h1">Semantic Search</h1>
      <p className="banner">Phase 1 uses simple keyword matching on mock data. True semantic search arrives with the FastAPI embeddings phase.</p>
      {q && <p className="muted">Results for "{q}"</p>}
      {loading ? <Loading /> : error ? <ErrorState text={error} retry={reload} /> : data.length ? data.map(a => <NewsCard key={a.id} article={a} />) : <Empty text="No results found." />}
    </>
  )
}
