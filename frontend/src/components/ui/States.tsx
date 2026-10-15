import type { ReactNode } from 'react'
import { AlertCircle, ArrowUpRight, FileSearch, LoaderCircle, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from './Primitives'

type StateProps = {
  title: string
  description: string
  icon?: ReactNode
  action?: { label: string; href: string }
  compact?: boolean
}

export function EmptyState({ title, description, icon, action, compact = false }: StateProps) {
  return (
    <div className={`state-panel${compact ? ' state-panel-compact' : ''}`}>
      <span className="state-icon" aria-hidden="true">{icon ?? <FileSearch size={19} />}</span>
      <div>
        <h3>{title}</h3>
        <p>{description}</p>
        {action && <Link className="text-link" to={action.href}>{action.label}<ArrowUpRight size={14} /></Link>}
      </div>
    </div>
  )
}

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="state-panel" role="status" aria-live="polite">
      <span className="state-icon"><LoaderCircle className="spin" size={19} /></span>
      <div><h3>{label}</h3><p>Please wait while PulseAI checks for available data.</p></div>
    </div>
  )
}

export function ErrorState({
  title = 'This section is unavailable',
  description,
  onRetry,
}: { title?: string; description?: string; onRetry?: () => void }) {
  return (
    <div className="state-panel state-panel-error" role="alert">
      <span className="state-icon"><AlertCircle size={19} /></span>
      <div><h3>{title}</h3><p>{description ?? 'No connected service is available for this section yet.'}</p>{onRetry && <Button type="button" onClick={onRetry}>Try again</Button>}</div>
    </div>
  )
}

export function PlannedState({ title, description }: Pick<StateProps, 'title' | 'description'>) {
  return (
    <EmptyState
      title={title}
      description={description}
      icon={<Sparkles size={18} />}
      compact
    />
  )
}
