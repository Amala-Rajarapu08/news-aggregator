import { useEffect, useState } from 'react'
export default function useAsync(fn, deps = []) {
  const [s, set] = useState({ data: null, loading: true, error: null })
  const [n, setN] = useState(0)
  useEffect(() => {
    let ok = true
    set(x => ({ ...x, loading: true, error: null }))
    fn().then(d => ok && set({ data: d, loading: false, error: null })).catch(e => ok && set({ data: null, loading: false, error: e.message }))
    return () => { ok = false }
    // eslint-disable-next-line
  }, [...deps, n])
  return { ...s, reload: () => setN(x => x + 1) }
}
