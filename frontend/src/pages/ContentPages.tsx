import { ConnectedProfile, ConnectedPreferences, ConnectedAnalytics } from './AccountPages'
import { ConnectedBriefing, ConnectedTrends, ConnectedSystem } from './IntelligencePages'
import { useCallback, useState, type FormEvent, type ReactNode } from 'react'
import { ArrowRight, Bell, CircleHelp, LockKeyhole, Newspaper, Search, ShieldCheck, TrendingUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Badge, Button, Card, Input, PageHeading } from '../components/ui/Primitives'
import { EmptyState, ErrorState, LoadingState, PlannedState } from '../components/ui/States'
import { useApiRequest } from '../hooks/useApiRequest'
import { getCategories, getSources } from '../services/sourceService'

function SectionCard({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return <Card className="intelligence-section"><div className="section-heading-inline"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2></div></div>{children}</Card>
}

function Unavailable({ title, description }: { title: string; description: string }) {
  return <EmptyState title={title} description={description} compact />
}

export function SourcesPage() {
  const [page, setPage] = useState(0)
  const request = useCallback((signal: AbortSignal) => getSources(signal, page), [page])
  const sources = useApiRequest(request)
  return (
    <div className="page-stack">
      <PageHeading eyebrow="PUBLICATION DIRECTORY" title="Sources" description="Review publishers represented by articles in the stored catalog." />
      <SectionCard eyebrow="SOURCE CATALOG" title="Connected publications">
        {sources.status === 'loading' || sources.status === 'idle'
          ? <LoadingState label="Loading sources" />
          : sources.status === 'error'
            ? <ErrorState title="Sources unavailable" description={sources.error.message} onRetry={sources.retry} />
            : sources.data.items.length
              ? <div className="source-directory">{sources.data.items.map(source => (
                <article className="source-directory-item" key={source.id}>
                  <div><h3>{source.name}</h3><p className="muted small">{source.domain}</p></div>
                  <Link className="text-link" to={`/discover?sourceId=${encodeURIComponent(source.id)}`}>Browse articles <ArrowRight size={15} /></Link>
                </article>
              ))}</div>
              : <Unavailable title="No publications available" description="No publisher metadata has been stored yet. Sources appear when valid articles are ingested." />}
      </SectionCard>
      {sources.status === 'success' && sources.data.totalPages > 1 && <div className="row wrap"><Button disabled={page===0} onClick={() => setPage(value => value-1)}>Previous</Button><span>Page {page+1} of {sources.data.totalPages}</span><Button disabled={page+1 >= sources.data.totalPages} onClick={() => setPage(value => value+1)}>Next</Button></div>}
      <SectionCard eyebrow="ATTRIBUTION" title="How sources will be represented"><p className="section-copy">Articles will retain their publisher, original publication time, and source link. Source coverage is not a quality score, and no publisher rankings are shown without a documented method.</p></SectionCard>
    </div>
  )
}

export function CategoriesPage() {
  const request = useCallback((signal: AbortSignal) => getCategories(signal), [])
  const categories = useApiRequest(request)
  return (
    <div className="page-stack">
      <PageHeading eyebrow="COVERAGE TAXONOMY" title="Categories" description="Browse the categories currently supported by the article catalog." />
      {categories.status === 'loading' || categories.status === 'idle'
        ? <LoadingState label="Loading categories" />
        : categories.status === 'error'
          ? <ErrorState title="Categories unavailable" description={categories.error.message} onRetry={categories.retry} />
          : categories.data.length
            ? <div className="taxonomy-grid">{categories.data.map(category => <Link key={category.id} className="taxonomy-link" to={`/discover?category=${encodeURIComponent(category.slug)}`}><span>{category.name}</span><ArrowRight size={15} /></Link>)}</div>
            : <Unavailable title="No categories available" description="Categories will appear after database migrations have completed." />}
    </div>
  )
}

export function ResearchPage() {
  const workflows = [
    ['Find coverage', 'Search indexed articles using keywords or semantic retrieval.', Search, '/search'],
    ['Trace a developing story', 'Review related source-attributed updates over time.', TrendingUp, '/events'],
    ['Ask a question', 'Explore a topic through evidence-linked answers.', CircleHelp, '/assistant'],
    ['Review source context', 'Understand the publisher and original article.', Newspaper, '/sources'],
  ] as const
  return (
    <div className="page-stack">
      <PageHeading eyebrow="RESEARCH WORKSPACE" title="Research dashboard" description="A set of research entry points built around traceable reporting—not a pre-filled analysis." />
      <div className="research-workflows">{workflows.map(([title, description, Icon, href]) => <Link className="workflow-link" to={href} key={title}><Icon size={18} /><span><strong>{title}</strong><small>{description}</small></span><ArrowRight size={15} /></Link>)}</div>
      <SectionCard eyebrow="WORKSPACE" title="Saved research"><Unavailable title="No research sessions" description="Search history and research notes are not stored in this frontend." /></SectionCard>
    </div>
  )
}

export function AdminPage() {
  const checks = ['Backend health', 'AI service health', 'Database health', 'Vector index', 'Ingestion status', 'Model version']
  return (
    <div className="page-stack">
      <PageHeading eyebrow="OPERATIONS" title="Admin intelligence" description="Operator telemetry is pending. Ingestion actions require an administrator account." action={<Badge tone="gold">NO ADMIN ACCESS</Badge>} />
      <div className="notice-strip"><LockKeyhole size={16} /><span>Administrator actions are enforced by the backend. This screen currently has no operator telemetry integration.</span></div>
      <SectionCard eyebrow="SERVICE TELEMETRY" title="Monitored components">
        <div className="telemetry-list">{checks.map(check => <div className="telemetry-row" key={check}><span>{check}</span><Badge>NOT MONITORED</Badge></div>)}</div>
      </SectionCard>
      <SectionCard eyebrow="ACCESS CONTROL" title="Security boundary"><p className="section-copy">Client-side route visibility is not authorization. No secrets, privileged actions, user records, or infrastructure details are accessible from this screen.</p></SectionCard>
    </div>
  )
}

export function NotificationsPage() {
  const [filter, setFilter] = useState<'all' | 'unread'>('all')
  return (
    <div className="page-stack">
      <PageHeading eyebrow="YOUR UPDATES" title="Notifications" description="Notifications require an account-backed service. No sample alerts or unread counts are shown." action={<Bell size={19} />} />
      <div className="filter-tabs" role="group" aria-label="Filter notifications">
        <Button variant={filter === 'all' ? 'primary' : 'secondary'} aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>All</Button>
        <Button variant={filter === 'unread' ? 'primary' : 'secondary'} aria-pressed={filter === 'unread'} onClick={() => setFilter('unread')}>Unread</Button>
      </div>
      <Unavailable title={filter === 'unread' ? 'No unread notifications' : 'No notifications available'} description="The notification service is not connected. This empty state does not imply that a real account inbox was checked." />
    </div>
  )
}

export function ComparisonPage() {
  return <div className="page-stack"><PageHeading eyebrow="MULTIPLE PERSPECTIVES" title="Source comparison" description="Compare coverage of the same event using linked articles, publication times, and clear source attribution." /><div className="comparison-columns"><SectionCard eyebrow="STORY A" title="Choose an event"><Unavailable title="No event available" description="A source comparison needs a real event and related articles." /></SectionCard><SectionCard eyebrow="STORY B" title="Compare reporting"><Unavailable title="No source perspectives" description="No reporting has been loaded or ranked." /></SectionCard></div></div>
}

export function SystemPage() {
  return <div className="page-stack"><PageHeading eyebrow="SERVICE STATUS" title="System status" description="No service health has been queried by this frontend." /><SectionCard eyebrow="COMPONENTS" title="Health checks"><Unavailable title="Status is unknown" description="A missing status check is not the same as a healthy service. Connect monitored health endpoints to show verified status." /></SectionCard></div>
}

export function NotFoundPage() {
  return <div className="page-stack"><PageHeading eyebrow="NOT FOUND" title="That page isn't here" description="The requested PulseAI page does not exist." /><EmptyState title="Route not found" description="Check the address or return to your dashboard." action={{ label: 'Go to dashboard', href: '/dashboard' }} /></div>
}

export function ContentPage({ page }: { page: 'briefing' | 'sources' | 'categories' | 'analytics' | 'trends' | 'profile' | 'preferences' | 'privacy' | 'research' | 'admin' | 'notifications' | 'comparison' | 'system' | 'not-found' }) {
  switch (page) {
    case 'briefing': return <ConnectedBriefing />
    case 'sources': return <SourcesPage />
    case 'categories': return <CategoriesPage />
    case 'analytics': return <ConnectedAnalytics />
    case 'trends': return <ConnectedTrends />
    case 'profile': return <ConnectedProfile />
    case 'preferences': return <ConnectedPreferences />
    case 'privacy': return <ConnectedPreferences privacy />
    case 'research': return <ResearchPage />
    case 'admin': return <AdminPage />
    case 'notifications': return <NotificationsPage />
    case 'comparison': return <ComparisonPage />
    case 'system': return <ConnectedSystem />
    case 'not-found': return <NotFoundPage />
  }
}

export default ContentPage
