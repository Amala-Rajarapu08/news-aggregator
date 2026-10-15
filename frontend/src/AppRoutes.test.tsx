import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AppProvider } from './hooks/useApp'
import { AuthProvider } from './hooks/useAuth'
import { LibraryProvider } from './hooks/useLibrary'
import { AppRoutes, routeDefinitions } from './AppRoutes'
import { testSession, emptyPage } from './test/fixtures'
import * as auth from './services/authService'
vi.mock('./services/authService', () => ({ refreshSession: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn() }))
vi.mock('./services/articleService', () => ({ getSavedArticles: vi.fn(async () => emptyPage), getArticles: vi.fn(async () => emptyPage) }))
beforeEach(() => { vi.mocked(auth.refreshSession).mockResolvedValue(testSession) })
function renderPath(path: string) { return render(<MemoryRouter initialEntries={[path]}><AuthProvider><LibraryProvider><AppProvider><AppRoutes /></AppProvider></LibraryProvider></AuthProvider></MemoryRouter>) }
describe('route configuration', () => {
  it('keeps declared paths unique and public account routes separate', () => { const paths = routeDefinitions.map(route => route.path); expect(new Set(paths).size).toBe(paths.length); expect(routeDefinitions.find(route => route.path === '/login')?.access).toBe('public') })
  it('redirects an unauthenticated workspace to sign in', async () => { vi.mocked(auth.refreshSession).mockRejectedValue({ status: 401 }); renderPath('/saved'); expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument(); expect(screen.queryByRole('navigation', { name: 'Primary navigation' })).not.toBeInTheDocument() })
  it('restores an authenticated shell and a trailing-slash route', async () => { renderPath('/events/'); expect(await screen.findByRole('heading', { name: 'Events' })).toBeInTheDocument(); expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toBeInTheDocument(); expect(screen.getByLabelText('Open Pulse Copilot')).toBeInTheDocument() })
  it('renders a real on-demand reading digest', async () => { renderPath('/briefing'); expect(await screen.findByRole('heading', { name: "Today's briefing" })).toBeInTheDocument(); expect(await screen.findByText('No reporting in this window')).toBeInTheDocument() })
  it('renders unknown authenticated routes as not found', async () => { renderPath('/missing-page'); expect(await screen.findByRole('heading', { name: "That page isn't here" })).toBeInTheDocument() })
})
