import { useState } from 'react'
import { CheckCircle2, XCircle, Shield, ChevronDown, ChevronRight } from 'lucide-react'

interface DeploymentEvent {
  id: string
  serviceId: string
  versionSha: string
  environment: string
  deployedAt: string
  status: 'success' | 'blocked' | 'overridden'
  violations?: Array<{ severity: string; description: string }>
}

interface DeploymentTimelineProps {
  events: DeploymentEvent[]
  serviceId?: string
}

const statusConfig: Record<string, { icon: typeof CheckCircle2; color: string; dotColor: string; bgColor: string }> = {
  success: { icon: CheckCircle2, color: 'text-green-400', dotColor: 'bg-green-400', bgColor: 'bg-green-500/10' },
  blocked: { icon: XCircle, color: 'text-red-400', dotColor: 'bg-red-400', bgColor: 'bg-red-500/10' },
  overridden: { icon: Shield, color: 'text-blue-400', dotColor: 'bg-blue-400', bgColor: 'bg-blue-500/10' },
}

function TimelineNode({ event, isLast }: { event: DeploymentEvent; isLast: boolean }) {
  const [expanded, setExpanded] = useState(false)
  const cfg = statusConfig[event.status] || statusConfig.success
  const Icon = cfg.icon

  return (
    <div className="flex gap-4">
      <div className="flex flex-col items-center shrink-0">
        <div className={`w-3 h-3 rounded-full ${cfg.dotColor} ring-4 ring-slate-800`} />
        {!isLast && <div className="w-px flex-1 bg-slate-700 my-1" />}
      </div>

      <div className="pb-6 flex-1 min-w-0">
        <button
          onClick={() => setExpanded(!expanded)}
          className="w-full text-left group"
        >
          <div className="flex items-center gap-3 mb-1">
            <span className="font-mono text-xs text-slate-200 bg-slate-700/50 px-1.5 py-0.5 rounded">
              {event.versionSha.slice(0, 7)}
            </span>
            <span className={`text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded ${cfg.bgColor} ${cfg.color}`}>
              {event.environment}
            </span>
            <div className="flex items-center gap-1 ml-auto">
              <Icon className={`w-4 h-4 ${cfg.color}`} />
              {event.violations && event.violations.length > 0 && (
                <span className="text-xs text-slate-500">
                  {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                </span>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">{event.serviceId}</span>
            <span className="text-xs text-slate-600">&middot;</span>
            <time className="text-xs text-slate-500">
              {new Date(event.deployedAt).toLocaleString()}
            </time>
          </div>
        </button>

        {expanded && event.violations && event.violations.length > 0 && (
          <div className="mt-3 space-y-2 pl-1">
            {event.violations.map((v, i) => (
              <div
                key={i}
                className={`flex items-start gap-2 p-2 rounded text-xs border ${
                  v.severity === 'BREAKING'
                    ? 'bg-red-500/5 border-red-500/20 text-red-300'
                    : v.severity === 'WARNING'
                    ? 'bg-yellow-500/5 border-yellow-500/20 text-yellow-300'
                    : 'bg-green-500/5 border-green-500/20 text-green-300'
                }`}
              >
                <span className="font-semibold shrink-0">{v.severity}:</span>
                <span className="text-slate-400">{v.description}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export default function DeploymentTimeline({ events }: DeploymentTimelineProps) {
  if (events.length === 0) {
    return (
      <div className="bg-slate-800 rounded-lg border border-slate-700 p-6 text-center">
        <p className="text-sm text-slate-500">No deployment events</p>
      </div>
    )
  }

  const sorted = [...events].sort(
    (a, b) => new Date(b.deployedAt).getTime() - new Date(a.deployedAt).getTime()
  )

  return (
    <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
      <div className="max-h-[600px] overflow-y-auto pl-2">
        {sorted.map((event, i) => (
          <TimelineNode key={event.id} event={event} isLast={i === sorted.length - 1} />
        ))}
      </div>
    </div>
  )
}
