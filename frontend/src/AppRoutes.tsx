import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import AppLayout from './layouts/AppLayout'
import { LoadingState } from './components/ui/States'

const AuthPage = lazy(() => import('./pages/AuthPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const CatalogPages = lazy(() => import('./pages/CatalogPages').then(module => ({ default: module.CatalogPage })))
const ResearchPages = lazy(() => import('./pages/ResearchPages').then(module => ({ default: module.ResearchPage })))
const ContentPages = lazy(() => import('./pages/ContentPages'))

export interface RouteDefinition {
  path: string
  title: string
  description: string
  access: 'public' | 'application'
  render: () => ReactNode
}

export const routeDefinitions: RouteDefinition[] = [
  { path: '/login', title: 'Sign in', description: 'Sign in to PulseAI', access: 'public', render: () => <AuthPage mode="login" /> },
  { path: '/register', title: 'Create account', description: 'Create a PulseAI account', access: 'public', render: () => <AuthPage mode="register" /> },
  { path: '/', title: 'Home', description: 'PulseAI home', access: 'application', render: () => <Navigate to="/dashboard" replace /> },
  { path: '/dashboard', title: 'Dashboard', description: 'Your news intelligence overview', access: 'application', render: () => <DashboardPage /> },
  { path: '/discover', title: 'Discover', description: 'Explore source-attributed reporting', access: 'application', render: () => <CatalogPages page="discover" /> },
  { path: '/recommendations', title: 'Recommendations', description: 'Explainable story recommendations', access: 'application', render: () => <ResearchPages page="recommendations" /> },
  { path: '/saved', title: 'Saved stories', description: 'Your reading list', access: 'application', render: () => <CatalogPages page="saved" /> },
  { path: '/search', title: 'Search', description: 'Search indexed coverage', access: 'application', render: () => <ResearchPages page="search" /> },
  { path: '/semantic-search', title: 'Semantic search', description: 'Search by meaning', access: 'application', render: () => <ResearchPages page="semantic-search" /> },
  { path: '/assistant', title: 'Research assistant', description: 'Ask source-grounded questions', access: 'application', render: () => <ResearchPages page="assistant" /> },
  { path: '/news-dna', title: 'News DNA', description: 'Your reading profile', access: 'application', render: () => <ResearchPages page="news-dna" /> },
  { path: '/briefing', title: 'Briefing', description: 'Your daily briefing', access: 'application', render: () => <ContentPages page="briefing" /> },
  { path: '/topics', title: 'Topics', description: 'Explore topics and categories', access: 'application', render: () => <CatalogPages page="topics" /> },
  { path: '/topics-intelligence', title: 'Topic intelligence', description: 'Coverage patterns across topics', access: 'application', render: () => <ContentPages page="trends" /> },
  { path: '/events', title: 'Events', description: 'Developing story coverage', access: 'application', render: () => <ResearchPages page="events" /> },
  { path: '/events/', title: 'Events', description: 'Developing story coverage', access: 'application', render: () => <ResearchPages page="events" /> },
  { path: '/events/:eventId', title: 'Event detail', description: 'Source-attributed event timeline', access: 'application', render: () => <ResearchPages page="events" /> },
  { path: '/article/:articleId', title: 'Article', description: 'Source-attributed article reader', access: 'application', render: () => <ResearchPages page="article" /> },
  { path: '/sources', title: 'Sources', description: 'Connected publication catalog', access: 'application', render: () => <ContentPages page="sources" /> },
  { path: '/categories', title: 'Categories', description: 'Coverage categories', access: 'application', render: () => <ContentPages page="categories" /> },
  { path: '/analytics', title: 'Analytics', description: 'Reading and coverage analytics', access: 'application', render: () => <ContentPages page="analytics" /> },
  { path: '/trends', title: 'Trends', description: 'Measured topic trends', access: 'application', render: () => <ContentPages page="trends" /> },
  { path: '/profile', title: 'Profile', description: 'Your account profile', access: 'application', render: () => <ContentPages page="profile" /> },
  { path: '/preferences', title: 'Preferences', description: 'Manage reading preferences', access: 'application', render: () => <ContentPages page="preferences" /> },
  { path: '/privacy', title: 'Privacy', description: 'Privacy and account controls', access: 'application', render: () => <ContentPages page="privacy" /> },
  { path: '/research', title: 'Research dashboard', description: 'Source-grounded research workflows', access: 'application', render: () => <ContentPages page="research" /> },
  { path: '/admin', title: 'Admin intelligence', description: 'Operational service telemetry', access: 'application', render: () => <ContentPages page="admin" /> },
  { path: '/notifications', title: 'Notifications', description: 'Your notifications', access: 'application', render: () => <ContentPages page="notifications" /> },
  { path: '/comparison', title: 'Source comparison', description: 'Compare reporting across sources', access: 'application', render: () => <ContentPages page="comparison" /> },
  { path: '/system', title: 'Service status', description: 'Monitored component status', access: 'application', render: () => <ContentPages page="system" /> },
]

function renderRoute(definition: RouteDefinition) {
  return <Route key={definition.path} path={definition.path} element={definition.render()} />
}

function ProtectedWorkspace() {
  const { user, restoring } = useAuth()
  const location = useLocation()
  if (restoring) return <main className="route-loading"><LoadingState label="Restoring your session" /></main>
  return user ? <Outlet /> : <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
}
export function AppRoutes() {
  return (
    <Suspense fallback={<main className="route-loading"><LoadingState label="Opening PulseAI" /></main>}>
      <Routes>
        <Route>{routeDefinitions.filter(route => route.access === 'public').map(renderRoute)}</Route>
        <Route element={<ProtectedWorkspace />}><Route element={<AppLayout />}>
          {routeDefinitions.filter(route => route.access === 'application').map(renderRoute)}
          <Route path="*" element={<ContentPages page="not-found" />} />
        </Route></Route>
      </Routes>
    </Suspense>
  )
}
