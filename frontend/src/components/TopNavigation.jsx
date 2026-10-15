import { useState } from 'react'
import { Bell, Menu, Search, UserRound, LogOut } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useApp } from '../hooks/useApp'
import ThemeToggle from './ThemeToggle'
import SearchBar from './SearchBar'

const TITLES = {
  '/': 'Dashboard',
  '/dashboard': 'Dashboard',
  '/discover': 'Discover',
  '/recommendations': 'Recommendations',
  '/saved': 'Saved stories',
  '/search': 'Search',
  '/semantic-search': 'Semantic search',
  '/assistant': 'AI assistant',
  '/news-dna': 'News DNA',
  '/briefing': 'AI briefing',
  '/topics': 'Topics',
  '/events': 'Events',
  '/sources': 'Sources',
  '/categories': 'Categories',
  '/analytics': 'Analytics',
  '/trends': 'Trends',
  '/profile': 'Profile',
  '/preferences': 'Preferences',
  '/privacy': 'Privacy',
  '/research': 'Research dashboard',
  '/admin': 'Admin intelligence',
  '/notifications': 'Notifications',
}

export default function TopNavigation({ onMenu, menuOpen }) {
  const location = useLocation()
  const { user, signOut } = useAuth()
  const { toast } = useApp()
  const navigate = useNavigate()
  const [searchOpen, setSearchOpen] = useState(false)
  const title = TITLES[location.pathname] ?? (location.pathname.startsWith('/article/') ? 'Article reader' : location.pathname === '/events/' ? 'Events' : location.pathname.startsWith('/events/') ? 'Event detail' : 'PulseAI')

  return (
    <header className="topbar">
      <div className="topbar-context">
        <button className="menu-btn" type="button" aria-label={menuOpen ? 'Close navigation menu' : 'Open navigation menu'} aria-expanded={menuOpen} aria-controls="primary-navigation" onClick={onMenu}><Menu size={20} /></button>
        <div><p className="topbar-kicker">PULSEAI / WORKSPACE</p><p className="topbar-title">{title}</p></div>
      </div>
      <div className="topbar-actions">
        <SearchBar className="topbar-search" />
        <button type="button" className="topbar-icon mobile-search-toggle" aria-label="Open search" onClick={() => setSearchOpen(value => !value)}><Search size={18} /></button>
        <ThemeToggle />
        <Link className="topbar-icon" to="/notifications" aria-label="Notifications"><Bell size={18} /></Link>
        <Link to="/profile" className="profile-chip" aria-label="Your account profile"><span className="profile-avatar"><UserRound size={15} /></span><span>{user?.name}</span></Link>
        <button type="button" className="topbar-icon" aria-label="Sign out" onClick={async () => { try { await signOut(); navigate('/login', { replace: true }) } catch (error) { toast(error.message) } }}><LogOut size={17} /></button>
      </div>
      {searchOpen && <form className="mobile-search-form" onSubmit={event => { event.preventDefault(); const form = new FormData(event.currentTarget); const query = String(form.get('q') ?? '').trim(); navigate(query ? `/search?q=${encodeURIComponent(query)}` : '/search'); setSearchOpen(false) }}><label htmlFor="mobile-search" className="sr">Search</label><input id="mobile-search" name="q" autoFocus placeholder="Search PulseAI…" /></form>}
    </header>
  )
}
