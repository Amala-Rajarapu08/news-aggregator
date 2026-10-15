import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Link, MemoryRouter } from 'react-router-dom'
import { PulseProvider, usePulse } from '../hooks/usePulse'
import { testArticle } from '../test/fixtures'
import { assistantService } from '../services/assistantService'
import PulseCopilot from './PulseCopilot'
vi.mock('../services/assistantService', () => ({ assistantService: { search: vi.fn(async () => ({ mode: 'publisher-excerpt', message: 'Publisher-provided excerpt, not an AI answer.', articles: [testArticle] })) } }))
function Harness() { const pulse = usePulse(); return <><button onClick={() => pulse.setArticle(testArticle)}>Use this article</button><Link to="/discover">Discover route</Link><PulseCopilot /></> }
describe('Pulse Copilot', () => {
  it('passes story context, preserves the session across routes, and closes with Escape', async () => {
    render(<MemoryRouter initialEntries={['/article/test-article']}><PulseProvider><Harness /></PulseProvider></MemoryRouter>)
    fireEvent.click(screen.getByText('Use this article'))
    fireEvent.click(screen.getByLabelText('Open Pulse Copilot'))
    fireEvent.click(screen.getByText('Show the publisher excerpt'))
    expect(await screen.findByText('Publisher-provided excerpt, not an AI answer.')).toBeInTheDocument()
    expect(assistantService.search).toHaveBeenCalledWith({ question: 'Show the publisher excerpt', articleId: 'test-article', pageContext: '/article/test-article' }, expect.any(AbortSignal))
    fireEvent.click(screen.getByText('Discover route'))
    expect(screen.getByText('Publisher-provided excerpt, not an AI answer.')).toBeInTheDocument()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Open Pulse Copilot')).toHaveFocus()
  })
})
