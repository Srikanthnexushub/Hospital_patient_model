import { useQuery } from '@tanstack/react-query'
import api from './patientApi.js'

// ── Raw API functions ─────────────────────────────────────────────────────────

export async function getRiskDashboard({ page = 0, size = 20 } = {}) {
  const response = await api.get('/dashboard/patient-risk', { params: { page, size } })
  return response.data
}

export async function getDashboardStats() {
  const response = await api.get('/dashboard/stats')
  return response.data
}

// ── React Query hooks ─────────────────────────────────────────────────────────

export function useRiskDashboard(page = 0) {
  return useQuery({
    queryKey: ['risk-dashboard', page],
    queryFn: () => getRiskDashboard({ page }),
    staleTime: 30_000,
    placeholderData: prev => prev,
    refetchInterval: 30_000,
  })
}

export function useDashboardStats() {
  return useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => getDashboardStats(),
    staleTime: 30_000,
    refetchInterval: 30_000,
  })
}
