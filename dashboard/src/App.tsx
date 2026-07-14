import { useEffect } from 'react'
import { Routes, Route, useLocation } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import Contracts from './pages/Contracts'
import ContractDetail from './pages/ContractDetail'
import Violations from './pages/Violations'
import GateHistory from './pages/GateHistory'
import { sentinelWs } from './lib/websocket'

export default function App() {
  const location = useLocation()

  useEffect(() => {
    sentinelWs.connect()
    return () => sentinelWs.disconnect()
  }, [])

  useEffect(() => {
    if (sentinelWs.connected) return
    sentinelWs.connect()
  }, [location.pathname])

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/contracts" element={<Contracts />} />
        <Route path="/contracts/:id" element={<ContractDetail />} />
        <Route path="/violations" element={<Violations />} />
        <Route path="/gate" element={<GateHistory />} />
      </Routes>
    </Layout>
  )
}
