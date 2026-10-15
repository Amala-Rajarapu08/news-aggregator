// Later: export const API_BASE = 'http://localhost:8080/api'
export const flags = { simulateError: false }
export const delay = (ms = 500) => new Promise((r, j) => setTimeout(() => (flags.simulateError ? j(new Error('News service temporarily unavailable.')) : r()), ms))
