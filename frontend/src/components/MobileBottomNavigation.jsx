import { NavLink } from 'react-router-dom'
import { Home, Compass, Sparkles, Bookmark, User } from 'lucide-react'
const items = [['/dashboard', 'Home', Home], ['/discover', 'Discover', Compass], ['/semantic-search', 'Search', Sparkles], ['/saved', 'Saved', Bookmark], ['/profile', 'Profile', User]]
export default function MobileBottomNavigation() {
  return (
    <nav className="bottomnav" aria-label="Mobile primary">
      {items.map(([to, label, Icon]) => <NavLink key={to} to={to} end={to === '/dashboard'} className={({ isActive }) => `bn ${isActive ? 'active' : ''}`}><Icon size={20} />{label}</NavLink>)}
    </nav>
  )
}
