import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { getContracts } from '../lib/api'

export default function Contracts() {
  const { data: contracts, isLoading } = useQuery({ queryKey: ['contracts'], queryFn: getContracts })
  const [search, setSearch] = useState('')

  const filtered = useMemo(() => {
    if (!contracts) return []
    if (!search) return contracts
    const q = search.toLowerCase()
    return contracts.filter(
      (c) => c.provider.toLowerCase().includes(q) || c.consumer.toLowerCase().includes(q),
    )
  }, [contracts, search])

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-white">Contracts</h2>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search by provider or consumer..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9 pr-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 w-72"
          />
        </div>
      </div>

      <div className="bg-slate-800 rounded-lg border border-slate-700 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-700 bg-slate-800/50">
              <th className="text-left px-4 py-3 font-medium text-slate-400">Provider</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Consumer</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Endpoint</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Method</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Version</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Status</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Source</th>
              <th className="text-left px-4 py-3 font-medium text-slate-400">Inferred At</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-500">Loading...</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-500">No contracts found</td></tr>
            ) : (
              filtered.map((c) => (
                <tr key={c.id} className="border-b border-slate-700/50 hover:bg-slate-700/30 transition-colors">
                  <td className="px-4 py-3">
                    <Link to={`/contracts/${c.id}`} className="text-emerald-400 hover:text-emerald-300 font-medium">
                      {c.provider}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-slate-300">{c.consumer}</td>
                  <td className="px-4 py-3 text-slate-300 font-mono text-xs">{c.endpoint}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs font-medium px-2 py-0.5 rounded bg-slate-700 text-slate-300">{c.method}</span>
                  </td>
                  <td className="px-4 py-3 text-slate-300">{c.version}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded ${
                      c.status === 'active' ? 'bg-green-500/10 text-green-400' : 'bg-slate-500/10 text-slate-400'
                    }`}>{c.status}</span>
                  </td>
                  <td className="px-4 py-3 text-slate-300">{c.source}</td>
                  <td className="px-4 py-3 text-slate-500 text-xs">{new Date(c.inferred_at).toLocaleString()}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
