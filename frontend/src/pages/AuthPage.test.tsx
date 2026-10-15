import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import AuthPage from './AuthPage'
import { AuthProvider } from '../hooks/useAuth'
import * as authService from '../services/authService'
import { testSession } from '../test/fixtures'
vi.mock('../services/authService', () => ({ refreshSession: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn() }))
beforeEach(() => { vi.mocked(authService.refreshSession).mockRejectedValue({ status: 401 }); vi.mocked(authService.login).mockReset(); vi.mocked(authService.register).mockReset() })
function renderAuth(mode: 'login' | 'register') { return render(<MemoryRouter><AuthProvider><AuthPage mode={mode} /></AuthProvider></MemoryRouter>) }
describe('AuthPage', () => {
  it('rejects mismatched passwords before registration', async () => {
    renderAuth('register')
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Pulse Reader' } })
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'reader@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'GoodPassword9!' } })
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'DifferentPassword9!' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Passwords do not match.')).toBeInTheDocument()
    expect(authService.register).not.toHaveBeenCalled()
  })
  it('shows a real service rejection without authenticating', async () => {
    vi.mocked(authService.login).mockRejectedValue(new Error('Email or password is incorrect.'))
    renderAuth('login')
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'reader@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'GoodPassword9!' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Email or password is incorrect.')).toBeInTheDocument()
    expect(authService.login).toHaveBeenCalledWith({ email: 'reader@example.test', password: 'GoodPassword9!' })
  })
  it('submits registration details to the account service', async () => {
    vi.mocked(authService.register).mockResolvedValue(testSession)
    renderAuth('register')
    for (const [label, value] of [['Full name', 'Test Reader'], ['Email address', 'reader@example.test'], ['Password', 'GoodPassword9!'], ['Confirm password', 'GoodPassword9!']]) fireEvent.change(screen.getByLabelText(label), { target: { value } })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
    await vi.waitFor(() => expect(authService.register).toHaveBeenCalledWith({ name: 'Test Reader', email: 'reader@example.test', password: 'GoodPassword9!' }))
  })
})
