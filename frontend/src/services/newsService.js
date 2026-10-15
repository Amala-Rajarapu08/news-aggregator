import { articles } from '../data/articles'
import { delay } from './config'
// Later: axios.get('/api/news', { params }) against Spring Boot
export async function getArticles({ category, q } = {}) {
  await delay()
  return articles.filter(a => (!category || category === 'All' || a.category === category) && (!q || (a.title + a.description).toLowerCase().includes(q.toLowerCase())))
}
export async function getArticle(id) { await delay(300); return articles.find(a => a.id === Number(id)) || null }
