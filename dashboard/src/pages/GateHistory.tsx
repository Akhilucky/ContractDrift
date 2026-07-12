import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getGateHistory } from '../lib/api'

const decisionColors: Record<string, string> = {
  ALLOW: 'bg-green-500/10 text-green-400',
  DENY: 'bg-red-500/10 text-red-400',
  OVERRIDE: 'bg-blue-500/10 text-blue-400',
}

export default function GateHistory() {
  const [serviceFilter, setServiceFilter] = useState('')
  const { data: history, isLoading } = useQuery({ queryKey: ['gate-history'], queryFn: () => getGateHistory() })

  const services = useMemo(() => {
    if (!history) return []
    return [...new Set(history.map((h) => h.service))]
  }, [history])

  const filtered = useMemo(() => {
    if (!history) return []
    if (!serviceFilter) return history
    return history.filter((h) => h.service === serviceFilter)
  }, [history, serviceFilter])

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-white">Gate History</h2>
        <select
          value={serviceFilter}
          onChange={(e) => setServiceFilter(e.target.value)}
          className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          <option value="">All Services</option>
          {services.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>

      <div className="bg-slate-800 rounded-lg border border-slate-700 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-700 bg-slate-800/50">
              <th className="text-left px-4 py-3 font-medium text-slate-400">Service</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Version</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Environment</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Decision</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Drift Score</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Violations</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Decided By</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Timestamp</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-500">Loading...</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-500">No gate history found</td></tr>
            ) : (
              filtered.map((g) => (
                <tr key={g.id} className="border-b border-slate-700/50 hover:bg-slate-700/30 transition-colors">
                  <td className="px-4 py-3 text-slate-200 font-medium">{g.service}</td>
                  <td className="px-4 py-3 text-slate-300">{g.version}</td>
                  <td className="px-4 py-3 text-slate-300">{g.environment}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded ${decisionColors[g.decision]}`}>
                      {g.decision}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <div className="w-20 h-1.5 bg-slate-700 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full ${
                            g.drift_score > 0.7 ? 'bg-red-400' : g.drift_score > 0.3 ? 'bg-yellow-400' : 'bg-green-400'
                          }`}
                          style={{ width: `${g.drift_score * 100}%` }}
                        />
                      </div>
                      <span className="text-xs text-slate-400">{(g.drift_score * 100).toFixed(0)}%</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-300">{g.violations}</td>
                  <td className="px-4 py-3 text-slate-300">{g.decided_by}</td>
                  <td className="px-4 py-3 text-slate-500 text-xs">{new Date(g.created_at).toLocaleString()}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
