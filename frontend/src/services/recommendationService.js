import { articles } from '../data/articles'
import { recommendedIds } from '../data/recommendations'
import { delay } from './config'
// Later: GET /api/recommendations
export async function getRecommendations() { await delay(); return recommendedIds.map(id => articles.find(a => a.id === id)) }
