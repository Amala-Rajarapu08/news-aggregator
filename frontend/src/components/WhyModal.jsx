import Modal from './Modal'
import { useApp } from '../hooks/useApp'
export function Factor({ label, pct }) {
  return <div><div className="row between small"><span>{label}</span><b>+{pct}%</b></div><div className="bar"><div style={{ width: `${pct}%` }} /></div></div>
}
export default function WhyModal() {
  const { whyArticle: a, setWhy } = useApp()
  if (!a) return null
  return (
    <Modal title="Why am I seeing this?" onClose={() => setWhy(null)}>
      <p className="muted">{a.title}</p>
      <p className="muted small">Explainable recommendation · demo values, not from a real model</p>
      {a.factors.map(f => <Factor key={f.label} {...f} />)}
    </Modal>
  )
}
