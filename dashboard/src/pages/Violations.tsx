import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getViolations, getViolationSummary, resolveViolation } from '../lib/api'
import { CheckCircle } from 'lucide-react'

const severityColors: Record<string, string> = {
  BREAKING: 'bg-red-500/10 text-red-400',
  WARNING: 'bg-yellow-500/10 text-yellow-400',
  ADDITIVE: 'bg-green-500/10 text-green-400',
}

const severityDot: Record<string, string> = {
  BREAKING: 'bg-red-400',
  WARNING: 'bg-yellow-400',
  ADDITIVE: 'bg-green-400',
}

export default function Violations() {
  const queryClient = useQueryClient()
  const [severityFilter, setSeverityFilter] = useState('')
  const [serviceFilter, setServiceFilter] = useState('')
  const [resolvedFilter, setResolvedFilter] = useState('')
  const [selectedViolation, setSelectedViolation] = useState<string | null>(null)

  const { data: violations } = useQuery({ queryKey: ['violations'], queryFn: () => getViolations() })
  const { data: summary } = useQuery({ queryKey: ['violation-summary'], queryFn: getViolationSummary })

  const resolveMutation = useMutation({
    mutationFn: resolveViolation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['violations'] })
      queryClient.invalidateQueries({ queryKey: ['violation-summary'] })
    },
  })

  const filtered = useMemo(() => {
    if (!violations) return []
    return violations.filter((v) => {
      if (severityFilter && v.severity !== severityFilter) return false
      if (serviceFilter && !v.service.toLowerCase().includes(serviceFilter.toLowerCase())) return false
      if (resolvedFilter === 'resolved' && !v.resolved) return false
      if (resolvedFilter === 'unresolved' && v.resolved) return false
      return true
    })
  }, [violations, severityFilter, serviceFilter, resolvedFilter])

  const selected = useMemo(() => {
    if (!selectedViolation || !violations) return null
    return violations.find((v) => v.id === selectedViolation) ?? null
  }, [selectedViolation, violations])

  const stats = [
    { label: 'Total', value: summary?.total ?? 0, color: 'text-slate-300' },
    { label: 'Breaking', value: summary?.breaking ?? 0, color: 'text-red-400' },
    { label: 'Warning', value: summary?.warning ?? 0, color: 'text-yellow-400' },
    { label: 'Additive', value: summary?.additive ?? 0, color: 'text-green-400' },
    { label: 'Resolved', value: summary?.resolved ?? 0, color: 'text-emerald-400' },
    { label: 'Unresolved', value: summary?.unresolved ?? 0, color: 'text-orange-400' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-white">Violations</h2>
      </div>

      <div className="grid grid-cols-6 gap-3">
        {stats.map((s) => (
          <div key={s.label} className="bg-slate-800 rounded-lg border border-slate-700 p-3 text-center">
            <p className={`text-lg font-bold ${s.color}`}>{s.value}</p>
            <p className="text-xs text-slate-500">{s.label}</p>
          </div>
        ))}
      </div>

      <div className="flex gap-3">
        <select
          value={severityFilter}
          onChange={(e) => setSeverityFilter(e.target.value)}
          className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          <option value="">All Severities</option>
          <option value="BREAKING">Breaking</option>
          <option value="WARNING">Warning</option>
          <option value="ADDITIVE">Additive</option>
        </select>
        <input
          type="text"
          placeholder="Filter by service..."
          value={serviceFilter}
          onChange={(e) => setServiceFilter(e.target.value)}
          className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500"
        />
        <select
          value={resolvedFilter}
          onChange={(e) => setResolvedFilter(e.target.value)}
          className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          <option value="">All</option>
          <option value="unresolved">Unresolved</option>
          <option value="resolved">Resolved</option>
        </select>
      </div>

      <div className="grid grid-cols-5 gap-4">
        <div className={`${selected ? 'col-span-3' : 'col-span-5'} bg-slate-800 rounded-lg border border-slate-700 overflow-hidden`}>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-700 bg-slate-800/50">
                <th className="text-left px-4 py-3 font-medium text-slate-400">Severity</th>
                <th className="text-left px-4 py-3 font-medium text-slate-400">Rule</th>
                <th className="text-left px-4 py-3 font-medium text-slate-400">Message</th>
                <th className="text-left px-4 py-3 font-medium text-slate-400">Service</th>
                <th className="text-left px-4 py-3 font-medium text-slate-400">Status</th>
                <th className="text-left px-4 py-3 font-medium text-slate-400">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((v) => (
                <tr
                  key={v.id}
                  className={`border-b border-slate-700/50 hover:bg-slate-700/30 transition-colors cursor-pointer ${
                    selectedViolation === v.id ? 'bg-slate-700/40' : ''
                  }`}
                  onClick={() => setSelectedViolation(v.id)}
                >
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <div className={`w-2 h-2 rounded-full ${severityDot[v.severity]}`} />
                      <span className={`text-xs font-medium px-2 py-0.5 rounded ${severityColors[v.severity]}`}>
                        {v.severity}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-300 font-mono text-xs">{v.rule}</td>
                  <td className="px-4 py-3 text-slate-300 max-w-xs truncate">{v.message}</td>
                  <td className="px-4 py-3 text-slate-300">{v.service}</td>
                  <td className="px-4 py-3">
                    {v.resolved ? (
                      <span className="text-xs font-medium text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded">Resolved</span>
                    ) : (
                      <span className="text-xs font-medium text-orange-400 bg-orange-500/10 px-2 py-0.5 rounded">Open</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {!v.resolved && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          resolveMutation.mutate(v.id)
                        }}
                        className="text-emerald-400 hover:text-emerald-300 transition-colors"
                        title="Resolve"
                      >
                        <CheckCircle className="w-4 h-4" />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {selected && (
          <div className="col-span-2 bg-slate-800 rounded-lg border border-slate-700 p-4">
            <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-3">Violation Detail</h3>
            <div className="space-y-3 text-sm">
              <div>
                <span className="text-slate-500">ID:</span>
                <p className="text-slate-200 font-mono text-xs mt-0.5">{selected.id}</p>
              </div>
              <div>
                <span className="text-slate-500">Rule:</span>
                <p className="text-slate-200 font-mono text-xs mt-0.5">{selected.rule}</p>
              </div>
              <div>
                <span className="text-slate-500">Message:</span>
                <p className="text-slate-200 mt-0.5">{selected.message}</p>
              </div>
              <div>
                <span className="text-slate-500">Path:</span>
                <p className="text-slate-200 font-mono text-xs mt-0.5">{selected.path}</p>
              </div>
              <div>
                <span className="text-slate-500">Expected:</span>
                <pre className="text-slate-200 bg-slate-900 rounded p-2 mt-1 text-xs max-h-24 overflow-auto">
                  {JSON.stringify(selected.expected, null, 2)}
                </pre>
              </div>
              <div>
                <span className="text-slate-500">Actual:</span>
                <pre className="text-slate-200 bg-slate-900 rounded p-2 mt-1 text-xs max-h-24 overflow-auto">
                  {JSON.stringify(selected.actual, null, 2)}
                </pre>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
