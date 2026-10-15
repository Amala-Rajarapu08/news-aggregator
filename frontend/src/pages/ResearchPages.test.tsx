import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { testArticle } from '../test/fixtures'
import { semanticSearch } from '../services/searchService'
import { getEvent, getEvents } from '../services/eventService'
import { EventsPage, SemanticSearchPage } from './ResearchPages'

vi.mock('../services/searchService', () => ({ semanticSearch: vi.fn() }))
vi.mock('../services/eventService', () => ({ getEvent: vi.fn(), getEvents: vi.fn() }))
vi.mock('../services/articleService', async importOriginal => {
  const actual = await importOriginal<typeof import('../services/articleService')>()
  return { ...actual, getArticles: vi.fn() }
})
vi.mock('../hooks/useLibrary', () => ({
  useLibrary: () => ({ items: [], loading: false, pending: new Set<string>(), isSaved: () => false, toggle: vi.fn() }),
}))
vi.mock('../hooks/usePulse', () => ({ usePulse: () => ({ setLens: vi.fn() }) }))

describe('semantic search mode reporting', () => {
  beforeEach(() => {
    vi.mocked(semanticSearch).mockResolvedValue({
      items: [testArticle], page: 0, pageSize: 20, totalItems: 1, totalPages: 1,
      mode: 'keyword-fallback', reason: 'pgvector-unavailable', indexedArticles: 0,
    })
  })

  it('labels keyword fallback and explains why vector search is unavailable', async () => {
    render(
      <MemoryRouter initialEntries={['/semantic-search?q=NASA']}>
        <Routes><Route path="/semantic-search" element={<SemanticSearchPage />} /></Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Keyword fallback results' })).toBeInTheDocument()
    expect(screen.getByText(/Keyword fallback: pgvector storage is not available/)).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Test evidence headline' })).toBeInTheDocument()
    expect(semanticSearch).toHaveBeenCalledWith('NASA', expect.any(AbortSignal))
  })

  it('labels genuine pgvector results and the model that generated the vectors', async () => {
    vi.mocked(semanticSearch).mockResolvedValue({
      items: [testArticle], page: 0, pageSize: 20, totalItems: 1, totalPages: 1,
      mode: 'pgvector', model: 'sentence-transformers/all-MiniLM-L6-v2', indexedArticles: 80,
    })

    render(
      <MemoryRouter initialEntries={['/semantic-search?q=space']}>
        <Routes><Route path="/semantic-search" element={<SemanticSearchPage />} /></Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Semantic results' })).toBeInTheDocument()
    expect(screen.getByText(/pgvector similarity using sentence-transformers\/all-MiniLM-L6-v2; 80 articles are indexed/)).toBeInTheDocument()
  })
})

const eventDetail = {
  id: 'event-1', title: 'Six Flags rollercoaster incident',
  description: 'First Publisher: Police say a visitor died after falling from a ride.\n\nSecond Publisher: A person died and others were injured at the Georgia park.',
  category: 'World', status: 'DEVELOPING' as const,
  firstReportedAt: '2026-10-14T09:00:00Z', latestReportedAt: '2026-10-14T09:30:00Z',
  articleCount: 2, sourceCount: 2,
  sources: [{ id: 'source-1', name: 'First Publisher', articleCount: 1 }, { id: 'source-2', name: 'Second Publisher', articleCount: 1 }],
  articles: [{ id: 'article-1', title: 'Six Flags rollercoaster incident leaves visitor dead', description: 'Police say a visitor died after falling from a ride.', publisher: 'First Publisher', publishedAt: '2026-10-14T09:00:00Z', url: 'https://publisher.example/story' }],
}

describe('event feed and event detail', () => {
  it('renders real event summary data and opens its chronological source-backed detail', async () => {
    const user = userEvent.setup()
    vi.mocked(getEvents).mockResolvedValue({ items: [eventDetail], totalItems: 1 })
    vi.mocked(getEvent).mockResolvedValue(eventDetail)
    render(<MemoryRouter initialEntries={['/events']}><Routes>
      <Route path="/events" element={<EventsPage />} />
      <Route path="/events/:eventId" element={<EventsPage />} />
    </Routes></MemoryRouter>)

    expect(await screen.findByText('Six Flags rollercoaster incident')).toBeInTheDocument()
    expect(screen.getAllByText('2', { selector: '.event-card-meta b' })).toHaveLength(2)
    expect(screen.getByText('First Publisher')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Open event: Six Flags rollercoaster incident' }))
    expect(await screen.findByRole('heading', { name: 'Reporting timeline' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Six Flags rollercoaster incident leaves visitor dead' })).toHaveAttribute('href', '/article/article-1')
    expect(screen.getByRole('link', { name: 'Read at First Publisher' })).toHaveAttribute('href', 'https://publisher.example/story')
    expect(getEvent).toHaveBeenCalledWith('event-1', expect.any(AbortSignal))
  })

  it('shows an evidence-based empty state when the feed has no multi-article events', async () => {
    vi.mocked(getEvents).mockResolvedValue({ items: [], totalItems: 0 })
    render(<MemoryRouter initialEntries={['/events']}><Routes><Route path="/events" element={<EventsPage />} /></Routes></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'No multi-article events yet' })).toBeInTheDocument()
  })
})
