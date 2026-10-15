import { describe, expect, it } from 'vitest'
import { testArticle } from '../test/fixtures'
import { selectBalancedEditorialArticles } from './dashboardEditorialSelection'

describe('dashboard editorial selection', () => {
  it('balances recent categories and limits a dominant publisher when alternatives exist', () => {
    const sports = Array.from({ length: 12 }, (_, index) => ({
      ...testArticle,
      id: `sports-${index}`,
      category: 'Sports',
      source: { ...testArticle.source, name: 'ESPN' },
    }))
    const alternatives = [
      { ...testArticle, id: 'tech', category: 'Technology', source: { ...testArticle.source, name: 'TechCrunch' } },
      { ...testArticle, id: 'world', category: 'World', source: { ...testArticle.source, name: 'BBC' } },
      { ...testArticle, id: 'science', category: 'Science', source: { ...testArticle.source, name: 'NASA' } },
      { ...testArticle, id: 'politics', category: 'Politics', source: { ...testArticle.source, name: 'PBS' } },
    ]

    const result = selectBalancedEditorialArticles([...sports, ...alternatives], 6)

    expect(result).toHaveLength(6)
    expect(result[0].id).toBe('sports-0')
    expect(result.filter(article => article.source.name === 'ESPN')).toHaveLength(2)
    expect(new Set(result.map(article => article.category)).size).toBe(5)
    expect(result.map(article => article.id)).toEqual(['sports-0', 'sports-1', 'tech', 'world', 'science', 'politics'])
  })

  it('keeps the available articles in API order when one category and source are all that exist', () => {
    const articles = Array.from({ length: 4 }, (_, index) => ({ ...testArticle, id: `only-${index}` }))
    expect(selectBalancedEditorialArticles(articles, 12)).toEqual(articles)
  })
})
