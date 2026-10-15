import { useApp } from '../hooks/useApp'
export default function Toast() {
  const { toasts } = useApp()
  return <div className="toasts" aria-live="polite">{toasts.map(t => <div key={t.id} className="toast">{t.msg}</div>)}</div>
}
