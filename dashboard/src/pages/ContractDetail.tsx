import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { getContract } from '../lib/api'

export default function ContractDetail() {
  const { id } = useParams<{ id: string }>()
  const { data: contract, isLoading } = useQuery({
    queryKey: ['contract', id],
    queryFn: () => getContract(id!),
    enabled: !!id,
  })

  if (isLoading) {
    return <div className="text-slate-400">Loading...</div>
  }

  if (!contract) {
    return <div className="text-slate-400">Contract not found</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <a href="/contracts" className="text-slate-400 hover:text-slate-200 transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </a>
        <div>
          <h2 className="text-xl font-bold text-white">{contract.provider} &rarr; {contract.consumer}</h2>
          <p className="text-sm text-slate-400">{contract.endpoint}</p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wider mb-1">Method</p>
          <span className="text-sm font-medium text-slate-200 px-2 py-0.5 rounded bg-slate-700">{contract.method}</span>
        </div>
        <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wider mb-1">Version</p>
          <p className="text-sm font-medium text-slate-200">{contract.version}</p>
        </div>
        <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wider mb-1">Status</p>
          <span className={`text-xs font-medium px-2 py-0.5 rounded ${
            contract.status === 'active' ? 'bg-green-500/10 text-green-400' : 'bg-slate-500/10 text-slate-400'
          }`}>{contract.status}</span>
        </div>
      </div>

      <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
        <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-3">Schema / Contract JSON</h3>
        <pre className="text-xs text-slate-300 bg-slate-900 rounded-lg p-4 max-h-[500px] overflow-auto font-mono">
          {JSON.stringify(contract.schema ?? contract, null, 2)}
        </pre>
      </div>

      <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
        <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-3">Metadata</h3>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <span className="text-slate-500">Source:</span>{' '}
            <span className="text-slate-300">{contract.source}</span>
          </div>
          <div>
            <span className="text-slate-500">Inferred At:</span>{' '}
            <span className="text-slate-300">{new Date(contract.inferred_at).toLocaleString()}</span>
          </div>
          <div>
            <span className="text-slate-500">Contract ID:</span>{' '}
            <span className="text-slate-300 font-mono text-xs">{contract.id}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
