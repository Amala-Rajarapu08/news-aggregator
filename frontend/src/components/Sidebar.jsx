import { useEffect } from 'react'
import { useAuth } from '../hooks/useAuth'
import { Link, NavLink } from 'react-router-dom'
import {
  Activity, Bookmark, BrainCircuit, Compass, FileSearch, FolderOpen, Gauge,
  Home, Layers3, LockKeyhole, MessageSquareText, Newspaper, Radio, Search,
  Settings2, Sparkles, UserRound, X,
} from 'lucide-react'

const GROUPS = [
  { title: 'HOME', items: [['/dashboard', 'Dashboard', Home], ['/discover', 'Discover', Compass], ['/recommendations', 'Recommendations', Sparkles], ['/saved', 'Saved', Bookmark]] },
  { title: 'INTELLIGENCE', items: [['/semantic-search', 'Semantic search', BrainCircuit], ['/assistant', 'Pulse Copilot', MessageSquareText], ['/news-dna', 'News DNA', Activity], ['/briefing', 'Daily briefing', Newspaper]] },
  { title: 'NEWS', items: [['/topics', 'Topics', Layers3], ['/events', 'Events', Radio], ['/sources', 'Sources', FolderOpen], ['/categories', 'Categories', Gauge]] },
  { title: 'ANALYTICS', items: [['/analytics', 'Analytics', Activity], ['/trends', 'Trends', Gauge]] },
  { title: 'ACCOUNT', items: [['/profile', 'Profile', UserRound], ['/preferences', 'Preferences', Settings2], ['/privacy', 'Privacy', LockKeyhole]] },
  { title: 'RESEARCH', items: [['/research', 'Research dashboard', FileSearch]] },
]

export default function Sidebar({ open, onClose }) {
  const { user } = useAuth()
  useEffect(() => {
    if (!open) return undefined
    const closeOnEscape = event => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [open, onClose])

  return (
    <>
      {open && <button className="drawer-backdrop" type="button" aria-label="Close navigation menu" onClick={onClose} />}
      <nav id="primary-navigation" className={`sidebar${open ? ' sidebar-open' : ''}`} aria-label="Primary navigation">
        <div className="sidebar-top">
          <Link className="logo" to="/dashboard" onClick={onClose}>
            <svg width="27" height="27" viewBox="0 0 28 28" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M2 15h5l3-9 5 17 3-8h8" /></svg>
            <span>Pulse<span className="logo-ai">AI</span></span>
          </Link>
          <button type="button" className="drawer-close" onClick={onClose} aria-label="Close navigation"><X size={19} /></button>
        </div>
        <div className="sidebar-scroll">
          {GROUPS.map(group => (
            <div className="nav-group" key={group.title}>
              <p className="nav-group-title">{group.title}</p>
              {group.items.map(([to, label, Icon]) => (
                <NavLink key={to} to={to} end={to === '/dashboard' || to === '/events'} onClick={onClose} className={({ isActive }) => `navlink${isActive ? ' active' : ''}`}>
                  <Icon size={17} strokeWidth={1.8} /><span>{label}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </div>
        <div className="sidebar-footer"><span className="status-dot" />{user?.name || 'Your workspace'} <span className="preview-label">ACCOUNT</span></div>
      </nav>
    </>
  )
}
