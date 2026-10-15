import NewsCard from '../components/NewsCard'
import { Empty } from '../components/States'
import { articles } from '../data/articles'
import { useApp } from '../hooks/useApp'
export default function Saved() {
  const { saved } = useApp()
  const list = articles.filter(a => saved.includes(a.id))
  return <><h1 className="h1">Saved Articles</h1>{list.length ? list.map(a => <NewsCard key={a.id} article={a} />) : <Empty text="Your reading list is empty." />}</>
}
