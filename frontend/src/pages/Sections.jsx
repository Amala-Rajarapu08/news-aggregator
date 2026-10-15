import { Link } from 'react-router-dom'
import BriefingCard from '../components/BriefingCard'
import NewsPulse from '../components/NewsPulse'
import PersonalizationPanel from '../components/PersonalizationPanel'
import NewsCard from '../components/NewsCard'
import { articles } from '../data/articles'
import { systemMetrics, notifications } from '../data/trends'
import { recommendedIds } from '../data/recommendations'
import { useApp } from '../hooks/useApp'

const Phase = ({ title, note, children }) => (<><h1 className="h1">{title}</h1><p className="banner">{note || 'Placeholder page: full implementation planned for a later phase. Data shown is mock.'}</p>{children}</>)
const getArticle = (id) => articles.find((article) => article.id === id) || null

export const Recommendations = () => <Phase title="Recommendation Center">{recommendedIds.map(id => {
  const article = getArticle(id)
  return article ? <NewsCard key={id} showWhy article={article} /> : null
})}</Phase>
export const NewsDNA = () => <Phase title="Your News DNA" />
export const BriefingPage = () => <Phase title="AI Briefing"><BriefingCard /></Phase>
export const TopicIntelligence = () => <Phase title="Topic Intelligence"><NewsPulse /></Phase>
export const Events = () => <Phase title="Event Clustering" note="Event coverage is available from stored articles."><Link className="btn" to="/events">Open developing events</Link></Phase>
export const Comparison = () => <Phase title="Multi-Source Comparison" />
export const Analytics = () => <Phase title="Personal Analytics" />
export const Trends = () => <Phase title="News Trends"><NewsPulse /></Phase>
export const System = () => <Phase title="System Intelligence Dashboard" note="All values are placeholders. No models are connected yet."><div className="card"><table><thead><tr><th>Component</th><th>Status</th><th>Latency</th><th>Version</th></tr></thead><tbody>{systemMetrics.map(m => <tr key={m.name}><td>{m.name}</td><td>{m.status}</td><td>{m.latency}</td><td>{m.version}</td></tr>)}</tbody></table></div></Phase>
export const Notifications = () => <Phase title="Notifications" note="Demo notifications.">{notifications.map(n => <div className="card" key={n.id}>{n.text}</div>)}</Phase>
export const Profile = () => <Phase title="Profile" note="Placeholder: authentication and real profiles come with Spring Boot + JWT." />
export const Preferences = () => <Phase title="Preferences"><PersonalizationPanel /></Phase>
export const Privacy = () => { const { toast } = useApp(); return <Phase title="Privacy & Personalization" note="Demo controls only."><div className="row wrap"><button className="btn" onClick={() => toast('Export (demo)')}>Export data</button><button className="btn" onClick={() => { localStorage.clear(); toast('Local data cleared. Reload to reset.') }}>Clear local data</button></div></Phase> }
