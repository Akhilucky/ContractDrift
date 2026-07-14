import { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { Shield, FileText, AlertTriangle, BarChart3, Activity } from 'lucide-react'

const navItems = [
  { to: '/', label: 'Dashboard', icon: BarChart3 },
  { to: '/contracts', label: 'Contracts', icon: FileText },
  { to: '/violations', label: 'Violations', icon: AlertTriangle },
  { to: '/gate', label: 'Gate History', icon: Shield },
]

interface LayoutProps {
  children: ReactNode
}

export default function Layout({ children }: LayoutProps) {
  return (
    <div className="flex h-screen bg-slate-900">
      <aside className="w-64 bg-slate-800 border-r border-slate-700 flex flex-col">
        <div className="p-6 border-b border-slate-700">
          <div className="flex items-center gap-3">
            <Shield className="w-8 h-8 text-emerald-400" />
            <div>
              <h1 className="text-lg font-bold text-white">Contract</h1>
              <p className="text-xs text-slate-400">Sentinel</p>
            </div>
          </div>
        </div>
        <nav className="flex-1 p-4 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-emerald-500/10 text-emerald-400'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-700/50'
                }`
              }
            >
              <item.icon className="w-5 h-5" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="p-4 border-t border-slate-700 space-y-3">
          <a
            href="http://localhost:16686"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-slate-400 hover:text-slate-200 hover:bg-slate-700/50 transition-colors"
          >
            <Activity className="w-5 h-5" />
            Jaeger Traces
          </a>
          <p className="text-xs text-slate-500">Contract Sentinel v1.0</p>
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto">
        <header className="h-16 border-b border-slate-700 bg-slate-800/50 flex items-center px-6">
          <h2 className="text-lg font-semibold text-white">Dashboard</h2>
          <div className="ml-auto flex items-center gap-3">
            <div className="w-2 h-2 rounded-full bg-emerald-400" />
            <span className="text-sm text-slate-400">All systems nominal</span>
          </div>
        </header>
        <div className="p-6">{children}</div>
      </main>
    </div>
  )
}
