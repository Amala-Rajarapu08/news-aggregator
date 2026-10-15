import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import TopNavigation from '../components/TopNavigation'
import MobileBottomNavigation from '../components/MobileBottomNavigation'
import PulseCopilot from '../components/PulseCopilot'
import PulseLens from '../components/PulseLens'
import { PulseProvider } from '../hooks/usePulse'
import Toast from '../components/Toast'
export default function AppLayout() {
  const [open, setOpen] = useState(false)
  return (
    <PulseProvider><div className="shell">
      <Sidebar open={open} onClose={() => setOpen(false)} />
      <div className="content">
        <main className="main"><TopNavigation menuOpen={open} onMenu={() => setOpen(o => !o)} /><div className="page-content"><Outlet /></div></main>
      </div>
      <MobileBottomNavigation />
      <PulseCopilot /><PulseLens /><Toast />
    </div></PulseProvider>
  )
}
