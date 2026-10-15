import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DiscoverPage } from './CatalogPages'
import { getArticles, getNewsIngestionStatus } from '../services/articleService'
import { getAvailableSources, getCategories } from '../services/sourceService'
import { testArticle } from '../test/fixtures'

vi.mock('../services/articleService', async importOriginal => {
  const actual = await importOriginal<typeof import('../services/articleService')>()
  return { ...actual, getArticles: vi.fn(), getNewsIngestionStatus: vi.fn() }
})
vi.mock('../services/sourceService', async importOriginal => {
  const actual = await importOriginal<typeof import('../services/sourceService')>()
  return { ...actual, getAvailableSources: vi.fn(), getCategories: vi.fn() }
})
vi.mock('../hooks/useLibrary', () => ({
  useLibrary: () => ({ isSaved: () => false, loading: false, pending: new Set<string>(), toggle: vi.fn() }),
}))
vi.mock('../hooks/usePulse', () => ({
  usePulse: () => ({ setLens: vi.fn() }),
}))

const status = {
  fetched: 4,
  inserted: 0,
  duplicatesSkipped: 0,
  invalidSkipped: 0,
  failures: 0,
  imagesBackfilled: 0,
  scheduled: true,
  refreshIntervalMs: 21_600_000,
  retentionHours: 168,
  maxCachedArticles: 2000,
  cachedArticles: 0,
  configuredProviders: ['rss'],
  feeds: [],
  categoryDistribution: {},
  publisherDistribution: {},
  articlesWithImages: 0,
  lastAttemptAt: '2026-10-13T10:00:00Z',
  lastSuccessfulRefreshAt: '2026-10-13T10:00:00Z',
}

function renderDiscover(path = '/discover') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes><Route path="/discover" element={<DiscoverPage />} /></Routes>
    </MemoryRouter>,
  )
}

const discoverCategories = [
  { id: 'business', name: 'Business', slug: 'business' },
  { id: 'entertainment', name: 'Entertainment', slug: 'entertainment' },
  { id: 'general', name: 'General', slug: 'general' },
  { id: 'health', name: 'Health', slug: 'health' },
  { id: 'politics', name: 'Politics', slug: 'politics' },
  { id: 'science', name: 'Science', slug: 'science' },
  { id: 'sports', name: 'Sports', slug: 'sports' },
  { id: 'technology', name: 'Technology', slug: 'technology' },
  { id: 'world', name: 'World', slug: 'world' },
]

describe('Discover catalog filters', () => {
  beforeEach(() => {
    vi.mocked(getArticles).mockResolvedValue({ items: [], page: 0, pageSize: 12, totalItems: 0, totalPages: 0 })
    vi.mocked(getCategories).mockResolvedValue(discoverCategories)
    vi.mocked(getAvailableSources).mockResolvedValue([{ id: 'source-1', name: 'SEC', domain: 'sec.gov', homepageUrl: 'https://www.sec.gov' }])
    vi.mocked(getNewsIngestionStatus).mockResolvedValue(status)
  })

  it('combines URL search, category, and publisher filters in the API query', async () => {
    renderDiscover('/discover?category=business&sourceId=source-1&q=markets')

    await waitFor(() => expect(getArticles).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'markets', category: 'business', sourceId: 'source-1', page: 0, pageSize: 12 }),
      expect.any(AbortSignal),
    ))
    expect(screen.getByRole('button', { name: 'Business' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Selected: SEC')).toBeInTheDocument()
    expect(getAvailableSources).toHaveBeenCalledWith('business', 'markets', expect.any(AbortSignal))
  })

  it('keeps existing category and publisher filters when searching', async () => {
    renderDiscover('/discover?category=business&sourceId=source-1')
    fireEvent.change(await screen.findByLabelText('Search coverage'), { target: { value: 'earnings' } })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(getArticles).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'earnings', category: 'business', sourceId: 'source-1' }),
      expect.any(AbortSignal),
    ))
  })

  it('filters Sports coverage through the article and source catalogs', async () => {
    renderDiscover('/discover')
    fireEvent.click(await screen.findByRole('button', { name: 'Sports' }))

    await waitFor(() => expect(getArticles).toHaveBeenLastCalledWith(
      expect.objectContaining({ category: 'sports', page: 0, pageSize: 12 }),
      expect.any(AbortSignal),
    ))
    expect(getAvailableSources).toHaveBeenLastCalledWith('sports', undefined, expect.any(AbortSignal))
  })

  it.each([
    ['All', undefined],
    ...discoverCategories.map(category => [category.name, category.slug] as [string, string]),
  ])('sends a valid publisher and article request for the %s category', async (label, slug) => {
    renderDiscover()
    if (slug) fireEvent.click(await screen.findByRole('button', { name: label }))

    await waitFor(() => {
      expect(getArticles).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: slug, page: 0, pageSize: 12 }),
        expect.any(AbortSignal),
      )
      expect(getAvailableSources).toHaveBeenLastCalledWith(slug, undefined, expect.any(AbortSignal))
    })
  })

  it('treats an empty publisher response as a normal empty state', async () => {
    vi.mocked(getAvailableSources).mockResolvedValue([])
    renderDiscover()

    expect(await screen.findByText('No publishers have articles in this catalog.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('retries the same valid publisher request after a request failure', async () => {
    vi.mocked(getAvailableSources)
      .mockRejectedValueOnce(new Error('Publisher catalog unavailable.'))
      .mockResolvedValueOnce([{ id: 'source-1', name: 'SEC', domain: 'sec.gov', homepageUrl: 'https://www.sec.gov' }])
    renderDiscover('/discover?category=business')

    expect(await screen.findByRole('alert')).toHaveTextContent('Publishers unavailable — Publisher catalog unavailable.')
    fireEvent.click(screen.getByRole('button', { name: 'Retry publishers' }))

    await waitFor(() => expect(getAvailableSources).toHaveBeenCalledTimes(2))
    expect(getAvailableSources).toHaveBeenLastCalledWith('business', undefined, expect.any(AbortSignal))
    expect(await screen.findByRole('combobox', { name: 'Select source' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('keeps articles visible when the publisher catalog request fails', async () => {
    vi.mocked(getAvailableSources).mockRejectedValue(new Error('Publisher catalog unavailable.'))
    vi.mocked(getArticles).mockResolvedValue({ items: [testArticle], page: 0, pageSize: 12, totalItems: 1, totalPages: 1 })
    renderDiscover('/discover?category=business')

    expect(await screen.findByRole('link', { name: testArticle.headline })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Publishers unavailable')
  })

  it('reports a successful provider fetch separately from newly saved articles', async () => {
    renderDiscover('/discover?category=business')

    expect(await screen.findByRole('heading', { name: 'No matching articles' })).toBeInTheDocument()
    expect(screen.getByText(/last successful provider fetch .* fetched 4 entries and saved 0 new articles/i)).toBeInTheDocument()
  })

  it('distinguishes an empty catalog from a failed feed run', async () => {
    vi.mocked(getNewsIngestionStatus).mockResolvedValue({
      ...status,
      failures: 2,
      lastAttemptAt: '2026-10-13T11:00:00Z',
      lastSuccessfulRefreshAt: '2026-10-13T10:00:00Z',
    })
    renderDiscover('/discover?category=business')

    expect(await screen.findByRole('heading', { name: 'No matching articles' })).toBeInTheDocument()
    expect(screen.getByText(/most recent overall ingestion attempt .* reported 2 provider or feed failures; this service-wide count does not show that these filters caused the gap/i)).toBeInTheDocument()
  })

  it('searches and selects an available source through the compact picker', async () => {
    renderDiscover('/discover?category=business')
    const picker = await screen.findByRole('combobox', { name: 'Select source' })
    fireEvent.change(picker, { target: { value: 'sec' } })
    fireEvent.click(await screen.findByRole('option', { name: 'SEC' }))

    await waitFor(() => expect(getArticles).toHaveBeenLastCalledWith(
      expect.objectContaining({ category: 'business', sourceId: 'source-1' }),
      expect.any(AbortSignal),
    ))
    expect(screen.getByText('Selected: SEC')).toBeInTheDocument()
  })

  it('sorts source options alphabetically and reports an unmatched search', async () => {
    vi.mocked(getAvailableSources).mockResolvedValue([
      { id: 'source-z', name: 'Zeta News', domain: 'zeta.example', homepageUrl: 'https://zeta.example' },
      { id: 'source-b', name: 'BBC News', domain: 'bbc.example', homepageUrl: 'https://bbc.example' },
      { id: 'source-a', name: 'Associated Press', domain: 'ap.example', homepageUrl: 'https://ap.example' },
    ])
    renderDiscover('/discover?category=business')
    const picker = await screen.findByRole('combobox', { name: 'Select source' })
    fireEvent.focus(picker)
    const options = await screen.findAllByRole('option')
    expect(options.map(option => option.textContent)).toEqual(['Associated Press', 'BBC News', 'Zeta News'])

    fireEvent.change(picker, { target: { value: 'missing publisher' } })
    expect(await screen.findByText('No sources match this search.')).toBeInTheDocument()
  })

  it('clears a source filter when a category change makes the source unavailable', async () => {
    vi.mocked(getAvailableSources).mockImplementation(async category => category === 'business'
      ? [{ id: 'source-1', name: 'SEC', domain: 'sec.gov', homepageUrl: 'https://www.sec.gov' }]
      : [{ id: 'source-2', name: 'BBC News', domain: 'bbc.com', homepageUrl: 'https://www.bbc.com' }])
    renderDiscover('/discover?category=business&sourceId=source-1')

    fireEvent.click(await screen.findByRole('button', { name: /^All$/ }))
    await waitFor(() => expect(getArticles).toHaveBeenLastCalledWith(
      expect.objectContaining({ sourceId: undefined, category: undefined }),
      expect.any(AbortSignal),
    ))
    expect(screen.queryByText('Selected: SEC')).not.toBeInTheDocument()
  })

  it('clears category, publisher, and search filters together', async () => {
    renderDiscover('/discover?category=business&sourceId=source-1&q=markets')
    fireEvent.click(await screen.findByRole('button', { name: 'Clear filters' }))

    await waitFor(() => expect(getArticles).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: undefined, category: undefined, sourceId: undefined, page: 0 }),
      expect.any(AbortSignal),
    ))
    expect(screen.getByRole('combobox', { name: 'Select source' })).toHaveValue('')
  })
})
