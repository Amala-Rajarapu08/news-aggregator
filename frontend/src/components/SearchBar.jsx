import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
export default function SearchBar({ className = '' }) {
  const [q, setQ] = useState('')
  const nav = useNavigate()
  return (
    <form className={`grow top-search-form ${className}`} onSubmit={e => { e.preventDefault(); nav(`/search?q=${encodeURIComponent(q)}`) }}>
      <label htmlFor="q" className="sr">Search</label>
      <input id="q" className="input" value={q} onChange={e => setQ(e.target.value)} placeholder="Search coverage…" />
    </form>
  )
}
