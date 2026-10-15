import { createContext, useContext, useState, type ReactNode } from 'react'
import type { Article } from '../types/domain'
import type { CopilotResult } from '../services/assistantService'
export interface Message { id: number; role: 'user' | 'assistant'; text: string; result?: CopilotResult }
interface Pulse { open: boolean; expanded: boolean; article: Article | null; lens: Article | null; messages: Message[]; setMessages: React.Dispatch<React.SetStateAction<Message[]>>; setOpen: (open: boolean) => void; setExpanded: (expanded: boolean) => void; setArticle: (article: Article | null) => void; setLens: (article: Article | null) => void }
const Context = createContext<Pulse | null>(null)
export function PulseProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false), [expanded, setExpanded] = useState(false)
  const [article, setArticle] = useState<Article | null>(null), [lens, setLens] = useState<Article | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  return <Context.Provider value={{ open, expanded, article, lens, messages, setMessages, setOpen, setExpanded, setArticle, setLens }}>{children}</Context.Provider>
}
export function usePulse() { const value = useContext(Context); if (!value) throw new Error('PulseProvider is required'); return value }
