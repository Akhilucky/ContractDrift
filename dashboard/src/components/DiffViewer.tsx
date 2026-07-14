import { useMemo, useState, useRef, useCallback } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'

interface DiffViewerProps {
  oldSchema: Record<string, unknown> | null
  newSchema: Record<string, unknown> | null
  title?: string
}

interface DiffLine {
  path: string
  type: 'added' | 'removed' | 'changed' | 'unchanged'
  oldValue?: unknown
  newValue?: unknown
  depth: number
}

function flattenJson(obj: Record<string, unknown> | null, prefix = '', depth = 0): DiffLine[] {
  if (!obj) return []
  const lines: DiffLine[] = []
  const keys = Object.keys(obj).sort()
  for (const key of keys) {
    const path = prefix ? `${prefix}.${key}` : key
    const value = obj[key]
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      lines.push({ path, type: 'unchanged', depth, oldValue: value, newValue: value })
      lines.push(...flattenJson(value as Record<string, unknown>, path, depth + 1))
    } else {
      lines.push({ path, type: 'unchanged', depth, oldValue: value, newValue: value })
    }
  }
  return lines
}

function computeDiff(
  oldSchema: Record<string, unknown> | null,
  newSchema: Record<string, unknown> | null
): DiffLine[] {
  const oldFlat = new Map<string, { value: unknown; depth: number }>()
  const newFlat = new Map<string, { value: unknown; depth: number }>()

  function collect(obj: Record<string, unknown> | null, prefix: string, depth: number) {
    if (!obj) return
    for (const key of Object.keys(obj)) {
      const path = prefix ? `${prefix}.${key}` : key
      const val = obj[key]
      if (val && typeof val === 'object' && !Array.isArray(val)) {
        collect(val as Record<string, unknown>, path, depth + 1)
      } else {
        const set = prefix.startsWith('_old_') ? oldFlat : newFlat
        set.set(path, { value: val, depth })
      }
    }
  }

  collect(oldSchema, '', 0)
  collect(newSchema, '', 0)

  const oldPaths = new Set(oldFlat.keys())
  const newPaths = new Set(newFlat.keys())
  const allPaths = [...new Set([...oldPaths, ...newPaths])].sort()

  const result: DiffLine[] = []
  for (const path of allPaths) {
    const old = oldFlat.get(path)
    const nw = newFlat.get(path)
    const depth = path.split('.').length - 1
    if (old && !nw) {
      result.push({ path, type: 'removed', oldValue: old.value, depth })
    } else if (!old && nw) {
      result.push({ path, type: 'added', newValue: nw.value, depth })
    } else if (old && nw) {
      if (JSON.stringify(old.value) !== JSON.stringify(nw.value)) {
        result.push({ path, type: 'changed', oldValue: old.value, newValue: nw.value, depth })
      } else {
        result.push({ path, type: 'unchanged', oldValue: old.value, newValue: nw.value, depth })
      }
    }
  }
  return result
}

function getSeverity(diffLines: DiffLine[]): 'BREAKING' | 'WARNING' | 'ADDITIVE' {
  const hasRemoved = diffLines.some((d) => d.type === 'removed')
  const hasChanged = diffLines.some((d) => d.type === 'changed')
  const hasAdded = diffLines.some((d) => d.type === 'added')
  if (hasRemoved) return 'BREAKING'
  if (hasChanged) return 'WARNING'
  if (hasAdded) return 'ADDITIVE'
  return 'ADDITIVE'
}

function formatValue(val: unknown): string {
  if (val === undefined) return '—'
  if (val === null) return 'null'
  if (typeof val === 'string') return `"${val}"`
  return String(val)
}

function DiffLineRow({ line, side }: { line: DiffLine; side: 'old' | 'new' }) {
  const [collapsed, setCollapsed] = useState(false)
  const isContainer = line.oldValue && typeof line.oldValue === 'object' && !Array.isArray(line.oldValue)

  const bgClass =
    line.type === 'removed' && side === 'old'
      ? 'bg-red-500/10'
      : line.type === 'added' && side === 'new'
      ? 'bg-green-500/10'
      : line.type === 'changed' && side === 'old'
      ? 'bg-yellow-500/10'
      : line.type === 'changed' && side === 'new'
      ? 'bg-yellow-500/10'
      : ''

  const prefix =
    side === 'old'
      ? line.type === 'removed'
        ? '-'
        : line.type === 'changed'
        ? '~'
        : ' '
      : line.type === 'added'
      ? '+'
      : line.type === 'changed'
      ? '+'
      : ' '

  const prefixColor =
    side === 'old'
      ? line.type === 'removed'
        ? 'text-red-400'
        : line.type === 'changed'
        ? 'text-yellow-400'
        : 'text-slate-500'
      : line.type === 'added'
      ? 'text-green-400'
      : line.type === 'changed'
      ? 'text-yellow-400'
      : 'text-slate-500'

  const value = side === 'old' ? line.oldValue : line.newValue

  return (
    <div className={`flex items-start text-xs font-mono leading-5 ${bgClass}`}>
      <span className="w-6 text-right pr-1 text-slate-600 select-none shrink-0">{prefix}</span>
      {isContainer && !collapsed ? (
        <span
          className="cursor-pointer select-none text-slate-400 hover:text-slate-200 flex items-center gap-0.5"
          style={{ paddingLeft: `${line.depth * 16}px` }}
          onClick={() => setCollapsed(true)}
        >
          <ChevronDown className="w-3 h-3" />
          <span className={`${prefixColor}`}>{line.path.split('.').pop()}</span>
          <span className="text-slate-500">: {'{'}</span>
        </span>
      ) : isContainer && collapsed ? (
        <span
          className="cursor-pointer select-none flex items-center gap-0.5"
          style={{ paddingLeft: `${line.depth * 16}px` }}
          onClick={() => setCollapsed(false)}
        >
          <ChevronRight className="w-3 h-3 text-slate-400" />
          <span className="text-slate-300">{line.path.split('.').pop()}</span>
          <span className="text-slate-500">: {'{'}...{'}'}</span>
        </span>
      ) : (
        <span
          className="flex items-center gap-1 min-w-0"
          style={{ paddingLeft: `${line.depth * 16 + 4}px` }}
        >
          <span className={`shrink-0 ${prefixColor}`}>{line.path.split('.').pop()}</span>
          <span className="text-slate-500">:</span>
          <span className="text-slate-300 truncate">{formatValue(value)}</span>
        </span>
      )}
    </div>
  )
}

export default function DiffViewer({ oldSchema, newSchema, title }: DiffViewerProps) {
  const leftRef = useRef<HTMLDivElement>(null)
  const rightRef = useRef<HTMLDivElement>(null)

  const diffLines = useMemo(() => computeDiff(oldSchema, newSchema), [oldSchema, newSchema])
  const severity = useMemo(() => getSeverity(diffLines), [diffLines])
  const changeLines = useMemo(
    () => diffLines.filter((l) => l.type !== 'unchanged'),
    [diffLines]
  )

  const severityConfig: Record<string, { label: string; cls: string }> = {
    BREAKING: { label: 'BREAKING', cls: 'bg-red-500/10 text-red-400 border-red-500/30' },
    WARNING: { label: 'WARNING', cls: 'bg-yellow-500/10 text-yellow-400 border-yellow-500/30' },
    ADDITIVE: { label: 'ADDITIVE', cls: 'bg-green-500/10 text-green-400 border-green-500/30' },
  }

  const cfg = severityConfig[severity]

  const handleScroll = useCallback((source: 'left' | 'right') => {
    return () => {
      const sourceRef = source === 'left' ? leftRef : rightRef
      const targetRef = source === 'left' ? rightRef : leftRef
      if (sourceRef.current && targetRef.current) {
        targetRef.current.scrollTop = sourceRef.current.scrollTop
      }
    }
  }, [])

  if (!oldSchema && !newSchema) {
    return (
      <div className="bg-slate-800 rounded-lg border border-slate-700 p-4">
        <p className="text-sm text-slate-500">No schema data to compare</p>
      </div>
    )
  }

  return (
    <div className="bg-slate-800 rounded-lg border border-slate-700 overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-700">
        <div className="flex items-center gap-3">
          <h3 className="text-sm font-semibold text-slate-300">{title || 'Schema Diff'}</h3>
          <span className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded border ${cfg.cls}`}>
            {cfg.label}
          </span>
        </div>
        <span className="text-xs text-slate-500">
          {changeLines.length} change{changeLines.length !== 1 ? 's' : ''}
        </span>
      </div>

      {changeLines.length === 0 ? (
        <div className="p-6 text-center">
          <p className="text-sm text-green-400">Schemas are identical</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 divide-x divide-slate-700 max-h-[500px] overflow-hidden">
          <div>
            <div className="px-4 py-1.5 bg-slate-900/50 border-b border-slate-700">
              <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Baseline (Old)</span>
            </div>
            <div ref={leftRef} onScroll={handleScroll('left')} className="overflow-auto max-h-[460px]">
              {diffLines.map((line, i) => (
                <DiffLineRow key={`l-${line.path}-${i}`} line={line} side="old" />
              ))}
            </div>
          </div>
          <div>
            <div className="px-4 py-1.5 bg-slate-900/50 border-b border-slate-700">
              <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Inferred (New)</span>
            </div>
            <div ref={rightRef} onScroll={handleScroll('right')} className="overflow-auto max-h-[460px]">
              {diffLines.map((line, i) => (
                <DiffLineRow key={`r-${line.path}-${i}`} line={line} side="new" />
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
