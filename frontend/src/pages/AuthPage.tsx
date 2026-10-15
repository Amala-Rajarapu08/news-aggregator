import { useState } from 'react'
import { ArrowLeft, ArrowRight, Eye, EyeOff, Fingerprint } from 'lucide-react'
import { Link, useLocation, useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Button, Input } from '../components/ui/Primitives'

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const registering = mode === 'register'
  const location = useLocation()
  const navigate = useNavigate()
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const destination = typeof location.state?.from === 'string' && location.state.from.startsWith('/') && !location.state.from.startsWith('//') && !['/login', '/register'].includes(location.state.from) ? location.state.from : '/dashboard' 
  const [showPassword, setShowPassword] = useState(false)
  const [message, setMessage] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  if (auth.user) return <Navigate to={destination} replace />
  return (
    <main className="auth-page">
      <section className="auth-aside">
        <Link to="/" className="auth-brand"><PulseMark /><span>PulseAI</span></Link>
        <div className="auth-quote">
          <p className="eyebrow">A CLEARER VIEW OF THE WORLD</p>
          <h1>Understand the news,<br /><em>not just the headlines.</em></h1>
          <p>Context across sources. Stories that unfold over time. A more considered way to stay informed.</p>
        </div>
        <span className="auth-aside-foot">Editorial intelligence, built with care.</span>
      </section>
      <section className="auth-main">
        <Link to="/" className="auth-back"><ArrowLeft size={15} />Back to PulseAI</Link>
        <div className="auth-form-wrap">
          <span className="auth-icon"><Fingerprint size={21} /></span>
          <p className="eyebrow">YOUR PULSEAI ACCOUNT</p>
          <h2>{registering ? 'Create your account' : 'Welcome back'}</h2>
          <p className="auth-intro">{registering ? 'A thoughtful reading experience starts with you.' : 'Sign in to continue to your news intelligence.'}</p>
          <div className="auth-notice">Your reading list and preferences belong to your account.</div>{registering && <p className="muted small">Use 12-64 characters with uppercase, lowercase, a number, and a symbol. Maximum 72 UTF-8 bytes.</p>}
          {auth.error && <p className="inline-notice" role="alert">Session service: {auth.error}</p>}
          <form className="auth-form" onSubmit={async event => {
            event.preventDefault()
            if (busy) return
            if (registering && password !== confirmPassword) { setMessage('Passwords do not match.'); return }
            const form = new FormData(event.currentTarget)
            setBusy(true); setMessage('')
            try {
              const credentials = { email: String(form.get('email') ?? '').trim(), password }
              if (registering) await auth.signUp({ ...credentials, name: String(form.get('name') ?? '').trim() })
              else await auth.signIn(credentials)
              navigate(destination, { replace: true })
            } catch (error) { setMessage(error instanceof Error ? error.message : 'Sign in failed.') }
            finally { setBusy(false) }
          }}>
            {registering && <div className="field"><label htmlFor="name">Full name</label><Input id="name" name="name" autoComplete="name" placeholder="Your name" required minLength={2} maxLength={80} /></div>}
            <div className="field"><label htmlFor="email">Email address</label><Input id="email" name="email" type="email" autoComplete="email" placeholder="you@example.com" required /></div>
            <div className="field"><label htmlFor="password">Password</label><div className="password-field"><Input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete={registering ? 'new-password' : 'current-password'} placeholder="Enter your password" required minLength={registering ? 12 : 1} maxLength={registering ? 64 : 128} value={password} onChange={event => setPassword(event.target.value)} /><button className="password-toggle" type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword(value => !value)}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div></div>
            {registering && <div className="field"><label htmlFor="confirm-password">Confirm password</label><Input id="confirm-password" name="confirmPassword" type="password" autoComplete="new-password" placeholder="Enter your password again" required minLength={8} value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} /></div>}
            {!registering && <div className="auth-options"><span className="small muted">Private session / up to 7 days</span><button type="button" className="text-button" onClick={() => setMessage('Password recovery is not connected yet.')}>Forgot password?</button></div>}
            <Button type="submit" variant="primary" className="auth-submit" disabled={busy}>{busy ? 'Please wait...' : registering ? 'Create account' : 'Sign in'}<ArrowRight size={16} /></Button>
            {message && <p className="auth-message" role="status">{message}</p>}
          </form>
          <p className="auth-switch">{registering ? 'Already have an account?' : 'New to PulseAI?'} <Link to={registering ? '/login' : '/register'} state={{ from: destination }}>{registering ? 'Sign in' : 'Create an account'}</Link></p>
          <p className="auth-legal">Passwords are securely hashed. Reading history is off until you enable it in Privacy.</p>
        </div>
      </section>
    </main>
  )
}

function PulseMark() {
  return <svg width="27" height="27" viewBox="0 0 28 28" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M2 15h5l3-9 5 17 3-8h8" /></svg>
}
