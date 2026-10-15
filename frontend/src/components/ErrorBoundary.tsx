import { Component, type ReactNode, type ErrorInfo } from 'react'
export default class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  componentDidCatch(_error: Error, _info: ErrorInfo) { /* No private article or account data is logged. */ }
  render() { return this.state.failed ? <main className="route-loading"><h1>This view could not open.</h1><p>Please reload PulseAI to try again.</p><button className="ui-button" onClick={() => window.location.reload()}>Reload</button></main> : this.props.children }
}
