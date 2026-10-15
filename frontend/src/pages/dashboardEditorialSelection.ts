import type { Article } from '../types/domain'

/**
 * Pick a fresh, category- and publisher-balanced sample from newest-first results.
 * The chosen articles retain their original order and always come from the catalog.
 */
export function selectBalancedEditorialArticles(articles: Article[], limit = 12): Article[] {
  const remaining = articles.map((article, index) => ({ article, index }))
  const selected: typeof remaining = []
  const categoryCounts = new Map<string, number>()
  const publisherCounts = new Map<string, number>()
  const publisherLimit = Math.max(2, Math.ceil(limit / 6))

  while (remaining.length && selected.length < limit) {
    const publisherEligible = remaining.filter(({ article }) =>
      (publisherCounts.get(publisherKey(article)) ?? 0) < publisherLimit,
    )
    const candidates = publisherEligible.length ? publisherEligible : remaining
    candidates.sort((left, right) => {
      const categoryDifference = (categoryCounts.get(categoryKey(left.article)) ?? 0)
        - (categoryCounts.get(categoryKey(right.article)) ?? 0)
      if (categoryDifference) return categoryDifference

      const publisherDifference = (publisherCounts.get(publisherKey(left.article)) ?? 0)
        - (publisherCounts.get(publisherKey(right.article)) ?? 0)
      return publisherDifference || left.index - right.index
    })

    const next = candidates[0]
    remaining.splice(remaining.indexOf(next), 1)
    selected.push(next)
    categoryCounts.set(categoryKey(next.article), (categoryCounts.get(categoryKey(next.article)) ?? 0) + 1)
    publisherCounts.set(publisherKey(next.article), (publisherCounts.get(publisherKey(next.article)) ?? 0) + 1)
  }

  return selected.sort((left, right) => left.index - right.index).map(({ article }) => article)
}

function categoryKey(article: Article) {
  return article.category?.trim().toLocaleLowerCase() || 'uncategorized'
}

function publisherKey(article: Article) {
  return article.source.name.trim().toLocaleLowerCase() || 'unknown publisher'
}
