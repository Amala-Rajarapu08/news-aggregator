import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useAuth } from './useAuth'
import { getSavedArticles, saveArticle, removeSavedArticle, type SavedArticle } from '../services/articleService'
type Library = { items: SavedArticle[]; error: string; loading: boolean; pending: Set<string>; reload: () => Promise<void>; toggle: (id: string) => Promise<void>; isSaved: (id: string) => boolean }
const Context = createContext<Library | null>(null)
export function LibraryProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [items, setItems] = useState<SavedArticle[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [pending, setPending] = useState(new Set<string>())
  const locks = useRef(new Set<string>())
  const owner = useRef(user?.id); owner.current = user?.id
  const reload = useCallback(async () => {
    const id = user?.id
    if (!id) { setItems([]); return }
    setLoading(true); setError('')
    try {
      const first = await getSavedArticles({ page: 0, size: 100 })
      const remaining = await Promise.all(Array.from({ length: Math.max(0, Math.min(5, first.totalPages) - 1) }, (_, index) => getSavedArticles({ page: index + 1, size: 100 })))
      const pages = [first, ...remaining]
      if (owner.current === id) setItems(pages.flatMap(page => page.items))
    } catch (err) { if (owner.current === id) setError(err instanceof Error ? err.message : 'Reading list unavailable.') }
    finally { if (owner.current === id) setLoading(false) }
  }, [user?.id])
  useEffect(() => { setItems([]); setError(''); void reload() }, [reload])
  const toggle = async (id: string) => {
    if (!user || locks.current.has(id)) return
    locks.current.add(id); setPending(new Set(locks.current)); setError('')
    try {
      if (items.some(item => item.article.id === id)) { await removeSavedArticle(id); setItems(current => current.filter(item => item.article.id !== id)) }
      else { const saved = await saveArticle(id); setItems(current => [saved, ...current]) }
    } catch (err) { setError(err instanceof Error ? err.message : 'Unable to change reading list.'); throw err }
    finally { locks.current.delete(id); setPending(new Set(locks.current)) }
  }
  return <Context.Provider value={{ items, error, loading, pending, reload, toggle, isSaved: id => items.some(item => item.article.id === id) }}>{children}</Context.Provider>
}
export function useLibrary() { const value = useContext(Context); if (!value) throw new Error('LibraryProvider is required'); return value }
