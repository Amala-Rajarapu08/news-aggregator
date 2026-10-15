import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import BriefingCard from '../components/BriefingCard'
import RecommendationCard from '../components/RecommendationCard'
import AskPulseAI from '../components/AskPulseAI'
import { Loading, Empty, ErrorState } from '../components/States'
import useAsync from '../hooks/useAsync'
import { useApp } from '../hooks/useApp'
import { getRecommendations } from '../services/recommendationService'
import { CATEGORIES } from '../data/articles'
export default function Dashboard() {
  const nav = useNavigate()
  const { unfollowed, simulateError, setSim } = useApp()
  const [cat, setCat] = useState('All')
  const [gen, setGen] = useState(0)
  const { data, loading, error, reload } = useAsync(getRecommendations, [simulateError])
  const list = (data || []).filter(a => !unfollowed.includes(a.category) && (cat === 'All' || a.category === cat))
  return (
    <>
      <section className="row between wrap">
        <div><h1 className="h1">Good morning.</h1><p className="muted">Here is your personalized news intelligence briefing. Understand the news, not just the headlines.</p></div>
        <div className="row wrap">
          <button className="btn primary" onClick={() => setGen(g => g + 1)}>Generate AI Briefing</button>
          <button className="btn outline-dark" onClick={() => nav('/discover')}>Explore Today's News</button>
        </div>
      </section>
      <p className="banner">Demo mode: all articles, trends and briefings are mock data, not live news or AI output.
        <button className="linkbtn" onClick={() => setSim(!simulateError)}>{simulateError ? 'Stop simulating API error' : 'Simulate API error'}</button></p>
      <BriefingCard key={gen} autoRun={gen > 0} />
      <h2 className="h2">Recommended For You</h2>
      <div className="row wrap">{['All', ...CATEGORIES.slice(0, 5)].map(c => <button key={c} aria-pressed={cat === c} className={`btn small-btn ${cat === c ? 'active' : ''}`} onClick={() => setCat(c)}>{c}</button>)}</div>
      {loading ? <Loading /> : error ? <ErrorState text={error} retry={reload} /> : list.length ? list.map(a => <RecommendationCard key={a.id} article={a} />) : <Empty />}
      <AskPulseAI dark />
    </>
  )
}
