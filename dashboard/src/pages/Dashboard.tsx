import { useState, useCallback } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { Shield, FileText, AlertTriangle, TrendingDown, Wifi, WifiOff } from 'lucide-react'
import { getContracts, getViolations, getViolationSummary, getGateHistory, Violation, GateDecision } from '../lib/api'
import { useWebSocket } from '../hooks/useWebSocket'
import { sentinelWs } from '../lib/websocket'

export default function Dashboard() {
  const queryClient = useQueryClient()
  const [wsConnected, setWsConnected] = useState(false)
  const [toast, setToast] = useState<{ message: string; severity: string } | null>(null)

  const { data: contracts } = useQuery({ queryKey: ['contracts'], queryFn: getContracts })
  const { data: violations } = useQuery({ queryKey: ['violations'], queryFn: () => getViolations() })
  const { data: summary } = useQuery({ queryKey: ['violation-summary'], queryFn: getViolationSummary })
  const { data: gateHistory } = useQuery({ queryKey: ['gate-history'], queryFn: () => getGateHistory() })

  useWebSocket('violation:new', useCallback((data: unknown) => {
    const violation = data as Violation
    queryClient.setQueryData<Violation[]>(['violations'], (old) => {
      if (!old) return [violation]
      return [violation, ...old]
    })
    setToast({ message: violation.message || 'New violation detected', severity: violation.severity })
    setTimeout(() => setToast(null), 5000)
  }, [queryClient]))

  useWebSocket('gate:decision', useCallback((data: unknown) => {
    const decision = data as GateDecision
    queryClient.setQueryData<GateDecision[]>(['gate-history'], (old) => {
      if (!old) return [decision]
      return [decision, ...old]
    })
  }, [queryClient]))

  useWebSocket('connected', useCallback(() => setWsConnected(true), []))
  useWebSocket('disconnected', useCallback(() => setWsConnected(false), []))

  const totalContracts = contracts?.length ?? 0
  const activeViolations = violations?.filter((v) => !v.resolved).length ?? 0
  const gateBlocks = gateHistory?.filter((g) => g.decision === 'DENY').length ?? 0
  const driftScore = gateHistory && gateHistory.length > 0
    ? Math.round(gateHistory.reduce((a, g) => a + g.drift_score, 0) / gateHistory.length * 100) / 100
    : 0

  const summaryCards = [
    { label: 'Total Contracts', value: totalContracts, icon: FileText, color: 'text-blue-400', bg: 'bg-blue-500/10' },
    { label: 'Active Violations', value: activeViolations, icon: AlertTriangle, color: 'text-red-400', bg: 'bg-red-500/10' },
    { label: 'Gate Blocks', value: gateBlocks, icon: Shield, color: 'text-orange-400', bg: 'bg-orange-500/10' },
    { label: 'Drift Score', value: driftScore, icon: TrendingDown, color: 'text-emerald-400', bg: 'bg-emerald-500/10' },
  ]

  const chartData = summary
    ? [
        { name: 'Breaking', value: summary.breaking },
        { name: 'Warning', value: summary.warning },
        { name: 'Additive', value: summary.additive },
      ]
    : []

  const recentViolations = violations?.slice(0, 5) ?? []

  return (
    <div className="space-y-6">
      {toast && (
        <div className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-lg border shadow-lg flex items-center gap-2 animate-pulse ${
          toast.severity === 'BREAKING'
            ? 'bg-red-900/90 border-red-500/50 text-red-200'
            : toast.severity === 'WARNING'
            ? 'bg-yellow-900/90 border-yellow-500/50 text-yellow-200'
            : 'bg-green-900/90 border-green-500/50 text-green-200'
        }`}>
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span className="text-sm">{toast.message}</span>
        </div>
      )}

      <div className="grid grid-cols-4 gap-4">
        {summaryCards.map((card) => (
          <div key={card.label} className="bg-slate-800 rounded-lg border border-slate-700 p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-slate-400">{card.label}</p>
                <p className="text-2xl font-bold text-white mt-1">{card.value}</p>
              </div>
              <div className={`p-3 rounded-lg ${card.bg}`}>
                <card.icon className={`w-6 h-6 ${card.color}`} />
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="flex items-center gap-2 text-xs text-slate-500">
        {wsConnected ? (
          <>
            <span className="relative flex h-2.5 w-2.5">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-400" />
            </span>
            <Wifi className="w-3.5 h-3.5 text-emerald-400" />
            <span>Live — real-time updates connected</span>
          </>
        ) : (
          <>
            <span className="h-2.5 w-2.5 rounded-full bg-slate-600" />
            <WifiOff className="w-3.5 h-3.5 text-slate-600" />
            <span>Disconnected — reconnecting...</span>
          </>
        )}
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="bg-slate-800 rounded-lg border border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-4">Violation Breakdown</h3>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="name" stroke="#94a3b8" fontSize={12} />
                <YAxis stroke="#94a3b8" fontSize={12} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px' }}
                  labelStyle={{ color: '#e2e8f0' }}
                />
                <Bar dataKey="value" fill="#10b981" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-slate-500 text-sm">No data</p>
          )}
        </div>

        <div className="bg-slate-800 rounded-lg border border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-4">Recent Violations</h3>
          {recentViolations.length > 0 ? (
            <div className="space-y-3">
              {recentViolations.map((v) => (
                <div key={v.id} className="flex items-center gap-3 p-2 rounded-md bg-slate-700/30">
                  <div className={`w-2 h-2 rounded-full ${
                    v.severity === 'BREAKING' ? 'bg-red-400' : v.severity === 'WARNING' ? 'bg-yellow-400' : 'bg-green-400'
                  }`} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-slate-200 truncate">{v.message}</p>
                    <p className="text-xs text-slate-500">{v.service} &middot; {v.rule}</p>
                  </div>
                  <span className={`text-xs font-medium px-2 py-0.5 rounded ${
                    v.severity === 'BREAKING' ? 'bg-red-500/10 text-red-400' : v.severity === 'WARNING' ? 'bg-yellow-500/10 text-yellow-400' : 'bg-green-500/10 text-green-400'
                  }`}>
                    {v.severity}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-slate-500 text-sm">No recent violations</p>
          )}
        </div>
      </div>
    </div>
  )
}
