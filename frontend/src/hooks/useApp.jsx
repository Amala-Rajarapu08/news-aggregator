import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { flags } from '../services/config'
import { useLibrary } from './useLibrary'
/** @type {import('react').Context<any>} */
const Ctx = createContext(null)
const load = (k, d) => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d } catch { return d } }
const store = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)) } catch {} }
export function AppProvider({ children }) {
  const [theme, setTheme] = useState(() => load('pulse.theme', 'light'))
  const library = useLibrary()
  const saved = library.items.map(item => item.article.id)
  const [unfollowed, setUnfollowed] = useState(() => load('pulse.unfollowed', []))
  const [toasts, setToasts] = useState([])
  const [whyArticle, setWhy] = useState(null)
  const [simulateError, setSim] = useState(false)
  useEffect(() => { document.documentElement.dataset.theme = theme; store('pulse.theme', theme) }, [theme])
  useEffect(() => { localStorage.removeItem('pulse.saved'); localStorage.removeItem('pulse.unfollowed') }, [])
  useEffect(() => store('pulse.unfollowed', unfollowed), [unfollowed])
  useEffect(() => { flags.simulateError = simulateError }, [simulateError])
  const toast = useCallback(msg => {
    const id = Math.random()
    setToasts(t => [...t, { id, msg }])
    setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 3000)
  }, [])
  const toggleSave = async id => { try { await library.toggle(id); toast('Reading list updated') } catch (error) { toast(error.message) } }
  const toggleFollow = c => setUnfollowed(u => u.includes(c) ? u.filter(x => x !== c) : [...u, c])
  const cycleTheme = () => setTheme(t => t === 'light' ? 'dark' : t === 'dark' ? 'contrast' : 'light')
  return <Ctx.Provider value={{ theme, cycleTheme, saved, toggleSave, unfollowed, toggleFollow, toasts, toast, whyArticle, setWhy, simulateError, setSim }}>{children}</Ctx.Provider>
}
export const useApp = () => useContext(Ctx)
