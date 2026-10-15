import { Sun, Moon, Contrast } from 'lucide-react'
import { useApp } from '../hooks/useApp'
export default function ThemeToggle() {
  const { theme, cycleTheme } = useApp()
  const Icon = theme === 'light' ? Sun : theme === 'dark' ? Moon : Contrast
  return <button className="btn" onClick={cycleTheme} aria-label={`Theme: ${theme}. Click to change`}><Icon size={18} /><span className="hide-sm">{theme}</span></button>
}
