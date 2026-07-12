const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  })
  if (!res.ok) {
    const err = await res.text()
    throw new Error(err || res.statusText)
  }
  return res.json()
}

export interface Contract {
  id: string
  provider: string
  consumer: string
  endpoint: string
  method: string
  version: string
  status: string
  source: string
  inferred_at: string
  schema?: Record<string, unknown>
}

export interface ContractPair {
  provider_contract: Contract | null
  consumer_contract: Contract | null
}

export interface Violation {
  id: string
  contract_id: string
  severity: 'BREAKING' | 'WARNING' | 'ADDITIVE'
  rule: string
  message: string
  path: string
  expected: unknown
  actual: unknown
  service: string
  resolved: boolean
  created_at: string
}

export interface ViolationSummary {
  total: number
  breaking: number
  warning: number
  additive: number
  resolved: number
  unresolved: number
}

export interface GateDecision {
  id: string
  service: string
  version: string
  environment: string
  decision: 'ALLOW' | 'DENY' | 'OVERRIDE'
  drift_score: number
  violations: number
  decided_by: string
  created_at: string
}

export function getContracts(): Promise<Contract[]> {
  return request<Contract[]>('/api/v1/contracts')
}

export function getContract(id: string): Promise<Contract> {
  return request<Contract>(`/api/v1/contracts/${id}`)
}

export function getContractPair(provider: string, consumer: string): Promise<ContractPair> {
  return request<ContractPair>(`/api/v1/contracts/pair?provider=${encodeURIComponent(provider)}&consumer=${encodeURIComponent(consumer)}`)
}

export function importContract(spec: Record<string, unknown>): Promise<Contract> {
  return request<Contract>('/api/v1/contracts/import', {
    method: 'POST',
    body: JSON.stringify(spec),
  })
}

export function getViolations(filter?: Record<string, string>): Promise<Violation[]> {
  const params = filter ? '?' + new URLSearchParams(filter).toString() : ''
  return request<Violation[]>(`/api/v1/violations${params}`)
}

export function getViolation(id: string): Promise<Violation> {
  return request<Violation>(`/api/v1/violations/${id}`)
}

export function resolveViolation(id: string): Promise<Violation> {
  return request<Violation>(`/api/v1/violations/${id}/resolve`, { method: 'POST' })
}

export function getViolationSummary(): Promise<ViolationSummary> {
  return request<ViolationSummary>('/api/v1/violations/summary')
}

export function checkPromotion(service: string, version: string, env: string): Promise<GateDecision> {
  return request<GateDecision>('/api/v1/gate/promote', {
    method: 'POST',
    body: JSON.stringify({ service, version, environment: env }),
  })
}

export function getGateHistory(service?: string): Promise<GateDecision[]> {
  const params = service ? `?service=${encodeURIComponent(service)}` : ''
  return request<GateDecision[]>(`/api/v1/gate/history${params}`)
}
