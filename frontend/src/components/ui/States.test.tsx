import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { EmptyState, ErrorState, LoadingState } from './States'

describe('shared page states', () => {
  it('renders a useful empty state action as a route link', () => {
    render(<MemoryRouter><EmptyState title="No stories" description="Coverage is not connected." action={{ label: 'Browse topics', href: '/topics' }} /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'No stories' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse topics' })).toHaveAttribute('href', '/topics')
  })

  it('announces loading and errors accessibly and supports retry', () => {
    const onRetry = vi.fn()
    const { rerender } = render(<LoadingState label="Loading articles" />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading articles')
    rerender(<ErrorState title="Articles unavailable" description="The service returned an error." onRetry={onRetry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('The service returned an error.')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })
})
