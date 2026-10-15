import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import ErrorBoundary from './components/ErrorBoundary'
import { LibraryProvider } from './hooks/useLibrary'
import { AuthProvider } from './hooks/useAuth'
import { AppProvider } from './hooks/useApp'
import './styles/tailwind.css'
import './index.css'
import './styles/pulse.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ErrorBoundary><AuthProvider><LibraryProvider><AppProvider>
        <App />
      </AppProvider></LibraryProvider></AuthProvider></ErrorBoundary>
    </BrowserRouter>
  </React.StrictMode>
)
