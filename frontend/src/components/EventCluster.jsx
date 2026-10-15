import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getEvents } from '../services/eventService'

export default function EventCluster() {
  const [event, setEvent] = useState(null)
  useEffect(() => {
    const controller = new AbortController()
    getEvents(controller.signal).then(result => setEvent(result.items[0] ?? null)).catch(() => setEvent(null))
    return () => controller.abort()
  }, [])
  if (!event) return null
  return (
    <section className="card">
      <h2 className="h2">Event Cluster</h2>
      <b>{event.title}</b>
      <div className="muted small">{event.articleCount} related articles · {event.sourceCount} publishers</div>
      <div><Link className="btn" to={`/events/${event.id}`}>View event</Link></div>
    </section>
  )
}
